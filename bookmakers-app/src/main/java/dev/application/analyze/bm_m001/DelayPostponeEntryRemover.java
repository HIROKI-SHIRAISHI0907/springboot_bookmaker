package dev.application.analyze.bm_m001;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * 試合が再開した（リアルタイムデータで通常の試合時間が取れた）ときに、
 * delay_postpone_＜日付＞(_N).json からその試合の「中断」「延期」「中止」を削除する（BM_M001）。
 *
 * <ul>
 *   <li>対象は前日分＋当日分のファイル全部（スクレイピングの出力も含む）。</li>
 *   <li>試合は mid が同じ、または ホーム・アウェー（NFKC・空白除去）が同じものを削除する。</li>
 *   <li>削除するのは status_type が 中断 / 延期 / 中止 / 再開（以前の出力の残り）の行だけ。遅延は残す
 *       （開始後の遅延は画面で表示しないため）。</li>
 *   <li>件数の項目（scraped_flagged_count など）は削除後の件数に直す。generated_at は変えない
 *       （読む側は generated_at の順で新しい状態を決めるため）。</li>
 *   <li>書き戻しは If-Match（読んだときの ETag）で行い、他で書き換えられていたら読み直して再試行する。</li>
 *   <li>失敗しても例外は投げない（ログのみ。次のリアルタイムデータで前回の状態が中断のままなら、もう一度削除を試みる）。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Component
public class DelayPostponeEntryRemover {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = DelayPostponeEntryRemover.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = DelayPostponeEntryRemover.class.getName();

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** 削除する status_type */
	private static final Set<String> REMOVE_STATUSES = Set.of(
			CsvMatchStatus.SUSPENDED, CsvMatchStatus.POSTPONED, CsvMatchStatus.CANCELLED, CsvMatchStatus.RESUMED);

	/** 配列名 → 件数の項目名 */
	private static final String[][] COUNT_FIELDS = {
			{ "scraped_flagged_matches", "scraped_flagged_count" },
			{ "matched_delay_postpone_games", "matched_count" },
			{ "carry_over_postponed_games", "carry_over_postponed_count" },
			{ "unresolved_unmatched_scraped_matches", "unresolved_unmatched_count" },
	};

	/** If-Match 失敗時の再試行回数 */
	private static final int MAX_RETRY = 3;

	@Autowired
	private PathConfig config;

	@Autowired
	private S3Operator s3Operator;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/** 削除する試合 */
	public static class Target {
		public final String mid;
		public final String home;
		public final String away;

		public Target(String mid, String home, String away) {
			this.mid = blankToNull(mid);
			this.home = blankToNull(home);
			this.away = blankToNull(away);
		}

		@Override
		public String toString() {
			return "mid=" + mid + ", home=" + home + ", away=" + away;
		}
	}

	/**
	 * 再開した試合の 中断・延期・中止 を delay_postpone から削除する。
	 *
	 * @param today 当日（日本時間）
	 * @param targets 再開した試合
	 * @return 削除した行の数
	 */
	public int remove(LocalDate today, List<Target> targets) {
		final String METHOD_NAME = "remove";
		if (targets == null || targets.isEmpty()) {
			return 0;
		}
		String bucket = this.config.getS3BucketsOutputsDelayPostpone();
		int removed = 0;
		for (LocalDate d : List.of(today.minusDays(1), today)) {
			String prefix = "delay_postpone_" + d;
			Pattern keyPattern = Pattern.compile("^" + Pattern.quote(prefix) + "(_\\d+)?\\.json$");
			List<S3Object> files;
			try {
				files = this.s3Operator.listObjectsByPrefixSortedByLastModifiedAsc(bucket, prefix, keyPattern);
			} catch (RuntimeException e) {
				log(METHOD_NAME, "delay_postpone の一覧取得に失敗（s3://" + bucket + "/" + prefix + "*）: " + e.getMessage());
				continue;
			}
			for (S3Object o : files) {
				removed += removeFromFile(bucket, o.key(), targets);
			}
		}
		log(METHOD_NAME, "再開した試合の中断・延期・中止を削除: " + removed + "行（試合: " + targets + "）");
		return removed;
	}

	/** 1ファイルから削除して書き戻す（削除した行の数） */
	private int removeFromFile(String bucket, String key, List<Target> targets) {
		final String METHOD_NAME = "removeFromFile";
		for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
			try {
				Optional<S3Operator.TextWithETag> cur = this.s3Operator.downloadTextUtf8WithETag(bucket, key);
				if (cur.isEmpty()) {
					return 0;
				}
				JsonNode root = MAPPER.readTree(cur.get().getText());
				if (!(root instanceof ObjectNode)) {
					return 0;
				}
				int removed = removeEntries((ObjectNode) root, targets);
				if (removed == 0) {
					return 0;
				}
				String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
				this.s3Operator.putTextConditional(bucket, key, json, cur.get().getETag());
				log(METHOD_NAME, "s3://" + bucket + "/" + key + " から " + removed + "行を削除");
				return removed;
			} catch (RuntimeException e) {
				if (this.s3Operator.isPreconditionFailed(e) && attempt < MAX_RETRY) {
					continue; // 読んだあとに書き換えられた → 読み直す
				}
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
						"delay_postpone の削除に失敗（処理は続行）: s3://" + bucket + "/" + key);
				return 0;
			} catch (Exception e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
						"delay_postpone の読み込みに失敗（処理は続行）: s3://" + bucket + "/" + key);
				return 0;
			}
		}
		return 0;
	}

	/**
	 * JSON から対象の試合の 中断・延期・中止・再開 を削除し、件数の項目を直す。
	 *
	 * @return 削除した行の数
	 */
	static int removeEntries(ObjectNode root, List<Target> targets) {
		int removed = 0;
		for (String[] f : COUNT_FIELDS) {
			JsonNode arr = root.get(f[0]);
			if (!(arr instanceof ArrayNode)) {
				continue;
			}
			ArrayNode array = (ArrayNode) arr;
			List<JsonNode> kept = new ArrayList<>();
			for (JsonNode n : array) {
				if (REMOVE_STATUSES.contains(PostponedMatchReader.text(n, "status_type")) && matches(n, targets)) {
					continue;
				}
				kept.add(n);
			}
			int diff = array.size() - kept.size();
			if (diff > 0) {
				array.removeAll();
				array.addAll(kept);
				removed += diff;
				if (root.has(f[1])) {
					root.put(f[1], array.size());
				}
			}
		}
		return removed;
	}

	/** mid が同じ、または ホーム・アウェーが同じ */
	private static boolean matches(JsonNode n, List<Target> targets) {
		String mid = PostponedMatchReader.text(n, "mid");
		String home = PostponedMatchReader.text(n, "home");
		String away = PostponedMatchReader.text(n, "away");
		for (Target t : targets) {
			if (t.mid != null && t.mid.equals(mid)) {
				return true;
			}
			if (t.home != null && t.away != null && home != null && away != null
					&& PostponedMatchReader.squash(t.home).equals(PostponedMatchReader.squash(home))
					&& PostponedMatchReader.squash(t.away).equals(PostponedMatchReader.squash(away))) {
				return true;
			}
		}
		return false;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private void log(String methodName, String message) {
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, methodName, MessageCdConst.MCD00099I_LOG, message);
	}
}
