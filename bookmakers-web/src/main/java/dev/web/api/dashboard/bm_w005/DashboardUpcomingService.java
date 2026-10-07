package dev.web.api.dashboard.bm_w005;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.web.api.dashboard.bm_w002.DashboardForecastService;
import dev.web.api.dashboard.futureDTO.DashboardFutureRow;
import dev.web.api.dashboard.support.DashboardSupport;
import dev.web.repository.master.DashboardFutureRepository;
import lombok.RequiredArgsConstructor;

/**
 * トップ画面のこれからの試合（future_master。今〜{@value #WINDOW_HOURS} 時間後）
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class DashboardUpcomingService {

	/** 何時間後まで */
	static final int WINDOW_HOURS = 36;

	/** 件数（既定・上限） */
	public static final int DEFAULT_LIMIT = 20;
	public static final int MAX_LIMIT = 100;

	private final DashboardFutureRepository futureRepository;

	private final DashboardForecastService forecastService;

	/**
	 * GET /v1/api/dashboard/upcoming
	 */
	public DashboardUpcomingResponse getUpcoming(Integer limit, boolean loggedIn) {
		int l = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
		List<DashboardUpcomingMatchDTO> list = new ArrayList<>();
		for (DashboardFutureRow row : this.futureRepository.findUpcoming(WINDOW_HOURS, l)) {
			list.add(toDTO(row, loggedIn));
		}
		return new DashboardUpcomingResponse(loggedIn, list.size(), list);
	}

	/**
	 * 画面用に変換（未ログインは数値を null）
	 */
	public DashboardUpcomingMatchDTO toDTO(DashboardFutureRow row, boolean loggedIn) {
		String[] cl = DashboardSupport.splitCategory(row.getGameTeamCategory());
		String country = cl == null ? "その他" : cl[0];
		String league = cl == null ? DashboardSupport.norm(row.getGameTeamCategory()) : cl[1];
		DashboardForecastService.Result r = forecast(row);

		DashboardUpcomingMatchDTO dto = new DashboardUpcomingMatchDTO();
		dto.setKickoff(row.getFutureTime());
		dto.setCountry(country);
		dto.setLeague(league);
		dto.setLeagueLabel(DashboardSupport.leagueLabel(country, league));
		dto.setRoundLabel(cl == null || cl[2] == null ? null : "ラウンド " + cl[2]);
		dto.setHome(DashboardForecastService.toTeamDTO(row.getHomeTeamName(), DashboardSupport.toInt(row.getHomeRank()),
				r.homeTeam, loggedIn));
		dto.setAway(DashboardForecastService.toTeamDTO(row.getAwayTeamName(), DashboardSupport.toInt(row.getAwayRank()),
				r.awayTeam, loggedIn));
		dto.setForecast(DashboardForecastService.toForecastDTO(r, loggedIn));
		return dto;
	}

	/** 試合前の見込み */
	public DashboardForecastService.Result forecast(DashboardFutureRow row) {
		String[] cl = DashboardSupport.splitCategory(row.getGameTeamCategory());
		String country = cl == null ? "" : cl[0];
		String league = cl == null ? "" : cl[1];
		return this.forecastService.forecast(country, league, row.getHomeTeamName(), row.getAwayTeamName(),
				0, 0, 1.0, false);
	}
}
