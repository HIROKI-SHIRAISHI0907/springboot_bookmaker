package dev.web.api.bm_a030;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.config.PathConfig;
import dev.common.s3.S3Operator;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * S3 の延期・遅延情報（delay_postpone_＜日付＞.json / delay_postpone_＜日付＞_N.json）を読む
 *
 * <ul>
 *   <li>S3 へのアクセスは共通の {@link S3Operator} を使う（S3Client は DI されたもの）。
 *       一覧は {@link S3Operator#listObjectsByPrefixSortedByLastModifiedAsc} で「delay_postpone_＜日付＞」に絞って取る。</li>
 *   <li>バケット直下だけを見る（json/ フォルダの中は対象外）。</li>
 *   <li>指定日のファイルは全部読む。同じ試合が複数ファイルにあれば、最終更新日時が新しいファイルの値を使う。
 *       （連番の大小と更新順は一致しない。例: _2 が 06:02、番号なしが 12:01）</li>
 *   <li>試合は mid（URL の ?mid=）で突き合わせる。mid で見つからなければ home / away（NFKC・空白除去）で突き合わせる。</li>
 *   <li>読み込みに失敗しても画面は止めない（ログを出して「延期情報なし」として扱う）。</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DelayPostponeS3Reader {

	/** JSON の中で試合が入っている配列（後ろのものほど優先） */
	private static final String[] MATCH_ARRAYS = {
			"scraped_flagged_matches",
			"carry_over_postponed_games",
			"matched_delay_postpone_games",
	};

	private final ObjectMapper objectMapper;

	private final PathConfig config;

	private final S3Operator s3Operator;

	/**
	 * 指定日の延期・遅延情報
	 */
	public DelayPostponeIndex load(LocalDate date) {
		DelayPostponeIndex index = new DelayPostponeIndex();
		String bucket = config.getS3BucketsOutputsDelayPostpone();
		String prefix = "delay_postpone_" + date;
		// バケット直下の delay_postpone_yyyy-MM-dd.json / delay_postpone_yyyy-MM-dd_N.json
		Pattern keyPattern = Pattern.compile("^" + Pattern.quote(prefix) + "(_\\d+)?\\.json$");

		List<S3Object> targets;
		try {
			// prefix で S3 側で絞り、最終更新日時の昇順（古い順に読み、新しいファイルで上書きする）
			targets = s3Operator.listObjectsByPrefixSortedByLastModifiedAsc(bucket, prefix, keyPattern);
		} catch (Exception e) {
			log.warn("延期・遅延情報の一覧取得に失敗しました（s3://{}/{}*）", bucket, prefix, e);
			return index;
		}

		for (S3Object o : targets) {
			try {
				String json = s3Operator.downloadTextUtf8(bucket, o.key());
				readJson(json, o.key(), index);
			} catch (Exception e) {
				log.warn("延期・遅延情報の読み込みに失敗しました（s3://{}/{}）", bucket, o.key(), e);
			}
		}
		// 反映されないときの切り分け用（読んだファイルと拾えた試合の数）
		log.info("延期・遅延情報: bucket={}, date={}, files={}, matches={}",
				bucket, date, targets.stream().map(S3Object::key).collect(Collectors.toList()), index.size());
		return index;
	}

	/** 1ファイル分を index に入れる（同じ試合は後から読んだものが勝つ） */
	void readJson(String json, String sourceKey, DelayPostponeIndex index) throws Exception {
		JsonNode root = objectMapper.readTree(json);
		for (String arrayName : MATCH_ARRAYS) {
			JsonNode arr = root.path(arrayName);
			if (!arr.isArray()) {
				continue;
			}
			for (JsonNode n : arr) {
				String statusType = text(n, "status_type");
				if (statusType == null) {
					continue;
				}
				DelayPostponeInfo info = new DelayPostponeInfo();
				info.setStatusType(statusType);
				String statusText = text(n, "status_text");
				info.setStatusText(statusText != null ? statusText : text(n, "raw_status_text"));
				info.setMid(text(n, "mid"));
				info.setHome(text(n, "home"));
				info.setAway(text(n, "away"));
				info.setSourceKey(sourceKey);
				index.put(info);
			}
		}
	}

	private static String text(JsonNode n, String field) {
		JsonNode v = n.get(field);
		if (v == null || v.isNull()) {
			return null;
		}
		String s = v.asText().trim();
		return s.isEmpty() ? null : s;
	}

	static String teamKey(String home, String away) {
		return squash(home) + "\u0000" + squash(away);
	}

	static String squash(String s) {
		return s == null ? "" : Normalizer.normalize(s, Normalizer.Form.NFKC).replaceAll("\\s+", "");
	}

	/** 延期・遅延の1試合分 */
	@Data
	public static class DelayPostponeInfo {
		/** 延期 / 遅延 */
		private String statusType;
		/** 画面に出ていた文言 */
		private String statusText;
		private String mid;
		private String home;
		private String away;
		/** 読み込んだ S3 キー */
		private String sourceKey;
	}

	/** mid / home・away で引ける索引 */
	public static class DelayPostponeIndex {
		private final Map<String, DelayPostponeInfo> byMid = new HashMap<>();
		private final Map<String, DelayPostponeInfo> byTeams = new HashMap<>();

		void put(DelayPostponeInfo info) {
			if (info.getMid() != null) {
				byMid.put(info.getMid(), info);
			}
			if (info.getHome() != null && info.getAway() != null) {
				byTeams.put(teamKey(info.getHome(), info.getAway()), info);
			}
		}

		/** mid を優先し、無ければ home / away で探す。見つからなければ null */
		public DelayPostponeInfo find(String mid, String home, String away) {
			if (mid != null && !mid.isBlank()) {
				DelayPostponeInfo i = byMid.get(mid.trim());
				if (i != null) {
					return i;
				}
			}
			if (home == null || away == null) {
				return null;
			}
			return byTeams.get(teamKey(home, away));
		}

		public boolean isEmpty() {
			return byMid.isEmpty() && byTeams.isEmpty();
		}

		/** 拾えた試合の数（mid・チーム名のどちらかで引けるもの） */
		public int size() {
			return Math.max(byMid.size(), byTeams.size());
		}
	}
}
