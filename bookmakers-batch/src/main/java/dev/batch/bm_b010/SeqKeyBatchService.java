package dev.batch.bm_b010;

import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.util.StringUtils;

import dev.batch.repository.bm.BookDataRepository;

/**
 * seq_key発番処理
 * @author shiraishitoshio
 *
 */
@Component
public class SeqKeyBatchService {
    private static final String RANDOM_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 振り直し時に一時退避する seq_key の接頭辞。
     * 正式な seq_key（英数字-連番）には出てこない文字にしておくこと。
     */
    private static final String TEMP_PREFIX = "~";

    @Autowired
    private BookDataRepository bookDataRepository;

    /**
     * static_dataテーブルのseq_keyを生成する。
     * 値は "match_id(または乱数)-連番" 形式。
     *
     * 1) match_idが来ている場合は、そのmatch_idをもとに連番を付与する
     * 2) match_idがnullで、過去も一度もmatch_idが確定していない場合はランダム値をベースにする
     * 3) 過去はランダム値だった試合群に、今回正式なmatch_idが来た場合は、
     *    過去分すべてを正式なmatch_idへ書き換えたうえで連番を振り直す
     * 4) 過去は正式なmatch_idだった試合群で、今回match_idがnullの場合は、
     *    過去の正式なmatch_idを引き継いで連番だけ+1する
     *
     * 【追記】ここから先（既存seq_keyの読み取り〜呼び出し元でのINSERT）は、
     * FinGettingStat#finGettingStat() のトランザクション内で bookDataRepository.lockByTeams()
     * によるPostgreSQLのトランザクションスコープ・アドバイザリロックに守られている。
     *
     * @param home 対象試合のホームチーム名
     * @param away 対象試合のアウェーチーム名
     * @param matchId 対象試合ID（null許容）
     * @return 生成されたseq_key（例: "12345678-1"）
     */
    public synchronized String create(String home, String away, String matchId) {
        // この対戦カードに対する一連の処理（既存seq_key読み取り〜INSERT）を
        // プロセス（ECSタスク）をまたいで直列化する。
        bookDataRepository.lockByTeams(home, away);
        List<SeqKeyDTO> existDto = bookDataRepository.findMatchId(home, away);

        if (StringUtils.hasValue(matchId)) {
            // ---- 1) 正式なmatch_idが来ているケース ----
            if (existDto == null || existDto.isEmpty()) {
                // 初回登録（ただし、同じmatch_idの行が別表記のチーム名で既にある可能性があるため確認）
                if (bookDataRepository.existsSeqKeyPrefix(matchId) > 0) {
                    return overwriteAndAppend(home, away, matchId);
                }
                return matchId + "-1";
            }
            String existingMatchId = sameChk(existDto);
            if (matchId.equals(existingMatchId)) {
                // 4) 既に同じmatch_idで採番されている試合群 → そのまま連番+1
                return nextRenban(existDto.get(0).getSeqKey());
            }
            // 3) それまでランダム値（または別のmatch_id）だった試合群に
            //    正式なmatch_idが連携された → 過去分を正式match_idへ書き換えて連番を振り直す
            return overwriteAndAppend(home, away, matchId);
        } else {
            // ---- 2) match_idが来ていないケース ----
            if (existDto == null || existDto.isEmpty()) {
                // 初回はランダム値を採番
                return generateRandomStringAndChkSeqKey() + "-1";
            }
            // 既存の採番（ランダムベース／過去に確定していたmatch_idベースいずれも）を
            // そのまま引き継いで連番だけ+1する
            return nextRenban(existDto.get(0).getSeqKey());
        }
    }

    /**
     * 過去分のseq_keyを正式なmatch_idベースに書き換えたうえで、
     * 新規レコード用のseq_keyを返す。
     *
     * 1件ずつ直接上書きすると、振り直し先のキー（例: matchId-2）を
     * まだ振り直していない別の行が使っていた場合に主キー重複になるため、
     * 以下の2段階で振り直す。
     *   1段階目: 対象行の seq_key を一時キー（"~" + 元のseq_key）へ退避
     *   2段階目: 一時キーから matchId-1, matchId-2 ... へ振り直す
     *
     * 対象は「同一対戦カードの行」＋「すでに matchId- で始まる行」。
     * （チーム名の表記違いで対戦カード検索に出てこない同一matchIdの行ともぶつからないようにする）
     *
     * @param home ホームチーム名
     * @param away アウェーチーム名
     * @param matchId 正式なmatch_id
     * @return 新規レコード用のseq_key
     */
    private String overwriteAndAppend(String home, String away, String matchId) {
        List<SeqKeyDTO> targets = bookDataRepository.findSeqKeysForRenumber(home, away, matchId);
        if (targets == null || targets.isEmpty()) {
            return matchId + "-1";
        }

        List<String> oldKeys = targets.stream()
                .map(SeqKeyDTO::getSeqKey)
                .collect(Collectors.toList());

        // 1段階目: 一時キーへ退避（ここで振り直し先とぶつかる行がなくなる）
        bookDataRepository.moveSeqKeysToTemp(TEMP_PREFIX, oldKeys);

        // 2段階目: 古い順に 1 から振り直す
        int renban = 0;
        for (String oldKey : oldKeys) {
            renban++;
            String newSeqKey = matchId + "-" + renban;
            bookDataRepository.updateSeqKey(TEMP_PREFIX + oldKey, newSeqKey, matchId);
        }
        return matchId + "-" + (renban + 1);
    }

    /**
     * match_idなしケース用の乱数base文字列を生成する（"-連番"を除いた部分）。
     * 既存のseq_keyのprefix（"乱数-"）と重複する場合は再生成する。
     * @return 重複していない8桁の乱数文字列
     */
    private String generateRandomStringAndChkSeqKey() {
        String candidate;
        do {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(RANDOM_CHARS.charAt(RANDOM.nextInt(RANDOM_CHARS.length())));
            }
            candidate = sb.toString();
        } while (bookDataRepository.existsSeqKeyPrefix(candidate) > 0);
        return candidate;
    }

    /**
     * seq_keyの語尾の連番を+1する。
     * @param key 現在のseq_key（例: "12345678-9"）
     * @return 連番を+1したseq_key（例: "12345678-10"）
     */
    private String nextRenban(String key) {
        int idx = key.lastIndexOf('-');
        String prefix = key.substring(0, idx);
        int renban = Integer.parseInt(key.substring(idx + 1));
        return prefix + "-" + (renban + 1);
    }

    /**
     * 既存レコードのseq_key接頭辞が全て一致しているかをチェックする。
     * @param existDto 既存レコード一覧
     * @return 全件一致していれば共通の接頭辞、1件でも異なればnull
     */
    private String sameChk(List<SeqKeyDTO> existDto) {
        if (existDto == null || existDto.isEmpty()) {
            return null;
        }
        String matchId = null;
        for (SeqKeyDTO dto : existDto) {
            String seqKey = dto.getSeqKey();
            if (seqKey == null || !seqKey.contains("-")) {
                throw new IllegalArgumentException("seqKeyの形式が不正です: " + seqKey);
            }
            String currentMatchId = seqKey.split("-", 2)[0];
            if (matchId == null) {
                matchId = currentMatchId;
            } else if (!matchId.equals(currentMatchId)) {
                return null;
            }
        }
        return matchId;
    }
}