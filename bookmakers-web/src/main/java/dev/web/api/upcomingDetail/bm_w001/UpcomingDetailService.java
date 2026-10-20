package dev.web.api.upcomingDetail.bm_w001;

import java.text.Normalizer;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.web.api.dashboard.support.DashboardSupport;
import dev.web.api.headToHead.bm_w001.HeadToHeadService;
import dev.web.api.matchDetail.bm_w002.MatchVenueService;
import dev.web.api.upcomingDetail.upcomingDetailDTO.GoalStatsDTO;
import dev.web.api.upcomingDetail.upcomingDetailDTO.StandingRowDTO;
import dev.web.api.upcomingDetail.upcomingDetailDTO.TeamGoalStatsDTO;
import dev.web.repository.bm.UpcomingDetailRepository;
import lombok.RequiredArgsConstructor;

/**
 * これからの試合の詳細（UpcomingDetail.tsx）
 *
 * <ul>
 *   <li>過去の対戦成績: {@link HeadToHeadService}（ホームチーム視点・全シーズン・全大会）</li>
 *   <li>試合会場: ホームチームのホームスタジアム（team_location_master。{@link MatchVenueService#findHomeVenue}）</li>
 *   <li>順位表: surface_overview_match の、試合のリーグの最新シーズン</li>
 *   <li>統計: 同じリーグ・シーズンのチームの試合（全試合 / ホーム時 / アウェー時）。
 *       そこに試合が無ければ（カップ戦・リーグが分からないなど）チームの最後の試合のリーグ・シーズン。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class UpcomingDetailService {

	/** 画面で国が分からないときの表記（DashboardUpcomingService と同じ） */
	private static final String UNKNOWN_COUNTRY = "その他";

	private final HeadToHeadService headToHeadService;

	private final MatchVenueService matchVenueService;

	private final UpcomingDetailRepository repository;

	/**
	 * @throws IllegalArgumentException home・away が空、または同じチームのとき（画面に 400 で返す）
	 */
	public UpcomingDetailResponse get(String country, String league, String home, String away, Integer h2hLimit) {
		String h = trimToNull(home);
		String a = trimToNull(away);
		if (h == null || a == null) {
			throw new IllegalArgumentException("home と away を指定してください。");
		}
		if (norm(h).equals(norm(a))) {
			throw new IllegalArgumentException("home と away に同じチームが指定されています。（" + h + "）");
		}
		String c = trimToNull(country);
		if (c != null && UNKNOWN_COUNTRY.equals(c)) {
			c = null;
		}
		String l = trimToNull(league);

		UpcomingDetailResponse res = new UpcomingDetailResponse();
		res.setCountry(c);
		res.setLeague(l);
		res.setLeagueLabel(c != null && l != null ? DashboardSupport.leagueLabel(c, l) : l);
		res.setHome(h);
		res.setAway(a);

		// 過去の対戦成績（ホームチーム視点）
		res.setHeadToHead(headToHeadService.get(h, a, h2hLimit));

		// 試合会場（ホームチームのホームスタジアム）
		res.setVenue(matchVenueService.findHomeVenue(h, c));

		// 順位表（試合のリーグの最新シーズン）
		String season = c != null && l != null ? repository.findLatestSeason(c, l) : null;
		if (season != null) {
			res.setStandingSeason(season);
			List<StandingRowDTO> rows = repository.findStandings(c, l, season);
			for (StandingRowDTO r : rows) {
				String k = norm(r.getTeam());
				if (k.equals(norm(h))) {
					r.setSide("H");
				} else if (k.equals(norm(a))) {
					r.setSide("A");
				}
			}
			res.setStandings(rows);
		}

		// ゴール数などの統計
		res.setHomeStats(teamStats(h, c, l, season));
		res.setAwayStats(teamStats(a, c, l, season));
		return res;
	}

	/** 試合のリーグ・シーズンで集計。そこに試合が無ければチームの最後の試合のリーグ・シーズン */
	private TeamGoalStatsDTO teamStats(String team, String country, String league, String season) {
		TeamGoalStatsDTO dto = new TeamGoalStatsDTO();
		dto.setTeam(team);
		if (season != null && fill(dto, country, league, season, team)) {
			return dto;
		}
		String[] latest = repository.findTeamLatestLeague(team);
		if (latest != null && fill(dto, latest[1], latest[2], latest[0], team)) {
			dto.setOtherLeague(league == null || !norm(latest[2]).equals(norm(league))
					|| country == null || !norm(latest[1]).equals(norm(country)));
			return dto;
		}
		// データ無し
		dto.setAll(empty("ALL"));
		dto.setHome(empty("HOME"));
		dto.setAway(empty("AWAY"));
		return dto;
	}

	/** 集計して dto に入れる。試合が 1 つも無ければ false */
	private boolean fill(TeamGoalStatsDTO dto, String country, String league, String season, String team) {
		GoalStatsDTO all = null;
		GoalStatsDTO home = null;
		GoalStatsDTO away = null;
		for (GoalStatsDTO s : repository.findGoalStats(country, league, season, team)) {
			if ("ALL".equals(s.getScope())) {
				all = s;
			} else if ("HOME".equals(s.getScope())) {
				home = s;
			} else {
				away = s;
			}
		}
		if (all == null || all.getGames() == 0) {
			return false;
		}
		dto.setSeason(season);
		dto.setCountry(country);
		dto.setLeague(league);
		dto.setAll(all);
		dto.setHome(home == null ? empty("HOME") : home);
		dto.setAway(away == null ? empty("AWAY") : away);
		return true;
	}

	private static GoalStatsDTO empty(String scope) {
		GoalStatsDTO d = new GoalStatsDTO();
		d.setScope(scope);
		return d;
	}

	private static String norm(String s) {
		return s == null ? "" : Normalizer.normalize(s.trim(), Normalizer.Form.NFKC);
	}

	private static String trimToNull(String s) {
		if (s == null) {
			return null;
		}
		String v = s.trim();
		return v.isEmpty() ? null : v;
	}
}
