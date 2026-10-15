package dev.application.analyze.bm_m001;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * S3 の延期・遅延情報（delay_postpone_＜日付＞.json / _N.json）から試合ごとの最後の状態を読む
 * （FutureStartFlgService: 今、延期になっている試合 / SuspensionStatusPublisher: 前回出力した状態）。
 *
 * <ul>
 *   <li>前日分＋当日分のファイルを最終更新日時の古い順に読み、試合ごとに<b>一番新しい状態</b>を採用する
 *       （中断 → 延期 → 再開 のように変わった場合は最後の状態）。</li>
 *   <li>最後の状態が「延期」の試合だけを返す。</li>
 *   <li>試合は mid（URL の ?mid=）で、無ければ ホーム・アウェー（NFKC・空白除去）で同一判定する。</li>
 *   <li>読み込みに失敗しても例外は投げない（空の一覧を返し、ログを出す）。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Component
public class PostponedMatchReader {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = PostponedMatchReader.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = PostponedMatchReader.class.getName();

	/** 延期 */
	private static final String POSTPONED = "延期";

	/** JSON の中で試合が入っている配列（後ろのものほど優先） */
	private static final String[] MATCH_ARRAYS = {
			"scraped_flagged_matches",
			"unresolved_unmatched_scraped_matches",
			"carry_over_postponed_games",
			"matched_delay_postpone_games",
	};

	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

	private static final DateTimeFormatter LOCAL_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Autowired
	private PathConfig config;

	@Autowired
	private S3Operator s3Operator;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/** 試合と最後の状態 */
	public static class PostponedMatch {
		/** 最後の状態（延期 / 中断 / 遅延 / 再開 / 中止） */
		public String status;
		public String mid;
		public String home;
		public String away;
		/** 元の試合開始日時（"yyyy-MM-dd HH:mm:ss"・日本時間。分からなければ null） */
		public String start;

		@Override
		public String toString() {
			return "status=" + status + ", mid=" + mid + ", home=" + home + ", away=" + away + ", start=" + start;
		}
	}

	/**
	 * 今、延期になっている試合（前日分＋当日分の delay_postpone で、最後の状態が「延期」のもの）。
	 *
	 * @param today 当日（日本時間）
	 * @return 延期中の試合（読めなければ空）
	 */
	public List<PostponedMatch> loadPostponed(LocalDate today) {
		final String METHOD_NAME = "loadPostponed";
		List<PostponedMatch> out = new ArrayList<>();
		for (PostponedMatch m : loadLatestStatuses(today).values()) {
			if (POSTPONED.equals(m.status)) {
				out.add(m);
			}
		}
		log(METHOD_NAME, "延期中の試合: " + out.size() + "件");
		return out;
	}

	/**
	 * 試合ごとの最後の状態（前日分＋当日分の delay_postpone）。
	 *
	 * @param today 当日（日本時間）
	 * @return 試合キー（{@link #matchKey(String, String, String)}）→ 最後の状態。読めなければ空
	 */
	public Map<String, PostponedMatch> loadLatestStatuses(LocalDate today) {
		final String METHOD_NAME = "loadLatestStatuses";
		Map<String, PostponedMatch> out = new LinkedHashMap<>();
		String bucket;
		try {
			bucket = config.getS3BucketsOutputsDelayPostpone();
		} catch (RuntimeException e) {
			log(METHOD_NAME, "延期情報のバケット名を取得できません（延期の戻しはしない）: " + e.getMessage());
			return out;
		}

		List<S3Object> targets = new ArrayList<>();
		for (LocalDate d : List.of(today.minusDays(1), today)) {
			String prefix = "delay_postpone_" + d;
			Pattern keyPattern = Pattern.compile("^" + Pattern.quote(prefix) + "(_\\d+)?\\.json$");
			try {
				targets.addAll(s3Operator.listObjectsByPrefixSortedByLastModifiedAsc(bucket, prefix, keyPattern));
			} catch (RuntimeException e) {
				log(METHOD_NAME, "延期情報の一覧取得に失敗（s3://" + bucket + "/" + prefix + "*）: " + e.getMessage());
			}
		}
		targets.sort(Comparator.comparing((S3Object o) -> o.lastModified() == null ? Instant.MAX : o.lastModified())
				.thenComparing(S3Object::key));

		// 試合キー → 最後の状態
		Map<String, JsonNode> latest = new LinkedHashMap<>();
		Map<String, String> latestStatus = new LinkedHashMap<>();
		for (S3Object o : targets) {
			try {
				JsonNode root = MAPPER.readTree(s3Operator.downloadTextUtf8(bucket, o.key()));
				for (String arrayName : MATCH_ARRAYS) {
					JsonNode arr = root.path(arrayName);
					if (!arr.isArray()) {
						continue;
					}
					for (JsonNode n : arr) {
						String status = text(n, "status_type");
						String key = matchKey(n);
						if (status == null || key == null) {
							continue;
						}
						// 同じファイル内で matched（ECS の開始時刻あり）を優先するため、後ろの配列で上書き
						latest.put(key, n);
						latestStatus.put(key, status);
					}
				}
			} catch (Exception e) {
				log(METHOD_NAME, "延期情報の読み込みに失敗（s3://" + bucket + "/" + o.key() + "）: " + e.getMessage());
			}
		}

		for (Map.Entry<String, String> e : latestStatus.entrySet()) {
			JsonNode n = latest.get(e.getKey());
			PostponedMatch m = new PostponedMatch();
			m.status = e.getValue();
			m.mid = text(n, "mid");
			m.home = text(n, "home");
			m.away = text(n, "away");
			m.start = toLocal(text(n, "start"));
			out.put(e.getKey(), m);
		}
		log(METHOD_NAME, "延期・中断などの情報: " + out.size() + "試合（読んだファイル: " + targets.size() + "件）");
		return out;
	}

	private static String matchKey(JsonNode n) {
		return matchKey(text(n, "mid"), text(n, "home"), text(n, "away"));
	}

	/**
	 * 試合キー（mid 優先。無ければ ホーム・アウェー（NFKC・空白除去））。作れなければ null。
	 */
	public static String matchKey(String mid, String home, String away) {
		if (mid != null && !mid.isBlank()) {
			return "mid:" + mid.trim();
		}
		if (home == null || away == null || home.isBlank() || away.isBlank()) {
			return null;
		}
		return "team:" + squash(home) + "\u0000" + squash(away);
	}

	private static String squash(String s) {
		return Normalizer.normalize(s, Normalizer.Form.NFKC).replaceAll("\\s+", "");
	}

	/** ISO（+09:00）→ "yyyy-MM-dd HH:mm:ss"（日本時間）。読めなければ null */
	private static String toLocal(String iso) {
		if (iso == null) {
			return null;
		}
		try {
			return OffsetDateTime.parse(iso).atZoneSameInstant(JST).toLocalDateTime().format(LOCAL_FMT);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String text(JsonNode n, String field) {
		if (n == null) {
			return null;
		}
		JsonNode v = n.get(field);
		if (v == null || v.isNull()) {
			return null;
		}
		String s = v.asText().trim();
		return s.isEmpty() ? null : s;
	}

	private void log(String methodName, String message) {
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, methodName, MessageCdConst.MCD00099I_LOG, message);
	}
}
