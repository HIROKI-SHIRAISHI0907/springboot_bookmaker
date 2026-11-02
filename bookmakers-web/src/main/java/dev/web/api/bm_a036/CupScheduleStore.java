package dev.web.api.bm_a036;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.s3.S3Operator;
import lombok.extern.slf4j.Slf4j;

/**
 * 大会の日程まわりの S3 の JSON（バケット: aws-s3-cup-schedule-csv）
 * <ul>
 *   <li>b026_cup_competition.json   : 大会と出場チーム（web が登録・変更のたびに出力）。日程スクレイパーの入力。</li>
 *   <li>b026_cup_schedule.json      : 大会の日程（日程スクレイパー cup-schedule-data が出力）。</li>
 *   <li>b026_cup_detail_request.json: 出場選手の取得を依頼した試合（Lambda cup-schedule-runner が出力）。
 *       {"試合ID": "依頼した日時（JST）" または "RETRY"}。web の「取り直す」は値を RETRY にする。</li>
 * </ul>
 * 日程は 60 秒だけ覚えておく（試合予定・試合中の画面から何度も呼ばれるため）。
 * @author shiraishitoshio
 */
@Slf4j
@Component
public class CupScheduleStore {

	public static final String COMPETITION_KEY = "b026_cup_competition.json";
	public static final String SCHEDULE_KEY = "b026_cup_schedule.json";
	/** 結果タブのスクレイパー（CupResultData_aws_S3.py）が読む大会URLの一覧（大会一括アップロード画面のボタンで出力。2026-11-01 追加） */
	public static final String URL_KEY = "json/b026_cup_url.json";
	/** 結果タブのスクレイパーが出力する結果（日程と同じ形。getSchedule で日程と合わせて読む。2026-11-01 追加） */
	public static final String RESULT_KEY = "json/b026_cup_result.json";
	public static final String REQUEST_KEY = "b026_cup_detail_request.json";
	/** 取り直しの印（Lambda は期間を過ぎていても依頼する） */
	public static final String RETRY = "RETRY";

	public static final ZoneId JST = ZoneId.of("Asia/Tokyo");
	public static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static final long CACHE_MILLIS = 60_000L;

	/** /team/<名前>/<チームID>/ */
	private static final java.util.regex.Pattern TEAM_LINK = java.util.regex.Pattern.compile("/team/[^/?#]+/([A-Za-z0-9]+)");

	private final S3Operator s3Operator;
	private final ObjectMapper objectMapper;
	private final String bucket;
	/** 試合日の翌日の何時から取るか（Lambda の DETAIL_HOUR_JST と合わせる） */
	private final int detailHourJst;
	/** 試合日の翌日から何日のあいだ依頼するか（Lambda の LOOKBACK_DAYS と合わせる） */
	private final int lookbackDays;

	private volatile List<ScheduleItem> cache;
	private volatile String cacheGeneratedAt;
	private volatile long cacheAt;

	public CupScheduleStore(S3Operator s3Operator, ObjectMapper objectMapper,
			@Value("${process.s3.buckets.cupSchedule:aws-s3-cup-schedule-csv}") String bucket,
			@Value("${process.cup.detailHourJst:6}") int detailHourJst,
			@Value("${process.cup.lookbackDays:3}") int lookbackDays) {
		this.s3Operator = s3Operator;
		this.objectMapper = objectMapper;
		this.bucket = bucket;
		this.detailHourJst = detailHourJst;
		this.lookbackDays = lookbackDays;
	}

	public String getBucket() {
		return bucket;
	}

	/** 大会と出場チームの JSON を出力する */
	public void putCompetitionJson(Object body) throws Exception {
		String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body);
		s3Operator.putJson(bucket, COMPETITION_KEY, json);
		log.info("[A036] 大会と出場チームを出力しました: s3://{}/{}", bucket, COMPETITION_KEY);
	}

	/** 大会URLの一覧の JSON を出力する（json/b026_cup_url.json） */
	public void putUrlJson(Object body) throws Exception {
		String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body);
		s3Operator.putJson(bucket, URL_KEY, json);
		log.info("[A036] 大会URLの一覧を出力しました: s3://{}/{}", bucket, URL_KEY);
	}

	/** 日程（無ければ空）。60 秒キャッシュ */
	public List<ScheduleItem> getSchedule() {
		long now = System.currentTimeMillis();
		List<ScheduleItem> c = cache;
		if (c != null && now - cacheAt < CACHE_MILLIS) {
			return c;
		}
		// 日程（b026_cup_schedule.json）と結果（json/b026_cup_result.json。結果タブのスクレイパーが出力）を合わせる。
		// 同じ試合ID は後から読んだ結果の方を使う。
		Map<String, ScheduleItem> byMid = new LinkedHashMap<>();
		String generatedAt = null;
		try {
			for (String key : List.of(SCHEDULE_KEY, RESULT_KEY)) {
				if (!s3Operator.existsOnS3(bucket, key)) {
					continue;
				}
				JsonNode root = objectMapper.readTree(s3Operator.downloadTextUtf8(bucket, key));
				String g = text(root, "generatedAt");
				if (generatedAt == null || g.compareTo(generatedAt) > 0) {
					generatedAt = g;
				}
				for (JsonNode m : root.path("matches")) {
					ScheduleItem it = new ScheduleItem();
					it.mid = text(m, "mid");
					if (it.mid.isEmpty()) {
						continue;
					}
					it.url = text(m, "url");
					it.date = text(m, "date");
					it.kickoff = text(m, "kickoff");
					it.competitionId = m.path("competitionId").isNumber() ? m.path("competitionId").asInt() : null;
					it.competitionName = text(m, "competitionName");
					it.region = text(m, "region");
					it.round = text(m, "round");
					it.home = text(m, "home");
					it.away = text(m, "away");
					it.homeTeamId = text(m, "homeTeamId");
					it.awayTeamId = text(m, "awayTeamId");
					for (JsonNode t : m.path("targets")) {
						ScheduleTarget st = new ScheduleTarget();
						st.country = text(t, "country");
						st.league = text(t, "league");
						st.team = text(t, "team");
						st.teamId = text(t, "teamId");
						st.side = text(t, "side");
						it.targets.add(st);
					}
					byMid.put(it.mid, it);
				}
			}
		} catch (Exception e) {
			log.warn("[A036] 日程・結果を読めませんでした: s3://{}/{} , {} ({})", bucket, SCHEDULE_KEY, RESULT_KEY, e.toString());
			if (c != null) {
				return c; // 読めないときは前回の値
			}
		}
		List<ScheduleItem> items = new ArrayList<>(byMid.values());
		items.sort((a, b) -> a.kickoff.compareTo(b.kickoff));
		cache = Collections.unmodifiableList(items);
		cacheGeneratedAt = generatedAt;
		cacheAt = now;
		return cache;
	}

	/** 日程を取った日時（getSchedule の後に呼ぶ） */
	public String getScheduleGeneratedAt() {
		return cacheGeneratedAt;
	}

	public boolean scheduleExists() {
		try {
			return s3Operator.existsOnS3(bucket, SCHEDULE_KEY) || s3Operator.existsOnS3(bucket, RESULT_KEY);
		} catch (Exception e) {
			return false;
		}
	}

	/** 取得を依頼した試合 {試合ID: 依頼日時 or RETRY}（無ければ空） */
	public Map<String, String> getRequests() {
		try {
			Optional<S3Operator.TextWithETag> t = s3Operator.downloadTextUtf8WithETag(bucket, REQUEST_KEY);
			if (t.isPresent() && t.get().getText() != null && !t.get().getText().isBlank()) {
				return objectMapper.readValue(t.get().getText(), new TypeReference<LinkedHashMap<String, String>>() {
				});
			}
		} catch (Exception e) {
			log.warn("[A036] 依頼済みの一覧を読めませんでした: s3://{}/{} ({})", bucket, REQUEST_KEY, e.toString());
		}
		return new LinkedHashMap<>();
	}

	/**
	 * 試合を「取り直す」にする（依頼済みの一覧の値を RETRY にする）。
	 * Lambda と同時に書いたときに消さないよう、ETag 付きで書く（ぶつかったら読み直して 3 回まで）。
	 * @return 取り直しにした件数
	 */
	public int markRetry(List<String> mids) throws Exception {
		if (mids == null || mids.isEmpty()) {
			return 0;
		}
		for (int i = 0; i < 3; i++) {
			Optional<S3Operator.TextWithETag> t = s3Operator.downloadTextUtf8WithETag(bucket, REQUEST_KEY);
			Map<String, String> map = new LinkedHashMap<>();
			String etag = null;
			if (t.isPresent()) {
				etag = t.get().getETag();
				String text = t.get().getText();
				if (text != null && !text.isBlank()) {
					map = objectMapper.readValue(text, new TypeReference<LinkedHashMap<String, String>>() {
					});
				}
			}
			int n = 0;
			for (String mid : mids) {
				if (mid != null && !mid.isBlank()) {
					map.put(mid.trim(), RETRY);
					n++;
				}
			}
			try {
				s3Operator.putTextConditional(bucket, REQUEST_KEY,
						objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map), etag);
				return n;
			} catch (Exception e) {
				if (!s3Operator.isPreconditionFailed(e) || i == 2) {
					throw e;
				}
			}
		}
		return 0;
	}

	/**
	 * 試合の状態（Lambda と同じ判定）
	 * @param registered 出場選手を登録済み（match_detail_match にある）
	 * @param requested 依頼済みの一覧の値（無ければ null）
	 */
	public String statusOf(ScheduleItem it, boolean registered, String requested, LocalDateTime nowJst) {
		if (registered) {
			return "REGISTERED";
		}
		if (requested != null) {
			return RETRY.equals(requested) ? "WAITING" : "REQUESTED";
		}
		LocalDate d = it.matchDate();
		if (d == null) {
			return "SCHEDULED";
		}
		LocalDateTime from = d.plusDays(1).atTime(detailHourJst, 0);
		LocalDateTime to = d.plusDays(1L + lookbackDays).atTime(23, 59, 59);
		if (nowJst.isBefore(from)) {
			return "SCHEDULED";
		}
		return nowJst.isAfter(to) ? "EXPIRED" : "WAITING";
	}

	/** チームのリンク（/team/vissel-kobe/xxxxxxxx/）からチームID（xxxxxxxx）を取り出す。取れなければ空文字 */
	public static String teamIdOfLink(String link) {
		if (link == null) {
			return "";
		}
		java.util.regex.Matcher m = TEAM_LINK.matcher(link);
		return m.find() ? m.group(1) : "";
	}

	/** キャッシュを捨てる */
	public void invalidate() {
		cache = null;
	}

	private static String text(JsonNode n, String field) {
		JsonNode v = n.path(field);
		return v.isMissingNode() || v.isNull() ? "" : v.asText("").trim();
	}

	/** 日程の 1 試合 */
	public static class ScheduleItem {
		public String mid = "";
		public String url = "";
		/** yyyy-MM-dd（JST） */
		public String date = "";
		/** yyyy-MM-dd HH:mm（JST） */
		public String kickoff = "";
		public Integer competitionId;
		public String competitionName = "";
		public String region = "";
		public String round = "";
		public String home = "";
		public String away = "";
		public String homeTeamId = "";
		public String awayTeamId = "";
		public List<ScheduleTarget> targets = new ArrayList<>();

		public LocalDate matchDate() {
			try {
				return LocalDate.parse(date.length() >= 10 ? date.substring(0, 10) : date);
			} catch (Exception e) {
				return null;
			}
		}

		public LocalDateTime kickoffTime() {
			try {
				String k = kickoff.replace('T', ' ');
				if (k.length() >= 16) {
					return LocalDateTime.parse(k.substring(0, 16), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
				}
			} catch (Exception e) {
				// 下へ
			}
			LocalDate d = matchDate();
			return d == null ? null : d.atStartOfDay();
		}
	}

	/** 試合に出る、登録した出場チーム */
	public static class ScheduleTarget {
		public String country = "";
		public String league = "";
		public String team = "";
		public String teamId = "";
		/** HOME / AWAY */
		public String side = "";
	}
}
