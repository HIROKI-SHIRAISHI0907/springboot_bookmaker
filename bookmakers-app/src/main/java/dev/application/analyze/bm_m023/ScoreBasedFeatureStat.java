package dev.application.analyze.bm_m023;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.CountryLeagueParser;
import dev.common.util.ExecuteMainUtil;
import dev.common.util.RecordTimeConverter;

/**
 * BM_M023 / BM_M026 統計分析ロジック（得点状況別の特徴量統計）
 *
 * <h2>何を導出するクラスか</h2>
 * <p>
 * 試合終了した試合ごとに、区分（全体 ALL / 前半 1st / 後半 2nd / スコア別 "1-0" など）× 特徴量（{@link ScoreBasedFeature}）の
 * 観測値の件数・Σx・Σx²・Σx³・Σx⁴・最小・最大（ホーム・アウェー別）と、値があった時点の試合時間の統計を
 * score_based_feature_match_stats に保存する。
 * </p>
 * <p>
 * 平均・標準偏差・歪度・尖度・最小・最大は、この明細からビューで計算する（Σ は足し合わせられるので、どの単位でも正確）。
 * </p>
 * <ul>
 *   <li>score_based_feature_stats: リーグ単位（旧 BM_M023）</li>
 *   <li>each_team_score_based_feature_stats: チーム単位（旧 BM_M026。ha でホーム戦・アウェー戦）</li>
 *   <li>card_score_based_feature_stats: カード単位（チーム × 対戦相手）</li>
 * </ul>
 * <p>
 * 画面では、これ以外の単位（直近 N 試合、期間指定など）も明細に WHERE を付けて集計できる。
 * 明細にラウンド番号（キーの「ラウンド N」）と試合終了行の記録時間を持つため、
 * 「ラウンド N 終了時点」の統計や推移（ビュー *_trend）も出せる。旧 BM_M023H / BM_M026H（履歴コピー）はこれで置き換える。
 * </p>
 *
 * <h3>区分の決め方（旧実装と同じ）</h3>
 * <ul>
 *   <li>状況: 試合終了時のスコアが 0-0 なら「得点なし」、それ以外は「得点あり」。</li>
 *   <li>ALL: 試合の全スナップショット。</li>
 *   <li>1st / 2nd: 最初のハーフタイム行の通番以前 / より後（ハーフタイム行が無い試合は作らない）。</li>
 *   <li>スコア別: 「得点あり」の試合だけ。その試合に出てきたスコア（0-0 以外）ごとに、そのスコアだった間のスナップショット。</li>
 * </ul>
 *
 * <h2>修正履歴（旧 BM_M023 / BM_M026 からの変更）</h2>
 * <ul>
 *   <li><b>二重集計の解消</b>: 既存統計値に今回分を足し込む方式をやめ、試合ごとの明細を上書き保存する方式にした。
 *       同じ試合が何度流れてきても値は増えない。</li>
 *   <li><b>歪度・尖度の集計範囲の不一致を解消</b>: 旧実装はリーグ単位の行に「今のカードの履歴だけ」の歪度・尖度を入れていた。
 *       今はすべての単位で同じ明細から計算する。stat_encryption の全件復号も不要になった（M023/M026 からは書き込まない）。</li>
 *   <li><b>時間の統計が 58 項目すべて同じ値だった</b> → その特徴量に値があった行の時間だけで集計。
 *       読めない試合時間は 0 分扱いにせず除外。</li>
 *   <li><b>国・リーグをカンマで分けていたため全件スキップになっていた</b> → CountryLeagueParser。</li>
 *   <li><b>getDeclaredFields の並び順・FEATURE_START = 11 などの位置決め打ちを廃止</b> → {@link ScoreBasedFeature} の getter。</li>
 *   <li>パス系は成功率・成功数・試行数に分けて集計（旧 M023 は成功数のみ、旧 M026 は対象外で不一致だった）。</li>
 *   <li>シーズン・seq（seq_counter 採番）に対応。1試合ごとに数十行出していた log.info を件数ログのみに。</li>
 *   <li>BM_M026（EachTeamScoreBasedFeatureStat）はこのクラスに統合（チーム単位はビュー）。</li>
 *   <li>BM_M023H / BM_M026H（統計の履歴コピー）を廃止。ラウンド番号・記録時間を明細に持ち、推移はビューで出す。</li>
 * </ul>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>観測はスナップショット単位</b>: 値はその時点までの累計で、データ取得間隔が細かい試合ほど観測数が多く、重みが大きい（旧実装と同じ）。</li>
 *   <li><b>データ量</b>: 1試合 約200行。30リーグで年 約250万行。ビューが重くなったらリーグ単位をマテリアライズドビューにする。</li>
 *   <li><b>シーズンは処理日基準</b>・<b>同じ組み合わせの試合がシーズン内に2試合ある場合は上書き</b>（Writer 参照）。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Component
public class ScoreBasedFeatureStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ScoreBasedFeatureStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ScoreBasedFeatureStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M023_SCORE_BASED_FEATURE";

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M023";

	/** キーのラウンド番号（"… - ラウンド 5" の 5） */
	private static final Pattern ROUND_PATTERN = Pattern.compile("ラウンド\\s*(\\d+)");

	@Autowired
	private ScoreBasedFeatureWriter scoreBasedFeatureWriter;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		int matchCount = 0;
		int savedMatchCount = 0;
		int notFinishedCount = 0;
		int invalidCount = 0;
		int seasonSkipCount = 0;
		long rowCount = 0;

		// シーズンのキャッシュは Writer 側（スレッド単位）。前回の残りを使わないよう開始時にも破棄する
		this.scoreBasedFeatureWriter.clearSeasonCache();
		try {
			if (entities == null || entities.isEmpty()) {
				debugLog(METHOD_NAME, BM_NUMBER + " 入力データなし");
				return;
			}

			for (Entry<String, Map<String, List<BookDataEntity>>> outerEntry : entities.entrySet()) {
				Map<String, List<BookDataEntity>> matchMap = outerEntry.getValue();
				if (matchMap == null || matchMap.isEmpty()) {
					continue;
				}
				// 「国: リーグ - ラウンドN」形式でないキーは無視
				String[] cl = CountryLeagueParser.parse(outerEntry.getKey());
				if (cl == null) {
					invalidCount += matchMap.size();
					debugLog(METHOD_NAME, BM_NUMBER + " 対象外のキーのためスキップ: " + outerEntry.getKey());
					continue;
				}
				String country = cl[0];
				String league = cl[1];
				Integer roundNo = parseRound(outerEntry.getKey());

				for (Entry<String, List<BookDataEntity>> matchEntry : matchMap.entrySet()) {
					matchCount++;
					String matchKey = matchEntry.getKey();

					List<BookDataEntity> sorted = sortUsableRows(matchEntry.getValue());
					if (sorted.isEmpty()) {
						invalidCount++;
						continue;
					}
					BookDataEntity fin = findLastFin(sorted);
					if (fin == null) {
						// 試合途中: 終了後のデータが届いたときに処理する
						notFinishedCount++;
						continue;
					}
					String home = trimOrNull(fin.getHomeTeamName());
					String away = trimOrNull(fin.getAwayTeamName());
					Integer finHome = parseScore(fin.getHomeScore());
					Integer finAway = parseScore(fin.getAwayScore());
					if (home == null || away == null || finHome == null || finAway == null) {
						invalidCount++;
						debugLog(METHOD_NAME, BM_NUMBER + " チーム名・最終スコアが取れないためスキップ: matchKey=" + matchKey);
						continue;
					}

					String situation = (finHome == 0 && finAway == 0)
							? AverageStatisticsSituationConst.NOSCORE
							: AverageStatisticsSituationConst.SCORE;

					Timestamp recordTime = RecordTimeConverter.toTimestamp(fin.getRecordTime());
					List<ScoreBasedFeatureMatchStatsEntity> rows = new ArrayList<>();
					for (Map.Entry<String, List<BookDataEntity>> seg : buildSegments(sorted, situation).entrySet()) {
						for (ScoreBasedFeatureMatchStatsEntity e : buildRows(seg.getKey(), seg.getValue(), situation,
								trimOrNull(fin.getMatchId()))) {
							e.setRoundNo(roundNo);
							e.setRecordTime(recordTime);
							rows.add(e);
						}
					}

					try {
						this.scoreBasedFeatureWriter.saveMatch(country, league, home, away, rows);
						savedMatchCount++;
						rowCount += rows.size();
					} catch (ScoreBasedFeatureWriter.SeasonNotResolvedException e) {
						// シーズン不明の国,リーグ: 何も保存されていないので、この試合だけスキップ
						seasonSkipCount++;
						debugLog(METHOD_NAME, BM_NUMBER + " シーズン取得不可のためスキップ: matchKey=" + matchKey
								+ " (" + e.getMessage() + ")");
					}
				}
			}
		} finally {
			this.scoreBasedFeatureWriter.clearSeasonCache();
			debugLog(METHOD_NAME, BM_NUMBER + " matchCount=" + matchCount
					+ ", savedMatchCount=" + savedMatchCount
					+ ", rowCount=" + rowCount
					+ ", notFinishedCount=" + notFinishedCount
					+ ", invalidCount=" + invalidCount
					+ ", seasonSkipCount=" + seasonSkipCount);
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	// ===== 区分 =====

	/**
	 * 区分（ALL / 1st / 2nd / スコア別）ごとの行を作る（登場順）。BM_M024 も使う。
	 */
	public static Map<String, List<BookDataEntity>> buildSegments(List<BookDataEntity> sorted, String situation) {
		Map<String, List<BookDataEntity>> segs = new LinkedHashMap<>();
		segs.put(AverageStatisticsSituationConst.ALL_DATA, sorted);

		// 前半・後半（最初のハーフタイム行で分ける）
		Long htSeq = null;
		for (BookDataEntity e : sorted) {
			String t = trimOrNull(e.getTime());
			if (BookMakersCommonConst.HALF_TIME.equals(t) || BookMakersCommonConst.FIRST_HALF_TIME.equals(t)) {
				htSeq = seqToLong(e.getSeq());
				break;
			}
		}
		if (htSeq != null) {
			List<BookDataEntity> first = new ArrayList<>();
			List<BookDataEntity> second = new ArrayList<>();
			for (BookDataEntity e : sorted) {
				if (seqToLong(e.getSeq()) <= htSeq) {
					first.add(e);
				} else {
					second.add(e);
				}
			}
			if (!first.isEmpty()) {
				segs.put(AverageStatisticsSituationConst.FIRST_DATA, first);
			}
			if (!second.isEmpty()) {
				segs.put(AverageStatisticsSituationConst.SECOND_DATA, second);
			}
		}

		// スコア別（得点ありの試合だけ。0-0 は除く）
		if (AverageStatisticsSituationConst.SCORE.equals(situation)) {
			for (BookDataEntity e : sorted) {
				Integer h = parseScore(e.getHomeScore());
				Integer a = parseScore(e.getAwayScore());
				if (h == null || a == null || (h == 0 && a == 0)) {
					continue;
				}
				segs.computeIfAbsent(h + "-" + a, k -> new ArrayList<>()).add(e);
			}
		}
		return segs;
	}

	/**
	 * 1区分の明細（特徴量ごとに1行）を作る。ホーム・アウェーとも値が無い特徴量は作らない。
	 */
	static List<ScoreBasedFeatureMatchStatsEntity> buildRows(String chkBody, List<BookDataEntity> rows,
			String situation, String matchId) {
		// 行ごとの試合時間（分）は特徴量に関係なく同じなので先に求める
		BigDecimal[] minutes = new BigDecimal[rows.size()];
		for (int i = 0; i < rows.size(); i++) {
			minutes[i] = toMinutes(trimOrNull(rows.get(i).getTime()));
		}

		List<ScoreBasedFeatureMatchStatsEntity> list = new ArrayList<>();
		for (ScoreBasedFeature f : ScoreBasedFeature.values()) {
			Moments home = new Moments();
			Moments away = new Moments();
			for (int i = 0; i < rows.size(); i++) {
				BookDataEntity row = rows.get(i);
				home.add(f.value(row, true), minutes[i]);
				away.add(f.value(row, false), minutes[i]);
			}
			if (home.n == 0 && away.n == 0) {
				continue;
			}
			ScoreBasedFeatureMatchStatsEntity e = new ScoreBasedFeatureMatchStatsEntity();
			e.setMatchId(matchId);
			e.setSituation(situation);
			e.setChkBody(chkBody);
			e.setFeature(f.getFeatureName());
			e.setFeatureOrder(f.getOrder());
			e.setHomeN(home.n);
			e.setHomeS1(home.s1);
			e.setHomeS2(home.s2);
			e.setHomeS3(home.s3);
			e.setHomeS4(home.s4);
			e.setHomeMin(home.min);
			e.setHomeMax(home.max);
			e.setHomeTn(home.tn);
			e.setHomeTs1(home.ts1);
			e.setHomeTs2(home.ts2);
			e.setHomeTmin(home.tmin);
			e.setHomeTmax(home.tmax);
			e.setAwayN(away.n);
			e.setAwayS1(away.s1);
			e.setAwayS2(away.s2);
			e.setAwayS3(away.s3);
			e.setAwayS4(away.s4);
			e.setAwayMin(away.min);
			e.setAwayMax(away.max);
			e.setAwayTn(away.tn);
			e.setAwayTs1(away.ts1);
			e.setAwayTs2(away.ts2);
			e.setAwayTmin(away.tmin);
			e.setAwayTmax(away.tmax);
			list.add(e);
		}
		return list;
	}

	// ===== 共通 =====

	/** キーからラウンド番号を取り出す（無ければ null） */
	public static Integer parseRound(String key) {
		if (key == null) {
			return null;
		}
		Matcher m = ROUND_PATTERN.matcher(java.text.Normalizer.normalize(key, java.text.Normalizer.Form.NFKC));
		if (!m.find()) {
			return null;
		}
		try {
			return Integer.parseInt(m.group(1));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * 使える行だけを通番の数値順に並べる（null 行・取得エラー行・PK 戦の行は除く）。
	 */
	public static List<BookDataEntity> sortUsableRows(List<BookDataEntity> rows) {
		List<BookDataEntity> sorted = new ArrayList<>();
		if (rows == null) {
			return sorted;
		}
		for (BookDataEntity e : rows) {
			if (e == null) {
				continue;
			}
			if (BookMakersCommonConst.GET_UNEXPECTED_ERROR.equals(e.getGoalTime())
					|| BookMakersCommonConst.GET_UNEXPECTED_ERROR.equals(e.getGoalTeamMember())) {
				continue;
			}
			String t = e.getTime();
			if (t != null && t.contains(BookMakersCommonConst.PENALTY)) {
				continue;
			}
			sorted.add(e);
		}
		sorted.sort(Comparator.comparingLong(e -> seqToLong(e.getSeq())));
		return sorted;
	}

	/** 最後の試合終了（FIN）行（最後の行が FIN でなければ null＝試合途中） */
	private static BookDataEntity findLastFin(List<BookDataEntity> sorted) {
		BookDataEntity last = sorted.get(sorted.size() - 1);
		return BookMakersCommonConst.FIN.equals(trimOrNull(last.getTime())) ? last : null;
	}

	/**
	 * 試合時間を分に変換する（読めなければ null）。
	 * ExecuteMainUtil.convertToMinutes は読めない形式（"中断"、"'" の無い "23" など）を 0 分にしてしまうため、
	 * 読める形式（FIN・ハーフタイム・"mm:ss"・"45+2'"・"23'"）だけ渡す。
	 */
	static BigDecimal toMinutes(String time) {
		if (time == null) {
			return null;
		}
		boolean readable = BookMakersCommonConst.FIN.equals(time)
				|| BookMakersCommonConst.HALF_TIME.equals(time) || BookMakersCommonConst.FIRST_HALF_TIME.equals(time)
				|| time.contains(":") || time.contains("+") || time.endsWith("'");
		if (!readable) {
			return null;
		}
		try {
			double d = ExecuteMainUtil.convertToMinutes(time);
			return Double.isFinite(d) ? BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP) : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** スコアを整数に変換（空・数値以外・負は null） */
	public static Integer parseScore(String value) {
		String s = trimOrNull(value);
		if (s == null) {
			return null;
		}
		s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC);
		if (!s.matches("\\d+")) {
			return null;
		}
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static long seqToLong(String seq) {
		if (seq == null || seq.isBlank()) {
			return Long.MAX_VALUE;
		}
		try {
			return Long.parseLong(seq.trim());
		} catch (NumberFormatException e) {
			return Long.MAX_VALUE;
		}
	}

	private static String trimOrNull(String s) {
		return (s == null || s.isBlank()) ? null : s.trim();
	}

	private void debugLog(String methodName, String message) {
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, methodName, MessageCdConst.MCD00099I_LOG, message);
	}

	/**
	 * 件数・Σx〜Σx⁴・最小・最大と、時間の件数・Σ・Σ²・最小・最大（BigDecimal で正確に足す）。
	 */
	static final class Moments {
		int n;
		BigDecimal s1 = BigDecimal.ZERO;
		BigDecimal s2 = BigDecimal.ZERO;
		BigDecimal s3 = BigDecimal.ZERO;
		BigDecimal s4 = BigDecimal.ZERO;
		BigDecimal min;
		BigDecimal max;
		int tn;
		BigDecimal ts1 = BigDecimal.ZERO;
		BigDecimal ts2 = BigDecimal.ZERO;
		BigDecimal tmin;
		BigDecimal tmax;

		void add(BigDecimal x, BigDecimal minute) {
			if (x == null) {
				return;
			}
			BigDecimal x2 = x.multiply(x);
			this.n++;
			this.s1 = this.s1.add(x);
			this.s2 = this.s2.add(x2);
			this.s3 = this.s3.add(x2.multiply(x));
			this.s4 = this.s4.add(x2.multiply(x2));
			this.min = (this.min == null || x.compareTo(this.min) < 0) ? x : this.min;
			this.max = (this.max == null || x.compareTo(this.max) > 0) ? x : this.max;
			if (minute != null) {
				this.tn++;
				this.ts1 = this.ts1.add(minute);
				this.ts2 = this.ts2.add(minute.multiply(minute));
				this.tmin = (this.tmin == null || minute.compareTo(this.tmin) < 0) ? minute : this.tmin;
				this.tmax = (this.tmax == null || minute.compareTo(this.tmax) > 0) ? minute : this.tmax;
			}
		}
	}
}