package dev.web.api.dashboard.bm_w005;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import dev.web.api.dashboard.bm_w002.DashboardForecastService;
import dev.web.api.dashboard.futureDTO.DashboardFutureRow;
import dev.web.api.dashboard.support.DashboardSupport;
import dev.web.repository.master.DashboardFutureRepository;
import lombok.RequiredArgsConstructor;

/**
 * これからの試合（future_master）
 *
 * <ul>
 *   <li>{@link #getTop}: トップ画面用。リーグごとに直近 {@value #DEFAULT_PER_LEAGUE} 試合、
 *       最初の試合が早いリーグから最大 {@value #DEFAULT_MAX_LEAGUES} リーグ（今〜{@value #WINDOW_HOURS} 時間後）。</li>
 *   <li>{@link #getAll}: 全試合画面（UpcomingMatches.tsx）用。期間内の全試合をリーグごとに。</li>
 *   <li>取得に失敗した行（カテゴリ「XXX: YYY」・チーム名が空など）は出さない。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class DashboardUpcomingService {

	/** トップ画面: 何時間後まで（今日・明日） */
	public static final int WINDOW_HOURS = 36;

	/** 全試合画面: 何時間後まで（上限） */
	public static final int MAX_HOURS = 24 * 7;

	/** トップ画面: リーグごとの件数 */
	public static final int DEFAULT_PER_LEAGUE = 3;

	/** トップ画面: リーグ数（0 以下なら全リーグ） */
	public static final int DEFAULT_MAX_LEAGUES = 5;

	/** 1回に読む最大件数（future_master が想定外に多いときの保険） */
	private static final int FETCH_CAP = 2000;

	private final DashboardFutureRepository futureRepository;

	private final DashboardForecastService forecastService;

	/**
	 * GET /v1/api/dashboard/upcoming（トップ画面）
	 *
	 * @param perLeague リーグごとの件数（null・0 以下なら 3）
	 * @param maxLeagues リーグ数（null なら 5、0 以下なら全リーグ）
	 */
	public DashboardUpcomingResponse getTop(Integer perLeague, Integer maxLeagues, boolean loggedIn) {
		int per = (perLeague == null || perLeague <= 0) ? DEFAULT_PER_LEAGUE : Math.min(perLeague, 20);
		int maxL = maxLeagues == null ? DEFAULT_MAX_LEAGUES : maxLeagues;
		return build(WINDOW_HOURS, per, maxL, null, loggedIn);
	}

	/**
	 * GET /v1/api/upcoming-matches（全試合画面）
	 *
	 * @param hours 何時間後まで（null なら 36、最大 168）
	 * @param leagueLabel 「国 / リーグ」で絞る（null・空なら全部）
	 */
	public DashboardUpcomingResponse getAll(Integer hours, String leagueLabel, boolean loggedIn) {
		int h = (hours == null || hours <= 0) ? WINDOW_HOURS : Math.min(hours, MAX_HOURS);
		String filter = leagueLabel == null || leagueLabel.isBlank() ? null : DashboardSupport.norm(leagueLabel);
		return build(h, 0, 0, filter, loggedIn);
	}

	/**
	 * 期間内の試合をリーグごとにまとめる
	 *
	 * @param per リーグごとの件数（0 以下なら全部）
	 * @param maxLeagues リーグ数（0 以下なら全部）
	 * @param leagueFilter 「国 / リーグ」（正規化済み。null なら全部）
	 */
	private DashboardUpcomingResponse build(int hours, int per, int maxLeagues, String leagueFilter,
			boolean loggedIn) {
		// リーグごと（キックオフ順に読むので、最初の試合が早いリーグから並ぶ）
		Map<String, List<DashboardFutureRow>> byLeague = new LinkedHashMap<>();
		int total = 0;
		for (DashboardFutureRow row : this.futureRepository.findUpcoming(hours, FETCH_CAP)) {
			// 試合前の future_master は国が無いカテゴリ（「リーグ - ラウンド N」）もあるので、Upcoming 用の判定を使う
			if (!DashboardSupport.isDisplayableUpcoming(row.getGameTeamCategory(), row.getHomeTeamName(),
					row.getAwayTeamName())) {
				continue;
			}
			String label = leagueLabelOf(row);
			if (leagueFilter != null && !leagueFilter.equals(DashboardSupport.norm(label))) {
				continue;
			}
			byLeague.computeIfAbsent(label, k -> new ArrayList<>()).add(row);
			total++;
		}

		List<DashboardUpcomingLeagueDTO> leagues = new ArrayList<>();
		List<DashboardUpcomingMatchDTO> flat = new ArrayList<>();
		for (Map.Entry<String, List<DashboardFutureRow>> e : byLeague.entrySet()) {
			if (maxLeagues > 0 && leagues.size() >= maxLeagues) {
				break;
			}
			List<DashboardFutureRow> rows = e.getValue();
			List<DashboardFutureRow> shown = per > 0 && rows.size() > per ? rows.subList(0, per) : rows;
			List<DashboardUpcomingMatchDTO> matches = new ArrayList<>(shown.size());
			for (DashboardFutureRow row : shown) {
				matches.add(toDTO(row, loggedIn));
			}
			DashboardUpcomingMatchDTO first = matches.get(0);
			leagues.add(new DashboardUpcomingLeagueDTO(e.getKey(), first.getCountry(), first.getLeague(), rows.size(),
					matches));
			flat.addAll(matches);
		}
		flat.sort(Comparator.comparing(DashboardUpcomingMatchDTO::getKickoff,
				Comparator.nullsLast(Comparator.naturalOrder())));

		return new DashboardUpcomingResponse(loggedIn, flat.size(), total, byLeague.size(), hours, flat, leagues);
	}

	/** カテゴリを {国, リーグ, ラウンド} に（国が無ければ country_league_master のチームの国、それも無ければ「その他」） */
	private static String[] categoryOf(DashboardFutureRow row) {
		return DashboardSupport.splitCategory(row.getGameTeamCategory(), row.getCountryHint());
	}

	private static String leagueLabelOf(DashboardFutureRow row) {
		String[] cl = categoryOf(row);
		String country = cl == null ? "その他" : cl[0];
		String league = cl == null ? DashboardSupport.norm(row.getGameTeamCategory()) : cl[1];
		return DashboardSupport.leagueLabel(country, league);
	}

	/**
	 * 画面用に変換（未ログインは数値を null）
	 */
	public DashboardUpcomingMatchDTO toDTO(DashboardFutureRow row, boolean loggedIn) {
		String[] cl = categoryOf(row);
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
		String[] cl = categoryOf(row);
		String country = cl == null ? "" : cl[0];
		String league = cl == null ? "" : cl[1];
		return this.forecastService.forecast(country, league, row.getHomeTeamName(), row.getAwayTeamName(),
				0, 0, 1.0, false);
	}
}
