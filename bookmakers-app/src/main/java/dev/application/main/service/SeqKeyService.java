package dev.application.main.service;

import java.security.SecureRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.util.StringUtils;

import dev.application.domain.repository.bm.BookDataRepository;

@Component
public class SeqKeyService {

    private static final String RANDOM_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private BookDataRepository bookDataRepository;

    /**
     * static_dataテーブルのseq_keyを生成する。
     * 値は "match_id-連番" 形式。連番は対象match_idの既存レコードの最大値+1。
     * 該当match_idのレコードが存在しない場合は1から採番する。
     *
     * @param home 対象試合のホームチーム名
     * @param away 対象試合のアウェーチーム名
     * @param matchId 対象試合ID（null許容）
     * @return 生成されたseq_key（例: "12345-1"）
     */
    public synchronized String create(String home, String away, String matchId) {
        // matchIdがnullの場合は乱数文字列をベースにしたキーを採番
        if (!StringUtils.hasValue(matchId)) {
            // 同じhome/awayの組み合わせで既存の登録があるか確認
            SeqKeyDTO existDto = bookDataRepository.findMatchId(home, away);
            if (existDto != null) {
                // 存在していれば語尾の連番を+1
                return nextRenban(existDto.getSeqKey());
            } else {
                // 存在していなければ乱数base + "-1"で新規採番
                return generateRandomStringAndChkSeqKey() + "-1";
            }
        } else {
            // match_idが含まれたseq_keyの最大連番を取得し、語尾の連番を+1
            SeqKeyDTO seqKey = bookDataRepository.findSeqKeyByMatchId(matchId);
            if (seqKey == null) {
                // 該当matchIdの登録が無ければ初回として1を採番
                return matchId + "-1";
            }
            return nextRenban(seqKey.getSeqKey());
        }
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
     * prefix部分に"-"が含まれるケース（例: match_id自体に"-"を含む）にも対応するため
     * 最後の"-"で分割する。
     * @param key 現在のseq_key（例: "12345-9"）
     * @return 連番を+1したseq_key（例: "12345-10"）
     */
    private String nextRenban(String key) {
        int idx = key.lastIndexOf('-');
        String prefix = key.substring(0, idx);
        int renban = Integer.parseInt(key.substring(idx + 1));
        return prefix + "-" + (renban + 1);
    }

}