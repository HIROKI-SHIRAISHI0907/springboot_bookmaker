package dev.application.analyze.bm_m001;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * リアルタイムデータ（CSV）の試合時間から「中断」「延期」「中止」「再開」を検知し、
 * delay_postpone_＜日付＞(_N).json を S3 に出力する（BM_M001。OriginService から呼ぶ）。
 *
 * <h2>何を出力するか</h2>
 * <table border="1">
 *   <tr><th>今回の CSV の最新の試合時間</th><th>前回までに出力した最後の状態</th><th>出力する status_type</th></tr>
 *   <tr><td>中断・中断中</td><td>中断 以外</td><td>中断</td></tr>
 *   <tr><td>延期</td><td>延期 以外</td><td>延期（FutureStartFlgService が start_flg を 0 に戻す）</td></tr>
 *   <tr><td>中止</td><td>中止 以外</td><td>中止</td></tr>
 *   <tr><td>通常の試合時間（23' / ハーフタイム / 終了済 など）</td><td>中断・延期・中止</td><td>再開</td></tr>
 * </table>
 * <p>
 * 状態が変わったときだけ出力する（リアルタイムデータは20分ごとに来るので、同じ「中断」を毎回出さない）。
 * 「前回までに出力した最後の状態」は前日分＋当日分の delay_postpone から読む（6時間ごとのスクレイピングの出力も含む）。
 * </p>
 *
 * <h2>出力の形</h2>
 * <p>
 * スクレイピング（delay_postpone_scrape.py）と同じ項目。試合は matched_delay_postpone_games と scraped_flagged_matches に入れる
 * （ECS との照合はしないので start / busy_end は future_master の試合開始日時が分からないため入れない）。
 * source.realtime_batch = "BM_M001"、run_mode = "realtime" を付ける。
 * </p>
 *
 * <h2>ファイル名</h2>
 * <p>
 * 既存の delay_postpone_＜日付＞*.json の次の連番。スクレイピングと同時に同じ名前を使わないよう、
 * 「無ければ作成」（If-None-Match: *）で書き込み、既にあれば次の番号で再試行する。
 * </p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li>出力に失敗しても例外は投げない（ログのみ。次のリアルタイムデータで状態が同じなら、もう一度出力を試みる）。</li>
 *   <li>試合時間の表記が「中断」「延期」「中止」以外の異常（例: 放棄）は通常扱い。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Component
public class SuspensionStatusPublisher {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = SuspensionStatusPublisher.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = SuspensionStatusPublisher.class.getName();

	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** 連番の最大試行回数 */
	private static final int MAX_KEY_RETRY = 50;

	/** 「国: リーグ - ラウンドN」の「国: リーグ」 */
	private static final Pattern CATEGORY_HEAD = Pattern.compile("^\\s*([^:：]+)[:：]\\s*(.+?)(?:\\s*-\\s*ラウンド.*)?$");

	@Autowired
	private PathConfig config;

	@Autowired
	private S3Operator s3Operator;

	@Autowired
	private PostponedMatchReader postponedMatchReader;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 今回の CSV から状態の変化を検知して出力する。
	 *
	 * @param csvMap 今回登録された CSV（key = CSV、value = その試合の行）
	 * @return 出力した試合の数
	 */
	public int publish(Map<String, List<DataEntity>> csvMap) {
		final String METHOD_NAME = "publish";
		if (csvMap == null || csvMap.isEmpty()) {
			return 0;
		}
		LocalDate today = LocalDate.now(JST);
		Map<String, PostponedMatchReader.PostponedMatch> previous = this.postponedMatchReader.loadLatestStatuses(today);

		List<ObjectNode> items = new ArrayList<>();
		for (List<DataEntity> rows : csvMap.values()) {
			DataEntity latest = CsvMatchStatus.latestRow(rows);
			if (latest == null) {
				continue;
			}
			String now = CsvMatchStatus.classify(latest.getTimes());
			if (now == null) {
				continue;
			}
			String key = PostponedMatchReader.matchKey(latest.getMatchId(), latest.getHomeTeamName(), latest.getAwayTeamName());
			if (key == null) {
				continue;
			}
			PostponedMatchReader.PostponedMatch prev = previous.get(key);
			String prevStatus = prev == null ? null : prev.status;

			String out = decide(now, prevStatus);
			if (out == null) {
				continue;
			}
			items.add(item(latest, out));
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"状態の変化を検知: " + prevStatus + " → " + out + " (times=" + latest.getTimes()
							+ ", home=" + latest.getHomeTeamName() + ", away=" + latest.getAwayTeamName()
							+ ", matchId=" + latest.getMatchId() + ")");
		}
		if (items.isEmpty()) {
			return 0;
		}

		try {
			String key = put(today, payload(today, items));
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"delay_postpone を出力しました: " + key + " (" + items.size() + "試合)");
			return items.size();
		} catch (RuntimeException e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
					"delay_postpone の出力に失敗（処理は続行）");
			return 0;
		}
	}

	/**
	 * 出力する status_type（出力しない場合は null）。
	 *
	 * @param now 今回の状態（中断 / 延期 / 中止 / 通常）
	 * @param prev 前回までに出力した最後の状態（無ければ null）
	 */
	static String decide(String now, String prev) {
		if (CsvMatchStatus.NORMAL.equals(now)) {
			// 中断・延期・中止のあとに通常の試合時間 → 再開
			if (CsvMatchStatus.SUSPENDED.equals(prev) || CsvMatchStatus.POSTPONED.equals(prev)
					|| CsvMatchStatus.CANCELLED.equals(prev)) {
				return CsvMatchStatus.RESUMED;
			}
			return null;
		}
		// 中断 / 延期 / 中止: 前回と同じなら出さない
		return now.equals(prev) ? null : now;
	}

	private ObjectNode item(DataEntity e, String status) {
		String[] cl = countryLeague(e.getDataCategory());
		ObjectNode n = MAPPER.createObjectNode();
		n.put("status_type", status);
		n.put("status_text", nvl(e.getTimes()));
		n.put("category", cl == null ? nvl(e.getDataCategory()) : cl[0] + ": " + cl[1]);
		n.put("country", cl == null ? "" : cl[0]);
		n.put("league", cl == null ? "" : cl[1]);
		n.put("home", nvl(e.getHomeTeamName()));
		n.put("away", nvl(e.getAwayTeamName()));
		n.put("url", nvl(e.getGameLink()));
		n.put("mid", nvl(e.getMatchId()));
		n.put("record_time", nvl(e.getRecordTime()));
		n.put("scraped_at", OffsetDateTime.now(JST).toString());
		n.put("source_section", "realtime");
		return n;
	}

	private ObjectNode payload(LocalDate today, List<ObjectNode> items) {
		ObjectNode p = MAPPER.createObjectNode();
		p.put("generated_at", OffsetDateTime.now(JST).toString());
		p.put("timezone", "Asia/Tokyo");
		p.put("target_date", today.toString());
		p.put("run_mode", "realtime");
		ObjectNode source = p.putObject("source");
		source.put("realtime_batch", "BM_M001");
		ObjectNode filters = p.putObject("filters");
		ArrayNode kw = filters.putArray("status_keywords");
		kw.add(CsvMatchStatus.SUSPENDED).add(CsvMatchStatus.POSTPONED).add(CsvMatchStatus.CANCELLED).add(CsvMatchStatus.RESUMED);

		ArrayNode scraped = p.putArray("scraped_flagged_matches");
		ArrayNode matched = p.putArray("matched_delay_postpone_games");
		for (ObjectNode n : items) {
			scraped.add(n.deepCopy());
			matched.add(n);
		}
		p.put("scraped_flagged_count", items.size());
		p.put("matched_count", items.size());
		p.put("carry_over_postponed_count", 0);
		p.putArray("carry_over_postponed_games");
		p.put("unresolved_unmatched_count", 0);
		p.putArray("unresolved_unmatched_scraped_matches");
		return p;
	}

	/**
	 * 次の連番のキーに「無ければ作成」で書き込む（同時に同じ名前を使った場合は次の番号で再試行）。
	 *
	 * @return 書き込んだキー
	 */
	private String put(LocalDate today, ObjectNode payload) {
		String bucket = this.config.getS3BucketsOutputsDelayPostpone();
		String prefix = "delay_postpone_" + today;
		Pattern keyPattern = Pattern.compile("^" + Pattern.quote(prefix) + "(?:_(\\d+))?\\.json$");

		int maxNo = 0;
		List<S3Object> existing = this.s3Operator.listObjectsByPrefixSortedByLastModifiedAsc(bucket, prefix, keyPattern);
		for (S3Object o : existing) {
			Matcher m = keyPattern.matcher(o.key());
			if (m.matches()) {
				int no = m.group(1) == null ? 1 : Integer.parseInt(m.group(1));
				maxNo = Math.max(maxNo, no);
			}
		}

		String json;
		try {
			json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
		} catch (Exception e) {
			throw new IllegalStateException("delay_postpone の JSON 作成に失敗", e);
		}

		int no = maxNo + 1;
		for (int i = 0; i < MAX_KEY_RETRY; i++, no++) {
			String key = no == 1 ? prefix + ".json" : prefix + "_" + no + ".json";
			try {
				this.s3Operator.putTextConditional(bucket, key, json, null);
				return key;
			} catch (RuntimeException e) {
				if (this.s3Operator.isPreconditionFailed(e)) {
					continue; // 同じ名前が先に作られた → 次の番号
				}
				throw e;
			}
		}
		throw new IllegalStateException("delay_postpone の空き番号が見つかりません: " + prefix);
	}

	/** 「国: リーグ - ラウンドN」→ {国, リーグ}。読めなければ null */
	static String[] countryLeague(String category) {
		if (category == null) {
			return null;
		}
		Matcher m = CATEGORY_HEAD.matcher(category);
		if (!m.matches()) {
			return null;
		}
		return new String[] { m.group(1).trim(), m.group(2).trim() };
	}

	private static String nvl(String s) {
		return s == null ? "" : s.trim();
	}
}
