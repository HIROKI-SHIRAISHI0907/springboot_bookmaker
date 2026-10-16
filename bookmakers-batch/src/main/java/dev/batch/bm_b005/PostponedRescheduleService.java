package dev.batch.bm_b005;

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
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.batch.repository.master.FutureRescheduleRepository;
import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.entity.FutureEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * 延期の試合の日程変更（B005。2026-10 追加）。
 *
 * <h2>何をするか</h2>
 * <p>
 * 延期になった試合は、Flashscore 上で別の日に変わることがある。B005 で取り込む試合予定の中に、
 * delay_postpone で延期になっている試合があり、日時が変わっていれば、future_master の同じ予定の future_time を新しい日時に上書きし、
 * delay_postpone からその試合の延期を削除する（新しい日では「データなし／開始前」と表示される）。
 * 日程がまだ決まっていない（取り込んだ予定の日時が同じ）ときは何もしない（延期の表示のまま）。
 * </p>
 *
 * <h2>突き合わせ（チーム名の表記揺れ対策）</h2>
 * <ol>
 *   <li>取り込む予定 ⇔ delay_postpone の延期: mid が一致、無ければチーム名（NFKC・空白除去）が一致し、カテゴリの「国: リーグ」が同じ
 *       （どちらかのカテゴリが空なら国・リーグは見ない）。</li>
 *   <li>delay_postpone の延期 ⇔ future_master の予定: mid、無ければ delay_postpone のチーム名（NFKC）。
 *       試合開始日時が元の試合開始日時（無ければ延期を検知した日時）の前後1日で、start_flg = 0 のもの。</li>
 *   <li>新しい日時は今の日時から60日以内のときだけ上書きする（同じカードの別の試合を結び付けないため）。</li>
 * </ol>
 *
 * <h2>読む delay_postpone</h2>
 * <p>バケット直下の delay_postpone_＜日付＞(_N).json のうち、今日から {@link #LOOKBACK_DAYS} 日前まで
 * （延期になってから日程が決まるまで日数がかかるため）。試合ごとの最新の状態（generated_at の新しい順）が「延期」のものだけ。</p>
 *
 * <h2>削除</h2>
 * <p>上書きできた試合だけ、読んだ期間の全ファイルから 延期・中断・中止・再開 の行を削除する（遅延は残す）。
 * 書き戻しは If-Match（読んだときの ETag）。generated_at は変えない。削除に失敗しても例外は投げない
 * （画面・start_flg 側は日付が合わない延期情報を使わないので、表示は崩れない）。</p>
 * @author shiraishitoshio
 */
@Component
public class PostponedRescheduleService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = PostponedRescheduleService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = PostponedRescheduleService.class.getName();

	/** 何日前までの delay_postpone を読むか */
	static final int LOOKBACK_DAYS = 14;

	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

	private static final DateTimeFormatter LOCAL_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final Pattern KEY_PATTERN = Pattern.compile("^delay_postpone_(\\d{4}-\\d{2}-\\d{2})(?:_(\\d+))?\\.json$");

	private static final Pattern MID_PATTERN = Pattern.compile("[?&]mid=([^&\\s]+)");

	/** JSON の中で試合が入っている配列（後ろのものほど優先） */
	private static final String[] MATCH_ARRAYS = {
			"scraped_flagged_matches",
			"unresolved_unmatched_scraped_matches",
			"carry_over_postponed_games",
			"matched_delay_postpone_games",
	};

	/** 配列名 → 件数の項目名 */
	private static final String[][] COUNT_FIELDS = {
			{ "scraped_flagged_matches", "scraped_flagged_count" },
			{ "matched_delay_postpone_games", "matched_count" },
			{ "carry_over_postponed_games", "carry_over_postponed_count" },
			{ "unresolved_unmatched_scraped_matches", "unresolved_unmatched_count" },
	};

	private static final String POSTPONED = "延期";

	/** 日程変更のときに削除する status_type */
	private static final Set<String> REMOVE_STATUSES = Set.of("延期", "中断", "中止", "再開");

	/** If-Match 失敗時の再試行回数 */
	private static final int MAX_RETRY = 3;

	@Autowired
	private FutureRescheduleRepository futureRescheduleRepository;

	@Autowired
	private PathConfig config;

	@Autowired
	private S3Operator s3Operator;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/** delay_postpone の延期1試合 */
	public static class PostponedEntry {
		public String mid;
		public String home;
		public String away;
		public String category;
		/** 元の試合開始日時（"yyyy-MM-dd HH:mm:ss"・日本時間。無ければ null） */
		public String start;
		/** 延期を検知した日時（"yyyy-MM-dd HH:mm:ss"・日本時間） */
		public String detectedAt;

		String baseTime() {
			return start != null ? start : detectedAt;
		}

		@Override
		public String toString() {
			return "mid=" + mid + ", home=" + home + ", away=" + away + ", category=" + category
					+ ", start=" + start + ", detectedAt=" + detectedAt;
		}
	}

	/** 日程を変えた試合（delay_postpone から削除する） */
	public static class Rescheduled {
		public final String mid;
		public final String home;
		public final String away;
		/** 取り込んだ予定の mid（試合ページが変わった場合） */
		public final String newMid;

		Rescheduled(PostponedEntry e, String newMid) {
			this.mid = e.mid;
			this.home = e.home;
			this.away = e.away;
			this.newMid = newMid;
		}

		@Override
		public String toString() {
			return "mid=" + mid + ", newMid=" + newMid + ", home=" + home + ", away=" + away;
		}
	}

	/**
	 * 延期になっている試合（今日から LOOKBACK_DAYS 日前までの delay_postpone で、最新の状態が延期のもの）。
	 * 読めなければ空（ログのみ）。
	 */
	public List<PostponedEntry> loadPostponed() {
		final String METHOD_NAME = "loadPostponed";
		List<PostponedEntry> out = new ArrayList<>();
		String bucket;
		try {
			bucket = this.config.getS3BucketsOutputsDelayPostpone();
		} catch (RuntimeException e) {
			log(METHOD_NAME, "延期情報のバケット名を取得できません（日程変更はしない）: " + e.getMessage());
			return out;
		}

		List<Loaded> files = loadFiles(bucket);
		files.sort(Comparator.comparing((Loaded f) -> f.generatedAt).thenComparing(f -> f.key));

		Map<String, JsonNode> latest = new LinkedHashMap<>();
		Map<String, Instant> latestFileTime = new LinkedHashMap<>();
		for (Loaded f : files) {
			for (String arrayName : MATCH_ARRAYS) {
				JsonNode arr = f.root.path(arrayName);
				if (!arr.isArray()) {
					continue;
				}
				for (JsonNode n : arr) {
					String key = matchKey(text(n, "mid"), text(n, "home"), text(n, "away"));
					if (key == null || text(n, "status_type") == null) {
						continue;
					}
					latest.put(key, n);
					latestFileTime.put(key, f.generatedAt);
				}
			}
		}
		for (Map.Entry<String, JsonNode> e : latest.entrySet()) {
			JsonNode n = e.getValue();
			if (!POSTPONED.equals(text(n, "status_type"))) {
				continue;
			}
			PostponedEntry p = new PostponedEntry();
			p.mid = text(n, "mid");
			if (p.mid == null) {
				p.mid = extractMid(text(n, "url"));
			}
			p.home = text(n, "home");
			p.away = text(n, "away");
			p.category = text(n, "category");
			p.start = toLocal(text(n, "start"));
			p.detectedAt = toLocal(text(n, "scraped_at"));
			if (p.detectedAt == null) {
				p.detectedAt = toLocal(text(n.path("scraped"), "scraped_at"));
			}
			if (p.detectedAt == null) {
				Instant t = latestFileTime.get(e.getKey());
				if (t != null && !Instant.MAX.equals(t)) {
					p.detectedAt = t.atZone(JST).toLocalDateTime().format(LOCAL_FMT);
				}
			}
			out.add(p);
		}
		log(METHOD_NAME, "延期中の試合: " + out.size() + "件（読んだファイル: " + files.size() + "件）");
		return out;
	}

	/**
	 * 取り込む予定が延期中の試合で、日時が変わっていれば future_master を上書きする。
	 *
	 * @param entity 取り込む予定
	 * @param postponed {@link #loadPostponed()} の結果
	 * @return 上書きした場合はその試合（呼び出し側は登録しない）。それ以外は null
	 */
	public Rescheduled tryReschedule(FutureEntity entity, List<PostponedEntry> postponed) {
		final String METHOD_NAME = "tryReschedule";
		if (entity == null || postponed == null || postponed.isEmpty()) {
			return null;
		}
		PostponedEntry p = findPostponed(entity, postponed);
		if (p == null || p.baseTime() == null) {
			return null;
		}
		FutureRescheduleRepository.Row target = this.futureRescheduleRepository.findRescheduleTarget(p.mid, p.home, p.away, p.baseTime());
		if (target == null) {
			return null;
		}
		int n = this.futureRescheduleRepository.updateRescheduledTime(
				target.getSeq(), entity.getFutureTime(), entity.getGameLink());
		if (n != 1) {
			// 日時が同じ（まだ延期のまま）・60日を超えて離れている
			return null;
		}
		log(METHOD_NAME, "延期の試合の日程を変更しました: seq=" + target.getSeq() + ", " + target.getFutureTime()
				+ " → " + entity.getFutureTime() + " (" + p + ")");
		return new Rescheduled(p, extractMid(entity.getGameLink()));
	}

	/**
	 * 日程を変えた試合の 延期・中断・中止・再開 を delay_postpone から削除する（失敗してもログのみ）。
	 *
	 * @return 削除した行の数
	 */
	public int removeFromDelayPostpone(List<Rescheduled> targets) {
		final String METHOD_NAME = "removeFromDelayPostpone";
		if (targets == null || targets.isEmpty()) {
			return 0;
		}
		String bucket;
		try {
			bucket = this.config.getS3BucketsOutputsDelayPostpone();
		} catch (RuntimeException e) {
			log(METHOD_NAME, "延期情報のバケット名を取得できません（削除しない）: " + e.getMessage());
			return 0;
		}
		int removed = 0;
		for (S3Object o : listFiles(bucket)) {
			removed += removeFromFile(bucket, o.key(), targets);
		}
		log(METHOD_NAME, "日程を変えた試合の延期を delay_postpone から削除: " + removed + "行（試合: " + targets + "）");
		return removed;
	}

	/* ---------------- 突き合わせ ---------------- */

	/** 取り込む予定に当たる延期（mid → チーム名＋国・リーグ）。無ければ null */
	static PostponedEntry findPostponed(FutureEntity entity, List<PostponedEntry> postponed) {
		String mid = extractMid(entity.getGameLink());
		if (mid != null) {
			for (PostponedEntry p : postponed) {
				if (mid.equals(p.mid)) {
					return p;
				}
			}
		}
		String home = squash(entity.getHomeTeamName());
		String away = squash(entity.getAwayTeamName());
		if (home.isEmpty() || away.isEmpty()) {
			return null;
		}
		for (PostponedEntry p : postponed) {
			if (home.equals(squash(p.home)) && away.equals(squash(p.away))
					&& sameCountryLeague(entity.getGameTeamCategory(), p.category)) {
				return p;
			}
		}
		return null;
	}

	/** 「国: リーグ」が同じ（どちらかが空なら true） */
	static boolean sameCountryLeague(String a, String b) {
		String x = countryLeague(a);
		String y = countryLeague(b);
		return x.isEmpty() || y.isEmpty() || x.equals(y);
	}

	/** 「国: リーグ - ラウンドN」→「国:リーグ」（NFKC・空白除去・小文字） */
	static String countryLeague(String category) {
		if (category == null) {
			return "";
		}
		String c = category.split(" - ", 2)[0];
		return squash(c).replace('：', ':').toLowerCase();
	}

	/* ---------------- S3 ---------------- */

	/** 読む期間の delay_postpone の一覧 */
	private List<S3Object> listFiles(String bucket) {
		final String METHOD_NAME = "listFiles";
		List<S3Object> out = new ArrayList<>();
		LocalDate today = LocalDate.now(JST);
		LocalDate min = today.minusDays(LOOKBACK_DAYS);
		try {
			for (S3Object o : this.s3Operator.listObjectsByPrefixSortedByLastModifiedAsc(bucket, "delay_postpone_", KEY_PATTERN)) {
				Matcher m = KEY_PATTERN.matcher(o.key());
				if (!m.matches()) {
					continue;
				}
				LocalDate d = LocalDate.parse(m.group(1));
				if (!d.isBefore(min) && !d.isAfter(today.plusDays(1))) {
					out.add(o);
				}
			}
		} catch (RuntimeException e) {
			log(METHOD_NAME, "delay_postpone の一覧取得に失敗: " + e.getMessage());
		}
		return out;
	}

	private List<Loaded> loadFiles(String bucket) {
		final String METHOD_NAME = "loadFiles";
		List<Loaded> out = new ArrayList<>();
		for (S3Object o : listFiles(bucket)) {
			try {
				JsonNode root = MAPPER.readTree(this.s3Operator.downloadTextUtf8(bucket, o.key()));
				out.add(new Loaded(o.key(), root, generatedAt(root, o.lastModified())));
			} catch (Exception e) {
				log(METHOD_NAME, "delay_postpone の読み込みに失敗（" + o.key() + "）: " + e.getMessage());
			}
		}
		return out;
	}

	/** 1ファイルから削除して書き戻す（削除した行の数） */
	private int removeFromFile(String bucket, String key, List<Rescheduled> targets) {
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
				log(METHOD_NAME, key + " から " + removed + "行を削除");
				return removed;
			} catch (RuntimeException e) {
				if (this.s3Operator.isPreconditionFailed(e) && attempt < MAX_RETRY) {
					continue;
				}
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
						"delay_postpone の削除に失敗（処理は続行）: " + key);
				return 0;
			} catch (Exception e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
						"delay_postpone の読み込みに失敗（処理は続行）: " + key);
				return 0;
			}
		}
		return 0;
	}

	/** JSON から対象の試合の行を削除し、件数の項目を直す（削除した行の数） */
	static int removeEntries(ObjectNode root, List<Rescheduled> targets) {
		int removed = 0;
		for (String[] f : COUNT_FIELDS) {
			JsonNode arr = root.get(f[0]);
			if (!(arr instanceof ArrayNode)) {
				continue;
			}
			ArrayNode array = (ArrayNode) arr;
			List<JsonNode> kept = new ArrayList<>();
			for (JsonNode n : array) {
				if (REMOVE_STATUSES.contains(text(n, "status_type")) && matches(n, targets)) {
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

	private static boolean matches(JsonNode n, List<Rescheduled> targets) {
		String mid = text(n, "mid");
		if (mid == null) {
			mid = extractMid(text(n, "url"));
		}
		String home = squash(text(n, "home"));
		String away = squash(text(n, "away"));
		for (Rescheduled t : targets) {
			if (mid != null && (mid.equals(t.mid) || mid.equals(t.newMid))) {
				return true;
			}
			if (!home.isEmpty() && !away.isEmpty() && home.equals(squash(t.home)) && away.equals(squash(t.away))) {
				return true;
			}
		}
		return false;
	}

	/* ---------------- 共通 ---------------- */

	/** 読み込んだ1ファイル */
	private static final class Loaded {
		final String key;
		final JsonNode root;
		final Instant generatedAt;

		Loaded(String key, JsonNode root, Instant generatedAt) {
			this.key = key;
			this.root = root;
			this.generatedAt = generatedAt;
		}
	}

	static Instant generatedAt(JsonNode root, Instant lastModified) {
		String g = text(root, "generated_at");
		if (g != null) {
			try {
				return OffsetDateTime.parse(g).toInstant();
			} catch (RuntimeException e) {
				// 最終更新日時を使う
			}
		}
		return lastModified == null ? Instant.MAX : lastModified;
	}

	static String matchKey(String mid, String home, String away) {
		if (mid != null && !mid.isBlank()) {
			return "mid:" + mid.trim();
		}
		if (home == null || away == null || home.isBlank() || away.isBlank()) {
			return null;
		}
		return "team:" + squash(home) + "\u0000" + squash(away);
	}

	static String extractMid(String link) {
		if (link == null || link.isBlank()) {
			return null;
		}
		Matcher m = MID_PATTERN.matcher(link);
		return m.find() ? m.group(1) : null;
	}

	static String squash(String s) {
		return s == null ? "" : Normalizer.normalize(s, Normalizer.Form.NFKC).replaceAll("\\s+", "");
	}

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

	static String text(JsonNode n, String field) {
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
