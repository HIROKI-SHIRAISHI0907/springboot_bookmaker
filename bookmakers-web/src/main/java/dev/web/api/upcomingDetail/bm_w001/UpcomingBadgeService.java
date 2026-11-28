package dev.web.api.upcomingDetail.bm_w001;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import dev.web.api.upcomingDetail.upcomingDetailDTO.TeamBadgeDTO;
import dev.web.repository.bm.StaticOverviewSource;
import dev.web.repository.bm.UpcomingBadgeRepository;
import dev.web.repository.bm.UpcomingBadgeRepository.MatchRow;
import dev.web.repository.bm.UpcomingBadgeRepository.Metric;
import dev.web.repository.bm.UpcomingBadgeRepository.MetricRow;
import dev.web.repository.bm.UpcomingDetailRepository;
import lombok.RequiredArgsConstructor;

/**
 * 試合予定の詳細に出すチームのアイコン（2026-11-27 追加）
 *
 * <h2>元データ</h2>
 * B006（統計CSV取り込み）の surface_overview_match（チーム × 試合の結果）と、統計のビュー
 * （team_style_profile・team_attack_stats・team_efficiency_stats・team_defense_stats・team_goal_timing_stats）。
 * シーズンは試合のリーグの最新シーズン（順位表と同じ）。ラウンド番号のある試合（リーグ戦）だけ。
 *
 * <h2>ラウンドがつながっていないときは出さない</h2>
 * 予定の試合のラウンド N（URL の round「ラウンド N」）が分からないとき・N より後のラウンドがもう反映されている（延期試合）ときは、
 * 「流れ」「前節」「今季」のアイコンは出さない。
 * <ul>
 *   <li>流れ（X連勝中など）: 前節 N−1 が反映済みで、連続の始まりが確定している（1 節目から、または連続の1つ前の節が反映済み）こと。
 *       連続の途中・手前の節が欠けていれば出さない（実際より少なく数えた値を出さないため）。</li>
 *   <li>前節（前節ホーム初勝利など）: 前節 N−1 が反映済み。「初」の判定は 1〜N−1 節がすべて反映済みのときだけ。</li>
 *   <li>今季（ホーム無敗など件数で判定するもの）: 1〜N−1 節がすべて反映済みのときだけ。</li>
 *   <li>割合で判定するもの（序盤好調・逆転勝ちが多い・先制すると負けない）とリーグ順位は、反映済みの試合だけで判定する。</li>
 * </ul>
 *
 * <h2>リーグ順位</h2>
 * 同じシーズン・リーグで {@value #RANK_MIN_MATCHES} 試合以上あるチームが {@value #RANK_MIN_TEAMS} チーム以上のときだけ。
 * 1 位（または最少・ワースト）のときに出す。同じ値のチームがあれば「タイ」。
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class UpcomingBadgeService {

	static final int STREAK_WIN = 3;
	static final int STREAK_LOSE = 3;
	static final int STREAK_UNBEATEN = 5;
	static final int STREAK_WINLESS = 5;
	static final int STREAK_DRAW = 3;
	static final int STREAK_MULTI_GOAL = 3;
	static final int STREAK_SCORING = 5;
	static final int STREAK_CLEAN_SHEET = 3;
	static final int STREAK_NO_SCORE = 3;
	static final int STREAK_CONCEDING = 5;
	/** 前節で連勝・連敗が止まった（その前の連続の長さ） */
	static final int STREAK_STOPPED = 3;
	/** 直近5試合の勝ち数 */
	static final int LAST5_WINS = 4;
	/** 直近5試合の勝ち数がこれ以下なら「不調」（2026-11-28 追加） */
	static final int LAST5_BAD_WINS = 1;
	/** リーグ内の得点・失点の順位を出す範囲（上位・ワースト何位まで。2026-11-28 追加） */
	static final int RANK_WITHIN = 3;
	/** 順位表の上位・下位（何チームまで。2026-11-28 追加） */
	static final int TABLE_EDGE = 3;
	/** 大勝・大敗（得失点差） */
	static final int BIG_MARGIN = 3;
	/** 今季未勝利（試合数） */
	static final int WINLESS_SEASON_GAMES = 5;
	/** ホーム無敗・アウェー未勝利など（その会場の試合数） */
	static final int VENUE_MIN_GAMES = 3;
	/** 序盤/中盤/終盤好調（試合数・勝率%） */
	static final int PHASE_MIN_GAMES = 3;
	static final int PHASE_WIN_PCT = 70;
	/** 逆転勝ちが多い（勝ち数・逆転勝ちの割合%） */
	static final int COMEBACK_MIN_WINS = 3;
	static final int COMEBACK_PCT = 30;
	/** 先制すると負けない（先制した試合数） */
	static final int FIRST_GOAL_MIN = 4;
	/** リーグ順位 */
	static final int RANK_MIN_MATCHES = 3;
	static final int RANK_MIN_TEAMS = 4;

	private static final String GOOD = "GOOD";
	private static final String BAD = "BAD";
	private static final String INFO = "INFO";

	private static final Pattern ROUND = Pattern.compile("(?:ラウンド|Round|第)\\s*(\\d+)");

	private final UpcomingBadgeRepository repository;

	private final UpcomingDetailRepository detailRepository;

	/** リーグ順位のアイコンの定義 */
	private enum Rank {
		POSSESSION_TOP(Metric.POSSESSION, true, "ボール保持率リーグ1位", GOOD, "%"),
		POSSESSION_LOW(Metric.POSSESSION, false, "ボール保持率リーグ最下位", INFO, "%"),
		PASS_ACCURACY_TOP(Metric.PASS_ACCURACY, true, "パス成功率リーグ1位", GOOD, "%"),
		GOALS_FOR_TOP(Metric.GOALS_FOR, true, "得点力リーグ1位", GOOD, "点/試合", "得点力リーグ%d位"),
		GOALS_FOR_WORST(Metric.GOALS_FOR, false, "得点力リーグワースト", BAD, "点/試合", "得点力リーグワースト%d位"),
		GOALS_AGAINST_BEST(Metric.GOALS_AGAINST, false, "失点の少なさリーグ1位", GOOD, "失点/試合", "失点の少なさリーグ%d位"),
		GOALS_AGAINST_WORST(Metric.GOALS_AGAINST, true, "失点数リーグワースト", BAD, "失点/試合", "失点数リーグワースト%d位"),
		SHOTS_TOP(Metric.SHOTS, true, "シュート数リーグ1位", GOOD, "本/90分"),
		SHOTS_ON_TARGET_TOP(Metric.SHOTS_ON_TARGET, true, "枠内シュート数リーグ1位", GOOD, "本/90分"),
		CORNERS_TOP(Metric.CORNERS, true, "コーナーキック数リーグ1位", INFO, "本/90分"),
		XG_TOP(Metric.XG, true, "xG（ゴール期待値）リーグ1位", GOOD, "/試合"),
		FINISHING_TOP(Metric.FINISHING, true, "決定力リーグ1位", GOOD, "%（ゴール÷シュート）"),
		SHOTS_CONCEDED_BEST(Metric.SHOTS_CONCEDED, false, "被シュートの少なさリーグ1位", GOOD, "本/90分"),
		SAVE_RATE_TOP(Metric.SAVE_RATE, true, "セーブ率リーグ1位", GOOD, "%"),
		SCORED_FIRST_TOP(Metric.SCORED_FIRST, true, "先制率リーグ1位", GOOD, "%"),
		LATE_GOALS_TOP(Metric.LATE_GOALS, true, "終盤（76分〜）の得点数リーグ1位", GOOD, "点/試合"),
		LATE_CONCEDED_WORST(Metric.LATE_CONCEDED, true, "終盤（76分〜）の失点数リーグワースト", BAD, "失点/試合");

		final Metric metric;
		/** true: 値が大きいほど上 */
		final boolean high;
		final String label;
		final String tone;
		final String unit;
		/** 2 位以下も出すときの表記（%d = 順位）。null なら 1 位だけ（2026-11-28 追加） */
		final String labelN;

		Rank(Metric metric, boolean high, String label, String tone, String unit) {
			this(metric, high, label, tone, unit, null);
		}

		Rank(Metric metric, boolean high, String label, String tone, String unit, String labelN) {
			this.metric = metric;
			this.high = high;
			this.label = label;
			this.tone = tone;
			this.unit = unit;
			this.labelN = labelN;
		}
	}

	public UpcomingBadgeResponse get(String country, String league, String home, String away, String roundLabel) {
		UpcomingBadgeResponse res = new UpcomingBadgeResponse();
		res.setHome(home);
		res.setAway(away);
		Integer round = parseRound(roundLabel);
		res.setRound(round);
		if (blank(country) || blank(league) || blank(home) || blank(away)) {
			res.getNotes().add("国・リーグ・チームが分からないため、アイコンは出していません");
			return res;
		}
		String latest = detailRepository.findLatestSeason(country, league);
		final String season = latest != null ? latest : StaticOverviewSource.SEASON;
		if (latest == null) {
			// 統計がまだ無いリーグは、終了済の試合（static_data）から作ったデータで出せるものだけ出す（2026-11-28 追加）
			res.getNotes().add("このリーグの統計がまだ無いため、終了済の試合から出せるものだけ出しています（ボール保持率などのリーグ順位・序盤/中盤/終盤は統計ができてから）");
		}
		res.setSeason(StaticOverviewSource.isStatic(season) ? null : season);

		// 前節の時点の順位（チーム → {順位, 節}）。teamBadges で入れる（2026-11-28 追加）
		Map<String, int[]> lastRank = new LinkedHashMap<>();
		res.setHomeBadges(teamBadges(country, league, season, home, "H", round, res.getNotes(), lastRank));
		res.setAwayBadges(teamBadges(country, league, season, away, "A", round, res.getNotes(), lastRank));

		// リーグ順位（数値はリーグ全体で 1 回だけ引く）
		Map<Metric, List<MetricRow>> cache = new LinkedHashMap<>();
		// 順位表の上位・下位（サイト表示の順位。チーム数は得点のデータがあるチーム数。2026-11-28 追加）
		int teamCount = cache.computeIfAbsent(Metric.GOALS_FOR, m -> repository.findMetric(m, country, league, season)).size();
		addTablePosition(res.getHomeBadges(), lastRank.get(norm(home)), teamCount);
		addTablePosition(res.getAwayBadges(), lastRank.get(norm(away)), teamCount);
		for (Rank r : Rank.values()) {
			List<MetricRow> rows = cache.computeIfAbsent(r.metric, m -> repository.findMetric(m, country, league, season));
			addRank(res.getHomeBadges(), r, rows, home);
			addRank(res.getAwayBadges(), r, rows, away);
		}
		addStyle(res.getHomeBadges(), repository.findStyleLabel(country, league, season, home));
		addStyle(res.getAwayBadges(), repository.findStyleLabel(country, league, season, away));
		return res;
	}

	/** 順位表の上位・下位（2026-11-28 追加） */
	private void addTablePosition(List<TeamBadgeDTO> out, int[] rankRound, int teamCount) {
		if (rankRound == null || teamCount < TABLE_EDGE * 2 + 2) {
			return;
		}
		int rank = rankRound[0];
		String when = "第" + rankRound[1] + "節の時点（" + teamCount + "チーム中）";
		if (rank == 1) {
			add(out, "TABLE_TOP", "首位", GOOD, "順位", when);
		} else if (rank <= TABLE_EDGE) {
			add(out, "TABLE_UPPER", "上位（" + rank + "位）", GOOD, "順位", when);
		} else if (rank > teamCount - TABLE_EDGE) {
			add(out, "TABLE_LOWER", rank == teamCount ? "最下位" : "下位に低迷（" + rank + "位）", BAD, "順位", when);
		}
	}

	// =========================================================
	// チームの試合から（流れ・前節・今季）
	// =========================================================
	private List<TeamBadgeDTO> teamBadges(String country, String league, String season, String team, String venue,
			Integer round, List<String> notes, Map<String, int[]> lastRank) {
		List<TeamBadgeDTO> out = new ArrayList<>();
		List<MatchRow> all = repository.findMatches(country, league, season, team);
		Map<Integer, MatchRow> byRound = new LinkedHashMap<>();
		for (MatchRow r : all) {
			byRound.put(r.getRoundNo(), r); // 同じラウンドが 2 つあれば後（新しい試合日時）
		}

		boolean ordered = round != null && round > 1;
		if (round == null) {
			addNote(notes, "ラウンドが分からないため、連勝・前節などのアイコンは出していません");
		} else if (all.stream().anyMatch(r -> r.getRoundNo() >= round)) {
			ordered = false;
			addNote(notes, team + ": 第" + round + "節より後の試合がすでにある（延期試合など）ため、連勝・前節などのアイコンは出していません");
		}

		// 予定の試合より前（ラウンド N 未満）の試合
		List<MatchRow> before = new ArrayList<>();
		for (MatchRow r : byRound.values()) {
			if (round == null || r.getRoundNo() < round) {
				before.add(r);
			}
		}

		if (ordered) {
			int prevRound = round - 1;
			MatchRow prev = byRound.get(prevRound);
			if (prev == null) {
				addNote(notes, team + ": 前節（第" + prevRound + "節）がまだ反映されていないため、連勝・前節などのアイコンは出していません");
			} else {
				boolean fullSeason = true;
				for (int r = 1; r <= prevRound; r++) {
					if (!byRound.containsKey(r)) {
						fullSeason = false;
						break;
					}
				}
				if (prev.getTeamRank() != null && prev.getTeamRank() > 0) {
					lastRank.put(norm(team), new int[] { prev.getTeamRank(), prevRound });
				}
				streaks(out, byRound, prevRound);
				previous(out, byRound, prev, prevRound, before, fullSeason);
				if (fullSeason) {
					season(out, before, venue);
				}
			}
		}
		rates(out, before);
		return out;
	}

	/** 流れ（連続記録） */
	private void streaks(List<TeamBadgeDTO> out, Map<Integer, MatchRow> byRound, int prevRound) {
		int win = streak(byRound, prevRound, r -> "W".equals(r.getResult()));
		int lose = streak(byRound, prevRound, r -> "L".equals(r.getResult()));
		int unbeaten = streak(byRound, prevRound, r -> !"L".equals(r.getResult()));
		int winless = streak(byRound, prevRound, r -> !"W".equals(r.getResult()));
		int draw = streak(byRound, prevRound, r -> "D".equals(r.getResult()));
		int multi = streak(byRound, prevRound, r -> r.getGoalsFor() >= 2);
		int scoring = streak(byRound, prevRound, r -> r.getGoalsFor() > 0);
		int clean = streak(byRound, prevRound, r -> r.getGoalsAgainst() == 0);
		int noScore = streak(byRound, prevRound, r -> r.getGoalsFor() == 0);
		int conceding = streak(byRound, prevRound, r -> r.getGoalsAgainst() > 0);

		if (win >= STREAK_WIN) {
			add(out, "WIN_STREAK", win + "連勝中", GOOD, "流れ", range(prevRound, win) + " " + win + "試合連続で勝利");
		}
		if (lose >= STREAK_LOSE) {
			add(out, "LOSE_STREAK", lose + "連敗中", BAD, "流れ", range(prevRound, lose) + " " + lose + "試合連続で敗戦");
		}
		if (unbeaten >= STREAK_UNBEATEN && unbeaten > win) {
			add(out, "UNBEATEN_STREAK", unbeaten + "試合負けなし", GOOD, "流れ", range(prevRound, unbeaten) + " 勝ちか引き分け");
		}
		if (winless >= STREAK_WINLESS && winless > lose) {
			add(out, "WINLESS_STREAK", winless + "試合勝ちなし", BAD, "流れ", range(prevRound, winless) + " 引き分けか敗戦");
		}
		if (draw >= STREAK_DRAW) {
			add(out, "DRAW_STREAK", "ドローが続く（" + draw + "試合）", INFO, "流れ", range(prevRound, draw) + " " + draw + "試合連続で引き分け");
		}
		if (multi >= STREAK_MULTI_GOAL) {
			add(out, "MULTI_GOAL_STREAK", multi + "試合連続複数得点中", GOOD, "流れ", range(prevRound, multi) + " 毎試合2点以上");
		}
		if (scoring >= STREAK_SCORING && scoring > multi) {
			add(out, "SCORING_STREAK", scoring + "試合連続得点中", GOOD, "流れ", range(prevRound, scoring) + " 毎試合得点");
		}
		if (clean >= STREAK_CLEAN_SHEET) {
			add(out, "CLEAN_SHEET_STREAK", clean + "試合連続無失点", GOOD, "流れ", range(prevRound, clean) + " 毎試合失点0");
		}
		if (noScore >= STREAK_NO_SCORE) {
			add(out, "NO_SCORE_STREAK", noScore + "試合連続無得点", BAD, "流れ", range(prevRound, noScore) + " 毎試合得点0");
		}
		if (conceding >= STREAK_CONCEDING) {
			add(out, "CONCEDING_STREAK", conceding + "試合連続失点中", BAD, "流れ", range(prevRound, conceding) + " 毎試合失点");
		}

		// 直近5試合（5試合すべて反映済みのときだけ）
		boolean last5 = prevRound >= 5;
		int w5 = 0;
		for (int r = prevRound; last5 && r > prevRound - 5; r--) {
			MatchRow m = byRound.get(r);
			if (m == null) {
				last5 = false;
			} else if ("W".equals(m.getResult())) {
				w5++;
			}
		}
		if (last5 && w5 >= LAST5_WINS && win < 5) {
			add(out, "LAST5_GOOD", "直近5試合で" + w5 + "勝", GOOD, "流れ", "第" + (prevRound - 4) + "〜" + prevRound + "節");
		}
		// 不調（直近5試合で1勝以下。5試合勝ちなし・5連敗のアイコンが出ているときは重ねない。2026-11-28 追加）
		if (last5 && w5 <= LAST5_BAD_WINS && winless < STREAK_WINLESS && lose < 5) {
			int d5 = 0;
			int l5 = 0;
			for (int r = prevRound; r > prevRound - 5; r--) {
				String res5 = byRound.get(r).getResult();
				if ("D".equals(res5)) {
					d5++;
				} else if ("L".equals(res5)) {
					l5++;
				}
			}
			add(out, "LAST5_BAD", w5 == 0 ? "直近5試合で未勝利" : "直近5試合で1勝のみ", BAD, "流れ",
					"第" + (prevRound - 4) + "〜" + prevRound + "節 " + w5 + "勝" + d5 + "分" + l5 + "敗");
		}
	}

	/**
	 * 前節から遡って条件が続く試合数。連続の始まりが確定しない（手前の節が欠けている）ときは 0（出さない）。
	 */
	static int streak(Map<Integer, MatchRow> byRound, int prevRound, Predicate<MatchRow> cond) {
		int n = 0;
		for (int r = prevRound; ; r--) {
			if (r <= 0) {
				return n; // 1 節目から続いている
			}
			MatchRow m = byRound.get(r);
			if (m == null) {
				return 0; // 欠けている → 本当の長さが分からない
			}
			if (!cond.test(m)) {
				return n; // 1 つ前の節で途切れている（確定）
			}
			n++;
		}
	}

	/** 前節 */
	private void previous(List<TeamBadgeDTO> out, Map<Integer, MatchRow> byRound, MatchRow prev, int prevRound,
			List<MatchRow> before, boolean fullSeason) {
		boolean won = "W".equals(prev.getResult());
		String vs = "前節（第" + prevRound + "節）" + ("H".equals(prev.getHa()) ? "ホーム" : "アウェー") + " vs "
				+ nvl(prev.getOpponent()) + " " + prev.getGoalsFor() + "-" + prev.getGoalsAgainst();
		if (won && fullSeason) {
			long wins = before.stream().filter(m -> "W".equals(m.getResult())).count();
			long haWins = before.stream().filter(m -> "W".equals(m.getResult()) && prev.getHa().equals(m.getHa())).count();
			if (wins == 1) {
				add(out, "PREV_FIRST_WIN", "前節今季初勝利", GOOD, "前節", vs);
			} else if (haWins == 1) {
				boolean h = "H".equals(prev.getHa());
				add(out, h ? "PREV_HOME_FIRST_WIN" : "PREV_AWAY_FIRST_WIN", h ? "前節ホーム初勝利" : "前節アウェー初勝利",
						GOOD, "前節", vs);
			}
		}
		if (won && prev.isFlowKnown() && prev.isEverTrailed()) {
			add(out, "PREV_COMEBACK_WIN", "前節逆転勝ち", GOOD, "前節", vs);
		}
		int diff = prev.getGoalsFor() - prev.getGoalsAgainst();
		if (diff >= BIG_MARGIN) {
			add(out, "PREV_BIG_WIN", "前節大勝", GOOD, "前節", vs);
		} else if (diff <= -BIG_MARGIN) {
			add(out, "PREV_BIG_LOSS", "前節大敗", BAD, "前節", vs);
		}
		if (won && prev.getGoalsAgainst() == 0 && diff < BIG_MARGIN) {
			add(out, "PREV_CLEAN_WIN", "前節完封勝利", GOOD, "前節", vs);
		}
		// 前節で連敗・連勝が止まった（前々節までの連続。始まりが確定しているときだけ）
		if (prevRound >= 2) {
			if (!"L".equals(prev.getResult())) {
				int l = streak(byRound, prevRound - 1, m -> "L".equals(m.getResult()));
				if (l >= STREAK_STOPPED) {
					add(out, "PREV_STOP_LOSING", "前節で連敗ストップ（" + l + "連敗）", GOOD, "前節", vs);
				}
			}
			if (!won) {
				int w = streak(byRound, prevRound - 1, m -> "W".equals(m.getResult()));
				if (w >= STREAK_STOPPED) {
					add(out, "PREV_STOP_WINNING", "前節で連勝ストップ（" + w + "連勝）", BAD, "前節", vs);
				}
			}
		}
	}

	/** 今季（1〜N−1 節がすべて反映済みのときだけ） */
	private void season(List<TeamBadgeDTO> out, List<MatchRow> before, String venue) {
		long games = before.size();
		long wins = before.stream().filter(m -> "W".equals(m.getResult())).count();
		if (wins == 0 && games >= WINLESS_SEASON_GAMES) {
			add(out, "SEASON_WINLESS", "今季未勝利（" + games + "試合）", BAD, "今季", "今季まだ勝ちがない（初勝利がかかる）");
		}
		// この試合の会場（ホームチームはホーム戦、アウェーチームはアウェー戦）の成績
		String place = "H".equals(venue) ? "ホーム" : "アウェー";
		List<MatchRow> v = before.stream().filter(m -> venue.equals(m.getHa())).collect(Collectors.toList());
		long vw = v.stream().filter(m -> "W".equals(m.getResult())).count();
		long vl = v.stream().filter(m -> "L".equals(m.getResult())).count();
		long vd = v.size() - vw - vl;
		String rec = place + " " + v.size() + "試合 " + vw + "勝" + vd + "分" + vl + "敗";
		if (v.size() >= VENUE_MIN_GAMES) {
			if (vl == 0 && vw == v.size()) {
				add(out, venue + "_ALL_WIN", place + "全勝", GOOD, "今季", rec);
			} else if (vl == 0) {
				add(out, venue + "_UNBEATEN", place + "無敗", GOOD, "今季", rec);
			} else if (vw == 0) {
				add(out, venue + "_WINLESS", place + "未勝利", BAD, "今季", rec);
			}
		}
	}

	/** 割合で判定するもの（反映済みの試合だけで判定） */
	private void rates(List<TeamBadgeDTO> out, List<MatchRow> before) {
		String[][] phases = { { "FIRST", "序盤" }, { "MID", "中盤" }, { "LAST", "終盤" } };
		for (String[] p : phases) {
			List<MatchRow> ps = before.stream().filter(m -> p[0].equals(m.getPhase())).collect(Collectors.toList());
			long w = ps.stream().filter(m -> "W".equals(m.getResult())).count();
			if (ps.size() >= PHASE_MIN_GAMES && w * 100 >= (long) PHASE_WIN_PCT * ps.size()) {
				add(out, "PHASE_" + p[0], p[1] + "好調", GOOD, "今季", p[1] + "（シーズンの3分の1ずつ） " + ps.size() + "試合 " + w + "勝");
			}
		}
		List<MatchRow> wins = before.stream().filter(m -> "W".equals(m.getResult()) && m.isFlowKnown()).collect(Collectors.toList());
		long cb = wins.stream().filter(MatchRow::isEverTrailed).count();
		if (wins.size() >= COMEBACK_MIN_WINS && cb * 100 >= (long) COMEBACK_PCT * wins.size() && cb > 0) {
			add(out, "COMEBACK_STRONG", "逆転勝ちが多い", GOOD, "今季", "勝ち " + wins.size() + "試合のうち逆転勝ち " + cb + "試合");
		}
		List<MatchRow> first = before.stream().filter(m -> "T".equals(m.getFirstGoal())).collect(Collectors.toList());
		long firstLose = first.stream().filter(m -> "L".equals(m.getResult())).count();
		long firstWin = first.stream().filter(m -> "W".equals(m.getResult())).count();
		if (first.size() >= FIRST_GOAL_MIN && firstLose == 0) {
			add(out, firstWin == first.size() ? "FIRST_GOAL_ALL_WIN" : "FIRST_GOAL_UNBEATEN",
					firstWin == first.size() ? "先制すると全勝" : "先制すると負けない", GOOD, "今季",
					"先制した " + first.size() + "試合 " + firstWin + "勝" + (first.size() - firstWin) + "分");
		}
	}

	// =========================================================
	// リーグ順位・スタイル
	// =========================================================
	private void addRank(List<TeamBadgeDTO> out, Rank def, List<MetricRow> rows, String team) {
		List<MetricRow> valid = rows.stream().filter(r -> r.getMatchCount() >= RANK_MIN_MATCHES).collect(Collectors.toList());
		if (valid.size() < RANK_MIN_TEAMS) {
			return;
		}
		MetricRow mine = null;
		for (MetricRow r : valid) {
			if (norm(r.getTeam()).equals(norm(team))) {
				mine = r;
				break;
			}
		}
		if (mine == null) {
			return;
		}
		BigDecimal best = null;
		for (MetricRow r : valid) {
			if (best == null || (def.high ? r.getValue().compareTo(best) > 0 : r.getValue().compareTo(best) < 0)) {
				best = r.getValue();
			}
		}
		if (best == null) {
			return;
		}
		if (mine.getValue().compareTo(best) != 0) {
			// 2 位以下（labelN があるものだけ。RANK_WITHIN 位まで。2026-11-28 追加）
			if (def.labelN == null) {
				return;
			}
			final BigDecimal mv = mine.getValue();
			long better = valid.stream()
					.filter(r -> def.high ? r.getValue().compareTo(mv) > 0 : r.getValue().compareTo(mv) < 0).count();
			int pos = (int) better + 1;
			if (pos > RANK_WITHIN) {
				return;
			}
			long same = valid.stream().filter(r -> r.getValue().compareTo(mv) == 0).count();
			add(out, def.name(), String.format(def.labelN, pos) + (same > 1 ? "（タイ）" : ""), def.tone, "リーグ順位",
					fmt(mine.getValue()) + " " + def.unit + "（" + valid.size() + "チーム中・" + mine.getMatchCount() + "試合）");
			return;
		}
		final BigDecimal top = best;
		long ties = valid.stream().filter(r -> r.getValue().compareTo(top) == 0).count();
		String label = def.label + (ties > 1 ? "（タイ）" : "");
		add(out, def.name(), label, def.tone, "リーグ順位",
				fmt(mine.getValue()) + " " + def.unit + "（" + valid.size() + "チーム中・" + mine.getMatchCount() + "試合）");
	}

	private void addStyle(List<TeamBadgeDTO> out, String style) {
		if (!blank(style)) {
			add(out, "STYLE", "スタイル: " + style.trim(), INFO, "スタイル", "今季の試合のボール保持・パス・守備の数値から分類");
		}
	}

	// =========================================================
	// 小物
	// =========================================================
	private static void add(List<TeamBadgeDTO> out, String code, String label, String tone, String category, String detail) {
		out.add(new TeamBadgeDTO(code, label, tone, category, detail));
	}

	private static void addNote(List<String> notes, String note) {
		if (!notes.contains(note)) {
			notes.add(note);
		}
	}

	private static String range(int prevRound, int n) {
		return n <= 1 ? "第" + prevRound + "節" : "第" + (prevRound - n + 1) + "〜" + prevRound + "節";
	}

	static Integer parseRound(String label) {
		if (label == null) {
			return null;
		}
		String s = Normalizer.normalize(label, Normalizer.Form.NFKC);
		Matcher m = ROUND.matcher(s);
		if (m.find()) {
			return Integer.valueOf(m.group(1));
		}
		return s.trim().matches("\\d+") ? Integer.valueOf(s.trim()) : null;
	}

	private static String fmt(BigDecimal v) {
		return v.setScale(v.abs().compareTo(BigDecimal.TEN) >= 0 ? 1 : 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
	}

	private static String norm(String s) {
		return s == null ? "" : Normalizer.normalize(s.trim(), Normalizer.Form.NFKC);
	}

	private static String nvl(String s) {
		return s == null ? "" : s;
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}
}
