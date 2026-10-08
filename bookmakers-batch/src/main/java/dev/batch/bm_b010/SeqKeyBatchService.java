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
 *
 * <p>2026-10 修正</p>
 * <ul>
 *   <li>チーム名が空なら採番しない（例外）。</li>
 *   <li>「同じ試合」を、同じ match_id（または seq_key の接頭辞）か、
 *       同じカードで記録時刻が前後 6 時間以内の行に限定。
 *       以前は同じカードの全期間の行を見ていたため、去年の同じカードの試合に連番が続いたり、
 *       正式な match_id が来たときに去年の試合の行まで今年の match_id に振り直されていた。
 *       チーム名が空の行では、別々の試合が全部まとめて振り直されていた。</li>
 *   <li>振り直すのは match_id がまだ無い（乱数で採番した）行だけ。別の正式な match_id の行は触らない。</li>
 * </ul>
 *
 * ここから先（既存seq_keyの読み取り〜呼び出し元でのINSERT）は、
 * FinGettingStat#finGettingStat() のトランザクション内で bookDataRepository.lockByTeams()
 * によるPostgreSQLのトランザクションスコープ・アドバイザリロックに守られている。
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
	 * 互換用（記録時刻なし＝現在時刻を基準に「同じ試合」を探す）。
	 * 新しいコードは {@link #create(String, String, String, String)} を使うこと。
	 */
	public synchronized String create(String home, String away, String matchId) {
		return create(home, away, matchId, null);
	}

	/**
	 * static_dataテーブルのseq_keyを生成する。
	 * 値は "match_id(または乱数)-連番" 形式。
	 *
	 * 1) match_idが来ている場合は、そのmatch_id（または接頭辞）の最大の連番+1
	 *    （同じ試合に match_id 未確定の行があれば、その続きに振り直してから+1）
	 * 2) match_idがnullで、同じ試合の行が無い場合はランダム値をベースにする
	 * 3) match_idがnullで、同じ試合の行がある場合は、その最新の行の連番+1（接頭辞を引き継ぐ）
	 *
	 * @param home 対象試合のホームチーム名（空不可）
	 * @param away 対象試合のアウェーチーム名（空不可）
	 * @param matchId 対象試合ID（null許容）
	 * @param baseTime 対象行の記録時刻（"yyyy-MM-dd HH:mm:ss" JST。null・空なら現在時刻）
	 * @return 生成されたseq_key（例: "12345678-1"）
	 */
	public synchronized String create(String home, String away, String matchId, String baseTime) {
		if (!StringUtils.hasValue(trim(home)) || !StringUtils.hasValue(trim(away))) {
			throw new IllegalArgumentException("チーム名が空のため seq_key を採番できません: home=[" + home
					+ "], away=[" + away + "], matchId=" + matchId);
		}
		// この対戦カードに対する一連の処理（既存seq_key読み取り〜INSERT）を
		// プロセス（ECSタスク）をまたいで直列化する。
		bookDataRepository.lockByTeams(home, away);

		if (StringUtils.hasValue(matchId)) {
			// ---- 1) 正式なmatch_idが来ているケース ----
			SeqKeyDTO latest = bookDataRepository.findSeqKeyByMatchIdOrPrefix(matchId);
			int start = latest == null ? 0 : renbanOf(latest.getSeqKey());
			// 同じ試合で match_id 未確定（乱数ベース）の行があれば、正式な match_id の続きに振り直す
			List<SeqKeyDTO> provisional = bookDataRepository.findProvisionalSeqKeys(home, away, baseTime);
			if (provisional != null && !provisional.isEmpty()) {
				return renumber(matchId, provisional, start);
			}
			return matchId + "-" + (start + 1);
		}

		// ---- 2) 3) match_idが来ていないケース ----
		SeqKeyDTO latest = bookDataRepository.findLatestSeqKeyForCard(home, away, baseTime);
		if (latest == null || !StringUtils.hasValue(latest.getSeqKey())) {
			return generateRandomStringAndChkSeqKey() + "-1";
		}
		return nextRenban(latest.getSeqKey());
	}

	/**
	 * match_id 未確定の行を「matchId-(start+1)」から順に振り直し、新規行用のseq_keyを返す。
	 *
	 * 1件ずつ直接上書きすると、振り直し先のキーを別の行が使っていた場合に主キー重複になるため、
	 * 1段階目で一時キー（"~" + 元のseq_key）へ退避してから、2段階目で振り直す。
	 *
	 * @param matchId 正式なmatch_id
	 * @param targets 振り直す行（登録が古い順 → 連番の小さい順）
	 * @param start この番号の次から振る（既存の matchId の最大の連番。無ければ 0）
	 * @return 新規レコード用のseq_key
	 */
	private String renumber(String matchId, List<SeqKeyDTO> targets, int start) {
		List<String> oldKeys = targets.stream()
				.map(SeqKeyDTO::getSeqKey)
				.collect(Collectors.toList());

		// 1段階目: 一時キーへ退避
		bookDataRepository.moveSeqKeysToTemp(TEMP_PREFIX, oldKeys);

		// 2段階目: 古い順に start+1 から振り直す
		int renban = start;
		for (String oldKey : oldKeys) {
			renban++;
			bookDataRepository.updateSeqKey(TEMP_PREFIX + oldKey, matchId + "-" + renban, matchId);
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
		return prefix + "-" + (renbanOf(key) + 1);
	}

	/** seq_key の語尾の連番（読めなければ 0） */
	private static int renbanOf(String key) {
		if (key == null) {
			return 0;
		}
		int idx = key.lastIndexOf('-');
		if (idx < 0) {
			return 0;
		}
		try {
			return Integer.parseInt(key.substring(idx + 1));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static String trim(String s) {
		return s == null ? null : s.trim();
	}
}
