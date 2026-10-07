package dev.web.api.dashboard.bm_w002;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import dev.web.api.dashboard.support.DashboardSupport;
import dev.web.api.dashboard.teamDTO.DashboardTeamDTO;
import dev.web.api.dashboard.teamDTO.DashboardTeamRateRow;
import dev.web.repository.bm.DashboardMatchRepository;
import lombok.RequiredArgsConstructor;

/**
 * トップ画面の見込み計算（得点しやすさ・失点しにくさ・勝ち/分け/負けの確率・ゴール数の見込み）
 *
 * <h2>計算方法</h2>
 * <ol>
 *   <li>今季のチーム × H/A の1試合平均得点・失点を、リーグ平均（同じ H/A）と比べる（ビュー dashboard_team_rate）。
 *       試合数が少ないと極端になるので、リーグ平均 {@value #PRIOR_MATCHES} 試合分を混ぜて均す。</li>
 *   <li>ホームの得点見込み = (ホームの平均得点 ÷ リーグのホーム平均得点) × (アウェーの平均失点 ÷ リーグのアウェー平均失点) × リーグのホーム平均得点。
 *       アウェー側も同様。</li>
 *   <li>得点はポアソン分布として 0〜{@value #MAX_GOALS} 点の組み合わせから勝ち・分け・負けの確率を出す。
 *       ライブは残り時間の割合を得点見込みに掛け、今のスコアに足した最終スコアで判定する（「このままなら」）。</li>
 *   <li>ゴール数の見込み = 今のスコア + 両チームの得点見込み。3.0 以上「多い」、2.2 以上「普通」、それ未満「少ない」。</li>
 *   <li>得点しやすさ（攻）: 平均得点 ÷ リーグ平均 が 1.15 以上 ◎、0.85 未満 △。
 *       失点しにくさ（守）: 平均失点 ÷ リーグ平均 が 0.85 以下 ◎、1.15 超 △。今季 {@value #MIN_MATCHES_FOR_GRADE} 試合未満は評価しない。</li>
 * </ol>
 * <p>今季の成績が無いチームはリーグ平均（リーグも無ければ全体の既定値）として計算する。</p>
 *
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class DashboardForecastService {

	/** 均すときに混ぜるリーグ平均の試合数 */
	static final int PRIOR_MATCHES = 3;

	/** ポアソンで数える最大得点 */
	static final int MAX_GOALS = 10;

	/** 評価（◎○△）を出す最低試合数 */
	static final int MIN_MATCHES_FOR_GRADE = 3;

	/** リーグの成績も無いときの既定値（ホーム・アウェーの平均得点） */
	private static final double DEFAULT_HOME_GOALS = 1.45;
	private static final double DEFAULT_AWAY_GOALS = 1.15;

	/** チーム成績のキャッシュ時間 */
	private static final long CACHE_MILLIS = 5 * 60 * 1000L;

	private final DashboardMatchRepository matchRepository;

	private volatile Map<String, DashboardTeamRateRow> teamCache = new HashMap<>();
	private volatile Map<String, DashboardTeamRateRow> leagueCache = new HashMap<>();
	private volatile long cachedAt = 0L;

	/**
	 * 計算結果（丸める前の値。画面に出す形は {@link #toForecastDTO} / {@link #toTeamDTO} で作る）
	 */
	public static final class Result {
		public double home;
		public double draw;
		public double away;
		public double expectedTotal;
		public Double expectedRest;
		public TeamSide homeTeam;
		public TeamSide awayTeam;
	}

	/** チーム側の値 */
	public static final class TeamSide {
		public Double avgGoalsFor;
		public Double avgGoalsAgainst;
		public String attackGrade;
		public String defenseGrade;
	}

	/**
	 * 計算する。
	 *
	 * @param country 国
	 * @param league リーグ
	 * @param homeTeam ホーム
	 * @param awayTeam アウェー
	 * @param homeNow 今のホームの得点（試合前は 0）
	 * @param awayNow 今のアウェーの得点（試合前は 0）
	 * @param remaining 残り時間の割合（試合前は 1）
	 * @param live ライブか（残りのゴール数の見込みを返す）
	 */
	public Result forecast(String country, String league, String homeTeam, String awayTeam,
			int homeNow, int awayNow, double remaining, boolean live) {
		loadIfNeeded();
		DashboardTeamRateRow lgH = this.leagueCache.get(leagueKey(country, league, "H"));
		DashboardTeamRateRow lgA = this.leagueCache.get(leagueKey(country, league, "A"));
		double lgHomeFor = positive(lgH == null ? null : lgH.getLeagueAvgGoalsFor(), DEFAULT_HOME_GOALS);
		double lgHomeAgainst = positive(lgH == null ? null : lgH.getLeagueAvgGoalsAgainst(), DEFAULT_AWAY_GOALS);
		double lgAwayFor = positive(lgA == null ? null : lgA.getLeagueAvgGoalsFor(), DEFAULT_AWAY_GOALS);
		double lgAwayAgainst = positive(lgA == null ? null : lgA.getLeagueAvgGoalsAgainst(), DEFAULT_HOME_GOALS);

		DashboardTeamRateRow h = this.teamCache.get(teamKey(country, league, homeTeam, "H"));
		DashboardTeamRateRow a = this.teamCache.get(teamKey(country, league, awayTeam, "A"));

		double hFor = shrink(h == null ? null : h.getAvgGoalsFor(), h == null ? 0 : h.getMatchCount(), lgHomeFor);
		double hAgainst = shrink(h == null ? null : h.getAvgGoalsAgainst(), h == null ? 0 : h.getMatchCount(), lgHomeAgainst);
		double aFor = shrink(a == null ? null : a.getAvgGoalsFor(), a == null ? 0 : a.getMatchCount(), lgAwayFor);
		double aAgainst = shrink(a == null ? null : a.getAvgGoalsAgainst(), a == null ? 0 : a.getMatchCount(), lgAwayAgainst);

		double lambdaHome = (hFor / lgHomeFor) * (aAgainst / lgAwayAgainst) * lgHomeFor * remaining;
		double lambdaAway = (aFor / lgAwayFor) * (hAgainst / lgHomeAgainst) * lgAwayFor * remaining;

		double[] ph = poisson(lambdaHome);
		double[] pa = poisson(lambdaAway);
		double win = 0;
		double draw = 0;
		double loss = 0;
		for (int i = 0; i <= MAX_GOALS; i++) {
			for (int j = 0; j <= MAX_GOALS; j++) {
				double p = ph[i] * pa[j];
				int fh = homeNow + i;
				int fa = awayNow + j;
				if (fh > fa) {
					win += p;
				} else if (fh == fa) {
					draw += p;
				} else {
					loss += p;
				}
			}
		}
		double sum = win + draw + loss;

		Result r = new Result();
		r.home = win / sum;
		r.draw = draw / sum;
		r.away = loss / sum;
		r.expectedTotal = homeNow + awayNow + lambdaHome + lambdaAway;
		r.expectedRest = live ? lambdaHome + lambdaAway : null;
		r.homeTeam = side(h, lgHomeFor, lgHomeAgainst);
		r.awayTeam = side(a, lgAwayFor, lgAwayAgainst);
		return r;
	}

	/**
	 * 画面用の見込み。未ログインは数値を null にし、バーは 10% 単位に丸める。
	 */
	public static DashboardForecastDTO toForecastDTO(Result r, boolean loggedIn) {
		int[] exact = percent(r.home, r.draw, r.away, 1);
		DashboardForecastDTO dto = new DashboardForecastDTO();
		dto.setGoalsLabel(r.expectedTotal >= 3.0 ? "HIGH" : r.expectedTotal >= 2.2 ? "MID" : "LOW");
		if (loggedIn) {
			dto.setProbHome(exact[0]);
			dto.setProbDraw(exact[1]);
			dto.setProbAway(exact[2]);
			dto.setBarHome(exact[0]);
			dto.setBarDraw(exact[1]);
			dto.setBarAway(exact[2]);
			dto.setExpectedTotalGoals(round1(r.expectedTotal));
			dto.setExpectedRestGoals(r.expectedRest == null ? null : round1(r.expectedRest));
		} else {
			int[] bar = percent(r.home, r.draw, r.away, 10);
			dto.setBarHome(bar[0]);
			dto.setBarDraw(bar[1]);
			dto.setBarAway(bar[2]);
		}
		return dto;
	}

	/**
	 * 画面用のチーム。未ログインは平均得点・失点を null にする。
	 */
	public static DashboardTeamDTO toTeamDTO(String name, Integer rank, TeamSide side, boolean loggedIn) {
		DashboardTeamDTO dto = new DashboardTeamDTO();
		dto.setName(name);
		dto.setRank(rank);
		if (side != null) {
			dto.setAttackGrade(side.attackGrade);
			dto.setDefenseGrade(side.defenseGrade);
			if (loggedIn) {
				dto.setAvgGoalsFor(side.avgGoalsFor == null ? null : round1(side.avgGoalsFor));
				dto.setAvgGoalsAgainst(side.avgGoalsAgainst == null ? null : round1(side.avgGoalsAgainst));
			}
		}
		return dto;
	}

	/** 確率を合計 100 の整数（step 単位）にする */
	static int[] percent(double h, double d, double a, int step) {
		int ph = (int) (Math.round(h * 100 / step) * step);
		int pa = (int) (Math.round(a * 100 / step) * step);
		if (ph + pa > 100) {
			pa = 100 - ph;
		}
		return new int[] { ph, 100 - ph - pa, pa };
	}

	private static TeamSide side(DashboardTeamRateRow row, double lgFor, double lgAgainst) {
		TeamSide s = new TeamSide();
		if (row == null) {
			return s;
		}
		s.avgGoalsFor = row.getAvgGoalsFor();
		s.avgGoalsAgainst = row.getAvgGoalsAgainst();
		if (row.getMatchCount() >= MIN_MATCHES_FOR_GRADE) {
			double att = row.getAvgGoalsFor() / lgFor;
			double def = row.getAvgGoalsAgainst() / lgAgainst;
			s.attackGrade = att >= 1.15 ? "A" : att < 0.85 ? "C" : "B";
			s.defenseGrade = def <= 0.85 ? "A" : def > 1.15 ? "C" : "B";
		}
		return s;
	}

	/** 試合数が少ないときはリーグ平均に寄せる */
	private static double shrink(Double value, int n, double leagueAvg) {
		if (value == null || n <= 0) {
			return leagueAvg;
		}
		return (value * n + leagueAvg * PRIOR_MATCHES) / (n + PRIOR_MATCHES);
	}

	private static double[] poisson(double lambda) {
		double[] p = new double[MAX_GOALS + 1];
		double l = Math.max(lambda, 1e-6);
		p[0] = Math.exp(-l);
		for (int k = 1; k <= MAX_GOALS; k++) {
			p[k] = p[k - 1] * l / k;
		}
		return p;
	}

	private static double positive(Double v, double def) {
		return (v == null || v <= 0) ? def : v;
	}

	private static double round1(double v) {
		return Math.round(v * 10) / 10.0;
	}

	private void loadIfNeeded() {
		long now = System.currentTimeMillis();
		if (now - this.cachedAt < CACHE_MILLIS) {
			return;
		}
		synchronized (this) {
			if (now - this.cachedAt < CACHE_MILLIS) {
				return;
			}
			List<DashboardTeamRateRow> rows = this.matchRepository.findTeamRates();
			Map<String, DashboardTeamRateRow> teams = new HashMap<>();
			Map<String, DashboardTeamRateRow> leagues = new HashMap<>();
			for (DashboardTeamRateRow r : rows) {
				teams.put(teamKey(r.getCountry(), r.getLeague(), r.getTeam(), r.getHa()), r);
				leagues.putIfAbsent(leagueKey(r.getCountry(), r.getLeague(), r.getHa()), r);
			}
			this.teamCache = teams;
			this.leagueCache = leagues;
			this.cachedAt = now;
		}
	}

	private static String teamKey(String country, String league, String team, String ha) {
		return DashboardSupport.norm(country) + "|" + DashboardSupport.norm(league) + "|" + DashboardSupport.norm(team) + "|" + ha;
	}

	private static String leagueKey(String country, String league, String ha) {
		return DashboardSupport.norm(country) + "|" + DashboardSupport.norm(league) + "|" + ha;
	}
}
