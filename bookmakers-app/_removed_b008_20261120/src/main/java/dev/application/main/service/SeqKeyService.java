package dev.application.main.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.util.StringUtils;

import dev.application.domain.repository.bm.BookDataRepository;

/**
 * seq_key発番処理
 *
 * <p>修正点</p>
 * <ul>
 *   <li>チーム名が空なら採番しない（例外）。空のチーム名で同じカードを探すと、
 *       チーム名が空の行（別々の試合）が全部「同じ試合」として見つかり、
 *       overwriteAndAppend でそれらの seq_key・match_id がまとめて書き換わっていた。</li>
 *   <li>overwriteAndAppend で書き換えるのは「match_id がまだ無い（乱数で採番した）行」だけにする。
 *       別の正式な match_id を持つ行は、別の試合なので触らない。</li>
 *   <li>同じカードの最新行は「登録日時 → 連番（数値）」の順で決める（findMatchId の ORDER BY）。
 *       register_time は CURRENT_TIMESTAMP（トランザクション開始時刻）なので、同じトランザクションの行は同じ値になる。</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@Component
public class SeqKeyService {

	private static final String RANDOM_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

	private static final SecureRandom RANDOM = new SecureRandom();

	@Autowired
	private BookDataRepository bookDataRepository;

	/**
	 * static_dataテーブルのseq_keyを生成する。
	 * 値は "match_id(または乱数)-連番" 形式。
	 *
	 * 1) match_idが来ている場合は、そのmatch_idをもとに連番を付与する
	 * 2) match_idがnullで、過去も一度もmatch_idが確定していない場合はランダム値をベースにする
	 * 3) 過去はランダム値だった試合群に、今回正式なmatch_idが来た場合は、
	 *    過去分（match_id が無い行だけ）を正式なmatch_idへ書き換えたうえで連番を振り直す
	 * 4) 過去は正式なmatch_idだった試合群で、今回match_idがnullの場合は、
	 *    過去の正式なmatch_idを引き継いで連番だけ+1する
	 *
	 * @param home 対象試合のホームチーム名（空不可）
	 * @param away 対象試合のアウェーチーム名（空不可）
	 * @param matchId 対象試合ID（null許容）
	 * @return 生成されたseq_key（例: "12345678-1"）
	 */
	public synchronized String create(String home, String away, String matchId) {
		if (!StringUtils.hasValue(trim(home)) || !StringUtils.hasValue(trim(away))) {
			throw new IllegalArgumentException("チーム名が空のため seq_key を採番できません: home=[" + home
					+ "], away=[" + away + "], matchId=" + matchId);
		}
		if (StringUtils.hasValue(matchId)) {
			// 1) 正式なmatch_idそのもので直接検索する（他カードと混同しない一番安全な経路）
			SeqKeyDTO latestForThisMatch = bookDataRepository.findSeqKeyByMatchId(matchId);
			if (latestForThisMatch != null && StringUtils.hasValue(latestForThisMatch.getSeqKey())) {
				return nextRenban(latestForThisMatch.getSeqKey());
			}
			// 2) このmatch_idでの登録がまだ無い場合のみ、
			//    「進行中」の同一カードに match_id 未確定（乱数ベース）の行が無いか確認する
			List<SeqKeyDTO> existDto = bookDataRepository.findMatchId(home, away);
			List<SeqKeyDTO> provisional = new ArrayList<>();
			if (existDto != null) {
				for (SeqKeyDTO dto : existDto) {
					if (!StringUtils.hasValue(trim(dto.getMatchId()))) {
						provisional.add(dto);
					}
				}
			}
			if (provisional.isEmpty()) {
				// 初回登録（同じカードに別の正式な match_id の行があっても、それは別の試合なので触らない）
				return matchId + "-1";
			}
			// それまでランダム値だった進行中の試合群に、正式なmatch_idが連携された
			// → match_id 未確定の行だけを正式match_idへ書き換えて連番を振り直す
			return overwriteAndAppend(matchId, provisional);
		} else {
			// ---- match_idが来ていないケース ----
			List<SeqKeyDTO> existDto = bookDataRepository.findMatchId(home, away);
			if (existDto == null || existDto.isEmpty()) {
				return generateRandomStringAndChkSeqKey() + "-1";
			}
			// 最新の行（登録日時 → 連番の降順の先頭）の連番+1
			return nextRenban(existDto.get(0).getSeqKey());
		}
	}

	/**
	 * 過去分のseq_keyを正式なmatch_idベースに書き換えたうえで、
	 * 新規レコード用のseq_keyを返す。
	 *
	 * @param matchId 正式なmatch_id
	 * @param existDto 登録日時・連番の降順の既存レコード（match_id 未確定の行だけ）
	 * @return 新規レコード用のseq_key
	 */
	private String overwriteAndAppend(String matchId, List<SeqKeyDTO> existDto) {
		// 古い順に並べ直してから連番を1から振り直す
		List<SeqKeyDTO> ascending = new ArrayList<>(existDto);
		Collections.reverse(ascending);

		int renban = 0;
		for (SeqKeyDTO dto : ascending) {
			renban++;
			String newSeqKey = matchId + "-" + renban;
			bookDataRepository.updateSeqKey(dto.getSeqKey(), newSeqKey, matchId);
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

	private static String trim(String s) {
		return s == null ? null : s.trim();
	}

}
