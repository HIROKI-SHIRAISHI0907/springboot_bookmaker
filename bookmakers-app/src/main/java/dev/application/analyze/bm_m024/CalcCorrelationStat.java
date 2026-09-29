package dev.application.analyze.bm_m024;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.bm_m023.AverageStatisticsSituationConst;
import dev.application.analyze.bm_m023.ScoreBasedFeature;
import dev.application.analyze.bm_m023.ScoreBasedFeatureStat;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.CountryLeagueParser;
import dev.common.util.RecordTimeConverter;

/**
 * BM_M024 相関分析ロジック
 *
 * <h2>何を導出するクラスか</h2>
 * <p>
 * 「どの特徴量が得点に結びつきやすいか」を見るため、特徴量（{@link ScoreBasedFeature}、39 項目）と
 * 得点の有無のピアソン相関係数を求める。試合終了した試合ごとに、区分（全体 ALL / 前半 1st / 後半 2nd）× 特徴量の
 * 組 (x, y) の件数・Σx・Σy・Σx²・Σy²・Σxy を calc_correlation_match_stats に保存し、
 * 相関係数はビューで多くの試合をまとめて計算する。
 * </p>
 * <ul>
 *   <li>組 (x, y): 連続する2スナップショット（区間）ごとに1組。</li>
 *   <li>x: 区間の終わり（後のスナップショット）の特徴量の値（旧実装と同じ）。</li>
 *   <li>y: その区間にその側が得点したか（ホーム特徴量はホームの得点、アウェー特徴量はアウェーの得点）。
 *       ゴール取り消し判定の行を含む区間は 0。スコアが読めない区間は組にしない。</li>
 * </ul>
 * <ul>
 *   <li>calc_correlation_stats: リーグ単位 / each_team_calc_correlation_stats: チーム単位（H / A / *合算）</li>
 *   <li>card_calc_correlation_stats: カード単位 / calc_correlation_trend・each_team_calc_correlation_trend: ラウンド推移</li>
 * </ul>
 *
 * <h2>修正履歴（旧実装からの変更）</h2>
 * <ul>
 *   <li><b>ロングパス・デュエルの相関が保存されていなかった</b>（Repository の列不足）→ 全 39 項目 × ホーム/アウェーを保存。</li>
 *   <li><b>試合途中も含めて毎回 INSERT していた</b> → 試合終了（FIN）の試合だけ、試合単位で上書き保存。</li>
 *   <li><b>1試合だけの相関は不安定</b>（1試合の得点は 0〜3 回）→ 明細の Σ を足し合わせ、リーグ・チーム単位で計算。</li>
 *   <li><b>計算できない場合も 0.0 だった</b> → ビューで NULL（相関なしと区別できる）。</li>
 *   <li>リフレクションの位置決め打ち（OUT_OFFSET = 9 / IN_START = 11）を廃止 → {@link ScoreBasedFeature} の getter。</li>
 *   <li>パス系は M023 と同じく成功率・成功数・試行数に分ける（旧実装の split3Safe と同じ意味）。</li>
 *   <li>シーズン・seq（seq_counter 採番）・ラウンド番号・記録時間に対応。</li>
 * </ul>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>x は区間の終わりの値</b>: 得点につながったシュート・期待値などを含むため、相関は高めに出る
 *       （「得点したから増えた」関係も含む）。予測に使うなら区間の始まりの値にする必要がある。</li>
 *   <li><b>値は累計</b>: 時間が経つほど大きくなるため、「試合の後半ほど値が大きい」ことも相関に含まれる。</li>
 *   <li><b>後半（2nd）の最初の区間</b>: ハーフタイム行と後半最初の行の間の区間は、どの区分にも入らない（旧実装と同じ）。</li>
 *   <li><b>シーズンは処理日基準</b>・<b>同じ組み合わせの試合がシーズン内に2試合ある場合は上書き</b>（Writer 参照）。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Component
public class CalcCorrelationStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CalcCorrelationStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CalcCorrelationStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M024_CALC_CORRELATION";

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M024";

	/** 対象の区分 */
	private static final List<String> TARGET_SEGMENTS = List.of(
			AverageStatisticsSituationConst.ALL_DATA,
			AverageStatisticsSituationConst.FIRST_DATA,
			AverageStatisticsSituationConst.SECOND_DATA);

	@Autowired
	private CalcCorrelationWriter calcCorrelationWriter;

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
		this.calcCorrelationWriter.clearSeasonCache();
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
				Integer roundNo = ScoreBasedFeatureStat.parseRound(outerEntry.getKey());

				for (Entry<String, List<BookDataEntity>> matchEntry : matchMap.entrySet()) {
					matchCount++;
					String matchKey = matchEntry.getKey();

					List<BookDataEntity> sorted = ScoreBasedFeatureStat.sortUsableRows(matchEntry.getValue());
					if (sorted.isEmpty()) {
						invalidCount++;
						continue;
					}
					BookDataEntity fin = sorted.get(sorted.size() - 1);
					if (!BookMakersCommonConst.FIN.equals(trimOrNull(fin.getTime()))) {
						// 試合途中: 終了後のデータが届いたときに処理する
						notFinishedCount++;
						continue;
					}
					String home = trimOrNull(fin.getHomeTeamName());
					String away = trimOrNull(fin.getAwayTeamName());
					Integer finHome = ScoreBasedFeatureStat.parseScore(fin.getHomeScore());
					Integer finAway = ScoreBasedFeatureStat.parseScore(fin.getAwayScore());
					if (home == null || away == null || finHome == null || finAway == null) {
						invalidCount++;
						debugLog(METHOD_NAME, BM_NUMBER + " チーム名・最終スコアが取れないためスキップ: matchKey=" + matchKey);
						continue;
					}
					String situation = (finHome == 0 && finAway == 0)
							? AverageStatisticsSituationConst.NOSCORE
							: AverageStatisticsSituationConst.SCORE;
					Timestamp recordTime = RecordTimeConverter.toTimestamp(fin.getRecordTime());

					// 区分（ALL / 1st / 2nd）。スコア別の区分は相関では使わない
					List<CalcCorrelationMatchStatsEntity> rows = new ArrayList<>();
					for (Map.Entry<String, List<BookDataEntity>> seg : ScoreBasedFeatureStat
							.buildSegments(sorted, situation).entrySet()) {
						if (!TARGET_SEGMENTS.contains(seg.getKey())) {
							continue;
						}
						for (CalcCorrelationMatchStatsEntity e : buildRows(seg.getKey(), seg.getValue(), situation)) {
							e.setMatchId(trimOrNull(fin.getMatchId()));
							e.setRoundNo(roundNo);
							e.setRecordTime(recordTime);
							rows.add(e);
						}
					}

					try {
						this.calcCorrelationWriter.saveMatch(country, league, home, away, rows);
						savedMatchCount++;
						rowCount += rows.size();
					} catch (CalcCorrelationWriter.SeasonNotResolvedException e) {
						// シーズン不明の国,リーグ: 何も保存されていないので、この試合だけスキップ
						seasonSkipCount++;
						debugLog(METHOD_NAME, BM_NUMBER + " シーズン取得不可のためスキップ: matchKey=" + matchKey
								+ " (" + e.getMessage() + ")");
					}
				}
			}
		} finally {
			this.calcCorrelationWriter.clearSeasonCache();
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

	/**
	 * 1区分の明細（特徴量ごとに1行）を作る。組が1つも無い特徴量は作らない。
	 *
	 * @param rows 区分内のスナップショット（通番順）
	 */
	static List<CalcCorrelationMatchStatsEntity> buildRows(String chkBody, List<BookDataEntity> rows, String situation) {
		List<CalcCorrelationMatchStatsEntity> list = new ArrayList<>();
		if (rows == null || rows.size() < 2) {
			return list;
		}
		// 区間ごとの得点フラグ（ホーム / アウェー。読めない区間は null）
		int pairs = rows.size() - 1;
		Integer[] yHome = new Integer[pairs];
		Integer[] yAway = new Integer[pairs];
		for (int i = 1; i < rows.size(); i++) {
			yHome[i - 1] = goalFlag(rows.get(i - 1), rows.get(i), true);
			yAway[i - 1] = goalFlag(rows.get(i - 1), rows.get(i), false);
		}

		for (ScoreBasedFeature f : ScoreBasedFeature.values()) {
			Sums home = new Sums();
			Sums away = new Sums();
			for (int i = 1; i < rows.size(); i++) {
				BookDataEntity curr = rows.get(i);
				home.add(f.value(curr, true), yHome[i - 1]);
				away.add(f.value(curr, false), yAway[i - 1]);
			}
			if (home.n == 0 && away.n == 0) {
				continue;
			}
			CalcCorrelationMatchStatsEntity e = new CalcCorrelationMatchStatsEntity();
			e.setSituation(situation);
			e.setChkBody(chkBody);
			e.setFeature(f.getFeatureName());
			e.setFeatureOrder(f.getOrder());
			e.setHomeN(home.n);
			e.setHomeSx(home.sx);
			e.setHomeSy(home.sy);
			e.setHomeSxx(home.sxx);
			e.setHomeSyy(home.syy);
			e.setHomeSxy(home.sxy);
			e.setAwayN(away.n);
			e.setAwaySx(away.sx);
			e.setAwaySy(away.sy);
			e.setAwaySxx(away.sxx);
			e.setAwaySyy(away.syy);
			e.setAwaySxy(away.sxy);
			list.add(e);
		}
		return list;
	}

	/**
	 * 区間（prev → curr）にその側が得点したか（1 / 0）。スコアが読めなければ null。
	 * ゴール取り消し判定の行を含む区間は 0（旧実装と同じ）。
	 */
	static Integer goalFlag(BookDataEntity prev, BookDataEntity curr, boolean home) {
		if (BookMakersCommonConst.GOAL_DELETE.equals(prev.getJudge())
				|| BookMakersCommonConst.GOAL_DELETE.equals(curr.getJudge())) {
			return 0;
		}
		Integer p = ScoreBasedFeatureStat.parseScore(home ? prev.getHomeScore() : prev.getAwayScore());
		Integer c = ScoreBasedFeatureStat.parseScore(home ? curr.getHomeScore() : curr.getAwayScore());
		if (p == null || c == null) {
			return null;
		}
		return (c > p) ? 1 : 0;
	}

	private static String trimOrNull(String s) {
		return (s == null || s.isBlank()) ? null : s.trim();
	}

	private void debugLog(String methodName, String message) {
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, methodName, MessageCdConst.MCD00099I_LOG, message);
	}

	/**
	 * 組 (x, y) の件数・Σx・Σy・Σx²・Σy²・Σxy（BigDecimal で正確に足す）。
	 */
	static final class Sums {
		int n;
		BigDecimal sx = BigDecimal.ZERO;
		BigDecimal sy = BigDecimal.ZERO;
		BigDecimal sxx = BigDecimal.ZERO;
		BigDecimal syy = BigDecimal.ZERO;
		BigDecimal sxy = BigDecimal.ZERO;

		void add(BigDecimal x, Integer y) {
			if (x == null || y == null) {
				return;
			}
			BigDecimal by = BigDecimal.valueOf(y);
			this.n++;
			this.sx = this.sx.add(x);
			this.sy = this.sy.add(by);
			this.sxx = this.sxx.add(x.multiply(x));
			this.syy = this.syy.add(by.multiply(by));
			this.sxy = this.sxy.add(x.multiply(by));
		}
	}
}