package dev.web.api.ranking.bm_w001;

import java.util.List;
import java.util.function.Function;

/**
 * ランキングに使えるデータ（2026-11-28 追加）。
 * <ul>
 *   <li>RESULT: 試合の結果から（統計が無いリーグでも、終了済の試合から出せる）</li>
 *   <li>STAT: 試合のスタッツから（統計（B006）ができたリーグだけ）</li>
 * </ul>
 * 値は「そのラウンドまでの累計（または平均・率）」。higherBetter = true なら値が大きいほど上位。
 * @author shiraishitoshio
 */
public enum RankingMetric {

	// ===================== 結果 =====================
	POINTS(Type.RESULT, "勝ち点", "点", true, 0, rows -> sum(rows, r -> r.getPoints())),
	POINTS_PER_GAME(Type.RESULT, "1試合平均の勝ち点", "点", true, 2, rows -> avg(rows, r -> r.getPoints())),
	WINS(Type.RESULT, "勝利数", "勝", true, 0, rows -> count(rows, r -> "W".equals(r.getResult()))),
	WIN_RATE(Type.RESULT, "勝率", "%", true, 1, rows -> rate(rows, r -> "W".equals(r.getResult()))),
	UNBEATEN_RATE(Type.RESULT, "負けなかった率", "%", true, 1, rows -> rate(rows, r -> !"L".equals(r.getResult()))),
	GOALS_FOR(Type.RESULT, "得点", "点", true, 0, rows -> sum(rows, r -> r.getGoalsFor())),
	GOALS_FOR_PER_GAME(Type.RESULT, "1試合平均の得点", "点", true, 2, rows -> avg(rows, r -> r.getGoalsFor())),
	GOALS_AGAINST(Type.RESULT, "失点（少ない順）", "点", false, 0, rows -> sum(rows, r -> r.getGoalsAgainst())),
	GOALS_AGAINST_PER_GAME(Type.RESULT, "1試合平均の失点（少ない順）", "点", false, 2, rows -> avg(rows, r -> r.getGoalsAgainst())),
	GOAL_DIFF(Type.RESULT, "得失点差", "", true, 0,
			rows -> diff(sum(rows, r -> r.getGoalsFor()), sum(rows, r -> r.getGoalsAgainst()))),
	CLEAN_SHEETS(Type.RESULT, "無失点試合", "試合", true, 0, rows -> count(rows, r -> isZero(r.getGoalsAgainst()))),
	FAILED_TO_SCORE(Type.RESULT, "無得点試合（少ない順）", "試合", false, 0, rows -> count(rows, r -> isZero(r.getGoalsFor()))),
	BOTH_SCORED_RATE(Type.RESULT, "両チーム得点の率", "%", true, 1,
			rows -> rate(rows, r -> pos(r.getGoalsFor()) && pos(r.getGoalsAgainst()))),
	OVER25_RATE(Type.RESULT, "合計3点以上の試合の率", "%", true, 1,
			rows -> rate(rows, r -> r.getGoalsFor() != null && r.getGoalsAgainst() != null
					&& r.getGoalsFor() + r.getGoalsAgainst() >= 3)),
	SCORED_FIRST(Type.RESULT, "先制した試合", "試合", true, 0, rows -> count(rows, r -> "T".equals(r.getFirstGoal()))),
	COMEBACK_WINS(Type.RESULT, "逆転勝ち", "試合", true, 0,
			rows -> count(rows, r -> "W".equals(r.getResult()) && r.isFlowKnown() && r.isEverTrailed())),
	FIRST_HALF_GOALS(Type.RESULT, "前半の得点", "点", true, 0, rows -> sum(rows, r -> r.getGoalsFor1st())),
	SECOND_HALF_GOALS(Type.RESULT, "後半の得点", "点", true, 0, rows -> sum(rows, r -> r.getGoalsFor2nd())),
	FIRST_HALF_CONCEDED(Type.RESULT, "前半の失点（少ない順）", "点", false, 0, rows -> sum(rows, r -> r.getGoalsAgainst1st())),
	SECOND_HALF_CONCEDED(Type.RESULT, "後半の失点（少ない順）", "点", false, 0, rows -> sum(rows, r -> r.getGoalsAgainst2nd())),

	// ===================== スタッツ =====================
	POSSESSION(Type.STAT, "ボール保持率（平均）", "%", true, 1, rows -> avgD(rows, r -> r.getPossession())),
	XG(Type.STAT, "ゴール期待値 xG（合計）", "", true, 2, rows -> sumD(rows, r -> r.getXg())),
	XG_PER_GAME(Type.STAT, "ゴール期待値 xG（1試合平均）", "", true, 2, rows -> avgD(rows, r -> r.getXg())),
	XG_AGAINST_PER_GAME(Type.STAT, "被ゴール期待値 xGA（1試合平均・少ない順）", "", false, 2, rows -> avgD(rows, r -> r.getXgAgainst())),
	SHOTS_PER_GAME(Type.STAT, "シュート（1試合平均）", "本", true, 1, rows -> avg(rows, r -> r.getShots())),
	SHOTS_ON_TARGET_PER_GAME(Type.STAT, "枠内シュート（1試合平均）", "本", true, 1, rows -> avg(rows, r -> r.getShotsOnTarget())),
	ON_TARGET_RATE(Type.STAT, "枠内率", "%", true, 1, rows -> ratio(rows, r -> r.getShotsOnTarget(), r -> r.getShots())),
	SHOTS_AGAINST_PER_GAME(Type.STAT, "被シュート（1試合平均・少ない順）", "本", false, 1, rows -> avg(rows, r -> r.getShotsAgainst())),
	SHOTS_ON_TARGET_AGAINST_PER_GAME(Type.STAT, "被枠内シュート（1試合平均・少ない順）", "本", false, 1,
			rows -> avg(rows, r -> r.getShotsOnTargetAgainst())),
	BIG_CHANCES_PER_GAME(Type.STAT, "ビッグチャンス（1試合平均）", "回", true, 1, rows -> avg(rows, r -> r.getBigChances())),
	CORNERS_PER_GAME(Type.STAT, "コーナーキック（1試合平均）", "本", true, 1, rows -> avg(rows, r -> r.getCorners())),
	BOX_TOUCHES_PER_GAME(Type.STAT, "ボックス内タッチ（1試合平均）", "回", true, 1, rows -> avg(rows, r -> r.getBoxTouches())),
	PASS_RATE(Type.STAT, "パス成功率", "%", true, 1, rows -> ratio(rows, r -> r.getPassSuccess(), r -> r.getPassTry())),
	PASSES_PER_GAME(Type.STAT, "パス成功数（1試合平均）", "本", true, 0, rows -> avg(rows, r -> r.getPassSuccess())),
	TACKLE_RATE(Type.STAT, "タックル成功率", "%", true, 1, rows -> ratio(rows, r -> r.getTackleSuccess(), r -> r.getTackleTry())),
	INTERCEPTIONS_PER_GAME(Type.STAT, "インターセプト（1試合平均）", "回", true, 1, rows -> avg(rows, r -> r.getInterceptions())),
	CLEARS_PER_GAME(Type.STAT, "クリア（1試合平均）", "回", true, 1, rows -> avg(rows, r -> r.getClears())),
	KEEPER_SAVES_PER_GAME(Type.STAT, "キーパーセーブ（1試合平均）", "回", true, 1, rows -> avg(rows, r -> r.getKeeperSaves())),
	FOULS_PER_GAME(Type.STAT, "ファウル（1試合平均・少ない順）", "回", false, 1, rows -> avg(rows, r -> r.getFouls())),
	YELLOW_CARDS_PER_GAME(Type.STAT, "イエローカード（1試合平均・少ない順）", "枚", false, 2, rows -> avg(rows, r -> r.getYellowCards())),
	OFFSIDES_PER_GAME(Type.STAT, "オフサイド（1試合平均・少ない順）", "回", false, 1, rows -> avg(rows, r -> r.getOffsides()));

	/** データの種類 */
	public enum Type {
		/** 試合の結果から */
		RESULT,
		/** 試合のスタッツから（統計ができたリーグだけ） */
		STAT
	}

	private final Type type;
	private final String label;
	private final String unit;
	private final boolean higherBetter;
	private final int scale;
	private final Function<List<RankingRow>, Double> calc;

	RankingMetric(Type type, String label, String unit, boolean higherBetter, int scale,
			Function<List<RankingRow>, Double> calc) {
		this.type = type;
		this.label = label;
		this.unit = unit;
		this.higherBetter = higherBetter;
		this.scale = scale;
		this.calc = calc;
	}

	public Type getType() {
		return type;
	}

	public String getLabel() {
		return label;
	}

	public String getUnit() {
		return unit;
	}

	public boolean isHigherBetter() {
		return higherBetter;
	}

	public int getScale() {
		return scale;
	}

	/** 値（計算できなければ null） */
	public Double calc(List<RankingRow> rows) {
		return rows == null || rows.isEmpty() ? null : calc.apply(rows);
	}

	/** 名前から（無ければ null） */
	public static RankingMetric of(String name) {
		if (name == null) {
			return null;
		}
		for (RankingMetric m : values()) {
			if (m.name().equalsIgnoreCase(name.trim())) {
				return m;
			}
		}
		return null;
	}

	// ---------------- 計算の部品 ----------------

	private static Double sum(List<RankingRow> rows, Function<RankingRow, Integer> f) {
		double s = 0;
		int n = 0;
		for (RankingRow r : rows) {
			Integer v = f.apply(r);
			if (v != null) {
				s += v;
				n++;
			}
		}
		return n == 0 ? null : s;
	}

	private static Double avg(List<RankingRow> rows, Function<RankingRow, Integer> f) {
		double s = 0;
		int n = 0;
		for (RankingRow r : rows) {
			Integer v = f.apply(r);
			if (v != null) {
				s += v;
				n++;
			}
		}
		return n == 0 ? null : s / n;
	}

	private static Double sumD(List<RankingRow> rows, Function<RankingRow, Double> f) {
		double s = 0;
		int n = 0;
		for (RankingRow r : rows) {
			Double v = f.apply(r);
			if (v != null) {
				s += v;
				n++;
			}
		}
		return n == 0 ? null : s;
	}

	private static Double avgD(List<RankingRow> rows, Function<RankingRow, Double> f) {
		Double s = sumD(rows, f);
		if (s == null) {
			return null;
		}
		long n = rows.stream().filter(r -> f.apply(r) != null).count();
		return s / n;
	}

	private static Double count(List<RankingRow> rows, java.util.function.Predicate<RankingRow> p) {
		double c = 0;
		for (RankingRow r : rows) {
			if (p.test(r)) {
				c++;
			}
		}
		return c;
	}

	private static Double rate(List<RankingRow> rows, java.util.function.Predicate<RankingRow> p) {
		return count(rows, p) * 100.0 / rows.size();
	}

	private static Double ratio(List<RankingRow> rows, Function<RankingRow, Integer> num, Function<RankingRow, Integer> den) {
		double a = 0;
		double b = 0;
		for (RankingRow r : rows) {
			Integer x = num.apply(r);
			Integer y = den.apply(r);
			if (x != null && y != null && y > 0) {
				a += x;
				b += y;
			}
		}
		return b == 0 ? null : a * 100.0 / b;
	}

	private static Double diff(Double a, Double b) {
		return a == null || b == null ? null : a - b;
	}

	private static boolean isZero(Integer v) {
		return v != null && v == 0;
	}

	private static boolean pos(Integer v) {
		return v != null && v > 0;
	}
}
