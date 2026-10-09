package dev.web.api.team.bm_w001;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import dev.web.api.dashboard.support.DashboardSupport;
import dev.web.repository.bm.TeamResultsRepository;
import lombok.RequiredArgsConstructor;

/**
 * チームの過去の結果（直近 N ラウンド）
 *
 * <ol>
 *   <li>今季（surface_overview_match の国・リーグの最新シーズン）のチームの試合を取る。</li>
 *   <li>static_data の「終了済」の行（今季の最初の試合の {@value #SEASON_MARGIN_DAYS} 日前以降。
 *       今季が分からなければ直近 {@value #FALLBACK_DAYS} 日）も取る。</li>
 *   <li>一番新しいラウンドから N ラウンド分、ラウンドごとに surface_overview_match → static_data の順で埋める。
 *       どちらにも無いラウンドは空欄（source = NONE）。</li>
 * </ol>
 * ラウンド番号が入っている試合だけが対象（ラウンドの無い試合・カップ戦などは出ない）。
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class TeamResultsService {

	/** 既定のラウンド数・上限 */
	public static final int DEFAULT_LIMIT = 5;
	public static final int MAX_LIMIT = 40;

	/** 今季の最初の試合の何日前から static_data を見るか */
	static final int SEASON_MARGIN_DAYS = 14;

	/** 今季が分からないとき static_data を何日前まで見るか */
	static final int FALLBACK_DAYS = 270;

	private final TeamResultsRepository repository;

	/**
	 * GET /v1/api/team-results
	 */
	public TeamResultsResponse getResults(String country, String league, String team, Integer limit) {
		int n = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
		String c = DashboardSupport.norm(country);
		String l = DashboardSupport.norm(league);
		String t = DashboardSupport.norm(team);

		TeamResultsResponse res = new TeamResultsResponse(c, l, t, null, new ArrayList<>(), 0, 0, 0, 0, 0);
		if (c.isEmpty() || l.isEmpty() || t.isEmpty()) {
			return res;
		}

		String season = this.repository.findLatestSeason(c, l);
		res.setSeason(season);

		Map<Integer, TeamResultItemDTO> byRound = new TreeMap<>();
		LocalDateTime from = null;
		if (season != null) {
			for (TeamResultRow r : this.repository.findOverviewResults(c, l, t, season, MAX_LIMIT * 2)) {
				byRound.putIfAbsent(r.getRoundNo(), fromOverview(r));
			}
			LocalDateTime start = this.repository.findSeasonStart(c, l, season);
			if (start != null) {
				from = start.minusDays(SEASON_MARGIN_DAYS);
			}
		}
		if (from == null) {
			from = LocalDateTime.now(DashboardSupport.JST).minusDays(FALLBACK_DAYS);
		}
		for (TeamResultRow r : this.repository.findStaticFinished(c, l, t, from)) {
			byRound.putIfAbsent(r.getRoundNo(), fromStatic(r, t));
		}
		if (byRound.isEmpty()) {
			return res;
		}

		int latest = ((TreeMap<Integer, TeamResultItemDTO>) byRound).lastKey();
		int oldest = Math.max(1, latest - n + 1);
		for (int round = latest; round >= oldest; round--) {
			TeamResultItemDTO item = byRound.get(round);
			if (item == null) {
				item = new TeamResultItemDTO();
				item.setRoundNo(round);
				item.setSource("NONE");
			}
			res.getItems().add(item);
			if ("W".equals(item.getResult())) {
				res.setWins(res.getWins() + 1);
			} else if ("D".equals(item.getResult())) {
				res.setDraws(res.getDraws() + 1);
			} else if ("L".equals(item.getResult())) {
				res.setLosses(res.getLosses() + 1);
			}
			if (item.getGoalsFor() != null && item.getGoalsAgainst() != null) {
				res.setGoalsFor(res.getGoalsFor() + item.getGoalsFor());
				res.setGoalsAgainst(res.getGoalsAgainst() + item.getGoalsAgainst());
			}
		}
		return res;
	}

	/** 直近 n ラウンドの W/D/L（新しい順。分からないラウンドは null）。お気に入りの表示用 */
	public List<String> recentForm(String country, String league, String team, int n) {
		List<String> form = new ArrayList<>();
		for (TeamResultItemDTO i : getResults(country, league, team, n).getItems()) {
			form.add(i.getResult());
		}
		return form;
	}

	private static TeamResultItemDTO fromOverview(TeamResultRow r) {
		TeamResultItemDTO d = new TeamResultItemDTO();
		d.setRoundNo(r.getRoundNo());
		d.setSource("OVERVIEW");
		d.setOpponent(r.getOpponent());
		d.setHomeAway(r.getHa());
		d.setGoalsFor(r.getGoalsFor());
		d.setGoalsAgainst(r.getGoalsAgainst());
		d.setResult(r.getResult());
		d.setPk(r.isPk());
		d.setPkGoalsFor(r.getPkGoalsFor());
		d.setPkGoalsAgainst(r.getPkGoalsAgainst());
		d.setMatchTime(r.getMatchTime());
		return d;
	}

	private static TeamResultItemDTO fromStatic(TeamResultRow r, String team) {
		boolean home = team.equals(DashboardSupport.norm(r.getHomeTeamName()));
		Integer hs = parse(r.getHomeScore());
		Integer as = parse(r.getAwayScore());
		TeamResultItemDTO d = new TeamResultItemDTO();
		d.setRoundNo(r.getRoundNo());
		d.setSource("STATIC");
		d.setOpponent(home ? r.getAwayTeamName() : r.getHomeTeamName());
		d.setHomeAway(home ? "H" : "A");
		d.setGoalsFor(home ? hs : as);
		d.setGoalsAgainst(home ? as : hs);
		if (d.getGoalsFor() != null && d.getGoalsAgainst() != null) {
			int diff = d.getGoalsFor() - d.getGoalsAgainst();
			d.setResult(diff > 0 ? "W" : diff < 0 ? "L" : "D");
		}
		d.setMatchTime(r.getMatchTime());
		return d;
	}

	private static Integer parse(String s) {
		if (s == null || !s.trim().matches("\\d+")) {
			return null;
		}
		return Integer.valueOf(s.trim());
	}
}
