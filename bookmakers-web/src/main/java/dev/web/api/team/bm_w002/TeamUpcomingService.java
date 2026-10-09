package dev.web.api.team.bm_w002;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.web.api.dashboard.bm_w005.DashboardUpcomingMatchDTO;
import dev.web.api.dashboard.bm_w005.DashboardUpcomingService;
import dev.web.api.dashboard.futureDTO.DashboardFutureRow;
import dev.web.api.dashboard.support.DashboardSupport;
import dev.web.api.dashboard.teamDTO.TeamUpcomingItemDTO;
import dev.web.api.team.bm_w001.TeamResultsService;
import dev.web.repository.master.DashboardFutureRepository;
import lombok.RequiredArgsConstructor;

/**
 * チームのこれからの試合（future_master）。チームの結果ページ（TeamResults.tsx）の右側用。
 * <ul>
 *   <li>今から {@value #DEFAULT_DAYS} 日以内の、このチームの試合をキックオフ順に最大 limit 件。</li>
 *   <li>試合の形・見込みはトップ画面の「これからの試合」と同じ（{@link DashboardUpcomingService#toDTO}）。</li>
 *   <li>チームの国・リーグが分かっている行は、指定の国と違う試合（同名の別チーム）を除く。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class TeamUpcomingService {

	static final int DEFAULT_DAYS = 30;

	static final int DEFAULT_LIMIT = 3;

	static final int MAX_LIMIT = 10;

	static final int FORM_SIZE = 5;

	private final DashboardFutureRepository futureRepository;

	private final DashboardUpcomingService upcomingService;

	private final TeamResultsService teamResultsService;

	public TeamUpcomingResponse getUpcoming(String country, String league, String team, Integer limit, boolean loggedIn) {
		int lim = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
		String key = DashboardSupport.norm(team);

		TeamUpcomingResponse res = new TeamUpcomingResponse();
		res.setCountry(country);
		res.setLeague(league);
		res.setTeam(team);
		res.setLoggedIn(loggedIn);
		res.setDays(DEFAULT_DAYS);

		List<TeamUpcomingItemDTO> items = new ArrayList<>();
		for (DashboardFutureRow row : this.futureRepository.findUpcomingByTeams(List.of(key), DEFAULT_DAYS * 24)) {
			if (items.size() >= lim) {
				break;
			}
			boolean home = key.equals(DashboardSupport.norm(row.getHomeTeamName()));
			boolean away = key.equals(DashboardSupport.norm(row.getAwayTeamName()));
			if (!home && !away) {
				continue;
			}
			DashboardUpcomingMatchDTO m = this.upcomingService.toDTO(row, loggedIn);
			// 国が分かっていて違う → 同名の別チームなので除く
			if (country != null && !country.isBlank() && m.getCountry() != null
					&& !"その他".equals(m.getCountry()) && !country.equals(m.getCountry())) {
				continue;
			}
			String opponent = home ? row.getAwayTeamName() : row.getHomeTeamName();

			TeamUpcomingItemDTO item = new TeamUpcomingItemDTO();
			item.setMatch(m);
			item.setHomeAway(home ? "H" : "A");
			item.setOpponent(opponent);
			item.setOpponentForm(this.teamResultsService.recentForm(country, league, opponent, FORM_SIZE));
			if (loggedIn && m.getForecast() != null) {
				item.setWinProb(home ? m.getForecast().getProbHome() : m.getForecast().getProbAway());
			}
			items.add(item);
		}
		res.setItems(items);
		return res;
	}
}
