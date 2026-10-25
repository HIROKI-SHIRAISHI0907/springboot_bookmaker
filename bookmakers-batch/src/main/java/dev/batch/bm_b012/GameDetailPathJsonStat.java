package dev.batch.bm_b012;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.batch.repository.master.FutureMasterRepository;
import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.util.DateOffsetDecisionUtil;

/**
 * GameDetailPathJsonStat ロジック（B012 から呼ぶ）
 *
 * <p>B016 試合詳細スクレイピング（Python: MatchDetailData_aws_S3.py）の入力
 * {@code b016_game_detail_path.json} を作り、aws-s3-detail-data-csv の直下に置く（毎回上書き）。</p>
 *
 * <ul>
 *   <li>対象: future_master のうち、前日と前々日（日本時間。{@link #DAYS_BACK} 日分）に開始した試合（start_flg = '1'）で game_link があるもの。
 *       前々日も入れるのは、日付をまたいで終わった試合や、前回スクレイピングに失敗した試合を拾い直すため。
 *       スクレイパーは取得済み（_done/&lt;試合ID&gt;.json がある）試合を飛ばすので、同じ試合を何度入れても取り直しにはならない。</li>
 *   <li>試合ID（URL の ?mid=、または古い形式 /match/&lt;試合ID&gt;/）が分からない game_link は入れない（ログに出す）。</li>
 *   <li>同じ試合（mid）が future_master に複数行あっても 1 件にする（開始時刻が一番早い行）。</li>
 *   <li>対象が 0 件でも空の JSON（{}）で上書きする（前回の一覧が残らないように）。</li>
 * </ul>
 *
 * <pre>
 * {
 *   "2026-10-24": [
 *     { "url": "https://www.flashscore.co.jp/match/soccer/as-roma-zVqqL0ma/napoli-69Dxbc61/?mid=SzXZwanC",
 *       "matchKey": "SzXZwanC", "home": "ナポリ", "away": "ローマ", "category": "イタリア: セリエA - ラウンド 8",
 *       "futureTime": "2026-10-24 20:45:00" }
 *   ]
 * }
 * </pre>
 * @author shiraishitoshio
 */
@Component
public class GameDetailPathJsonStat {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = GameDetailPathJsonStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = GameDetailPathJsonStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "GAME_DETAIL_PATH_JSON";

	/** B016 の入力 JSON（バケット直下） */
	public static final String GAME_DETAIL_PATH_KEY = "b016_game_detail_path.json";

	/** 何日前まで入れるか（1 = 前日だけ、2 = 前日と前々日） */
	private static final int DAYS_BACK = 2;

	/** game_link が相対パスのときに付けるサイト */
	private static final String SITE = "https://www.flashscore.co.jp";

	/** URL の mid= */
	private static final Pattern MID_PATTERN = Pattern.compile("[?&]mid=([A-Za-z0-9]+)");

	/** 古い形式の URL: /match/&lt;試合ID 8 文字&gt;/ */
	private static final Pattern LEGACY_MID_PATTERN = Pattern.compile("/match/([A-Za-z0-9]{8})/?(?:[?#].*)?$");

	@Autowired
	private FutureMasterRepository futureMasterRepository;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private PathConfig pathConfig;
	@Autowired
	private S3Operator s3Operator;
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * b016_game_detail_path.json を作って S3 に置く。
	 * @throws Exception DB・S3 のエラー
	 */
	public void execute() throws Exception {
		final String METHOD_NAME = "execute";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		try {
			run(METHOD_NAME);
		} finally {
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	private void run(String METHOD_NAME) throws Exception {
		// 前日（日本時間）の範囲を UTC の ISO 文字列で受け取り、始まりを DAYS_BACK - 1 日さかのぼる
		String[] previousDayRange = DateOffsetDecisionUtil.previousDayRangeAsUtcIsoStrings();
		String rangeStart = OffsetDateTime.parse(previousDayRange[0]).minusDays(DAYS_BACK - 1)
				.withOffsetSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		String rangeEnd = previousDayRange[1];
		info(METHOD_NAME, "検索期間: " + rangeStart + "~" + rangeEnd + "(日本時間換算: "
				+ DateOffsetDecisionUtil.toIsoJstString(rangeStart) + "~" + DateOffsetDecisionUtil.toIsoJstString(rangeEnd) + ")");

		List<GameDetailPathRow> rows = futureMasterRepository.findGameDetailPathTargets(rangeStart, rangeEnd);
		info(METHOD_NAME, "期間内に開始した試合（game_link あり）: " + rows.size() + " 行");

		// mid ごとに 1 件（rows は開始時刻の昇順なので最初の行）
		Map<String, GameDetailPathRow> byMid = new LinkedHashMap<>();
		Map<String, String> urlByMid = new LinkedHashMap<>();
		int noMid = 0;
		for (GameDetailPathRow row : rows) {
			String url = normalizeUrl(row.getGameLink());
			String mid = extractMid(url);
			if (mid == null) {
				noMid++;
				info(METHOD_NAME, "試合ID が分からないため入れません: " + row.getHomeTeamName() + " vs "
						+ row.getAwayTeamName() + " url=" + row.getGameLink());
				continue;
			}
			if (!byMid.containsKey(mid)) {
				byMid.put(mid, row);
				urlByMid.put(mid, url);
			}
		}

		// 日付キー（昇順）→ [{url, matchKey, home, away, category, futureTime}, ...]
		Map<String, List<Map<String, Object>>> out = new TreeMap<>();
		for (Map.Entry<String, GameDetailPathRow> e : byMid.entrySet()) {
			GameDetailPathRow row = e.getValue();
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("url", urlByMid.get(e.getKey()));
			item.put("matchKey", e.getKey());
			item.put("home", nvl(row.getHomeTeamName()));
			item.put("away", nvl(row.getAwayTeamName()));
			item.put("category", nvl(row.getGameTeamCategory()));
			item.put("futureTime", nvl(row.getFutureTime()));
			out.computeIfAbsent(nvl(row.getMatchDate()), k -> new ArrayList<>()).add(item);
		}

		String bucket = pathConfig.getS3BucketsDetailData();
		String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);
		try {
			s3Operator.putJson(bucket, GAME_DETAIL_PATH_KEY, json);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
					GAME_DETAIL_PATH_KEY + " のアップロードに失敗しました（bucket=" + bucket
							+ "。403 の場合はタスクロールに s3:PutObject の権限があるか確認）");
			throw e;
		}
		info(METHOD_NAME, "アップロード完了: s3://" + bucket + "/" + GAME_DETAIL_PATH_KEY
				+ " 試合数=" + byMid.size() + " 日付=" + out.keySet() + " 試合ID不明で除外=" + noMid);
	}

	private void info(String method, String message) {
		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, method, MessageCdConst.MCD00099I_LOG, message);
	}

	/** 相対パス（/match/...）ならサイトを付ける。前後の空白は除く */
	static String normalizeUrl(String link) {
		if (link == null) {
			return null;
		}
		String u = link.trim();
		if (u.startsWith("//")) {
			return "https:" + u;
		}
		if (u.startsWith("/")) {
			return SITE + u;
		}
		return u;
	}

	/** 試合ID: ?mid= を優先、無ければ古い形式 /match/&lt;試合ID&gt;/。どちらも無ければ null */
	static String extractMid(String url) {
		if (url == null || url.isEmpty()) {
			return null;
		}
		Matcher m = MID_PATTERN.matcher(url);
		if (m.find()) {
			return m.group(1);
		}
		Matcher legacy = LEGACY_MID_PATTERN.matcher(url);
		return legacy.find() ? legacy.group(1) : null;
	}

	private static String nvl(String s) {
		return s == null ? "" : s;
	}
}
