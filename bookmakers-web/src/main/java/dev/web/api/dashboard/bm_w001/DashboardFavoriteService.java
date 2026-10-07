package dev.web.api.dashboard.bm_w001;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import dev.web.api.dashboard.bm_w002.DashboardForecastService;
import dev.web.api.dashboard.bm_w003.DashboardLiveService;
import dev.web.api.dashboard.bm_w005.DashboardUpcomingService;
import dev.web.api.dashboard.futureDTO.DashboardFutureRow;
import dev.web.api.dashboard.support.DashboardSupport;
import dev.web.repository.master.DashboardFutureRepository;
import lombok.RequiredArgsConstructor;

/**
 * トップ画面のお気に入り（ログイン時だけ）
 *
 * <ul>
 *   <li>お気に入りチームがライブ中ならその試合（スコア・経過時間・このままなら勝つ確率）。</li>
 *   <li>ライブでなければ {@value #NEXT_WINDOW_DAYS} 日以内の次の試合（キックオフ・相手・ホーム/アウェー・勝つ確率）。</li>
 *   <li>どちらも無ければ status = NONE。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class DashboardFavoriteService {

	/** 次の試合を探す日数 */
	static final int NEXT_WINDOW_DAYS = 14;

	private final DashboardFavoriteTeamProvider favoriteTeamProvider;

	private final DashboardLiveService liveService;

	private final DashboardUpcomingService upcomingService;

	private final DashboardFutureRepository futureRepository;

	/**
	 * GET /v1/api/dashboard/favorites
	 */
	public DashboardFavoriteResponse getFavorites(String userId) {
		List<DashboardFavoriteTeam> teams = this.favoriteTeamProvider.findByUserId(userId);
		List<DashboardFavoriteItemDTO> items = new ArrayList<>();
		if (teams == null || teams.isEmpty()) {
			return new DashboardFavoriteResponse(items);
		}

		DashboardLiveService.Snapshot snap = this.liveService.snapshot();
		Set<String> names = new LinkedHashSet<>();
		for (DashboardFavoriteTeam t : teams) {
			names.add(DashboardSupport.norm(t.getTeamName()));
		}
		List<DashboardFutureRow> future = this.futureRepository.findUpcomingByTeams(new ArrayList<>(names),
				NEXT_WINDOW_DAYS * 24);

		for (DashboardFavoriteTeam t : teams) {
			String key = DashboardSupport.norm(t.getTeamName());
			DashboardFavoriteItemDTO item = fromLive(t, key, snap);
			if (item == null) {
				item = fromFuture(t, key, future);
			}
			if (item == null) {
				item = new DashboardFavoriteItemDTO();
				item.setTeamName(t.getTeamName());
				item.setCountry(t.getCountry());
				item.setLeague(t.getLeague());
				item.setStatus("NONE");
			}
			items.add(item);
		}
		// ライブ → 次の試合（キックオフ順） → 予定なし
		items.sort((a, b) -> {
			int oa = order(a);
			int ob = order(b);
			if (oa != ob) {
				return Integer.compare(oa, ob);
			}
			String ka = a.getKickoff() == null ? "" : a.getKickoff();
			String kb = b.getKickoff() == null ? "" : b.getKickoff();
			return ka.compareTo(kb);
		});
		return new DashboardFavoriteResponse(items);
	}

	private DashboardFavoriteItemDTO fromLive(DashboardFavoriteTeam t, String key, DashboardLiveService.Snapshot snap) {
		for (DashboardLiveService.LiveItem i : snap.live) {
			boolean home = key.equals(DashboardSupport.norm(i.row.getHomeTeamName()));
			boolean away = key.equals(DashboardSupport.norm(i.row.getAwayTeamName()));
			if (!home && !away) {
				continue;
			}
			DashboardFavoriteItemDTO d = new DashboardFavoriteItemDTO();
			d.setTeamName(home ? i.row.getHomeTeamName() : i.row.getAwayTeamName());
			d.setCountry(i.country);
			d.setLeague(i.league);
			d.setRank(DashboardSupport.toInt(home ? i.row.getHomeRank() : i.row.getAwayRank()));
			d.setStatus("LIVE");
			d.setOpponent(home ? i.row.getAwayTeamName() : i.row.getHomeTeamName());
			d.setHomeAway(home ? "H" : "A");
			d.setMinuteLabel(i.clock.label);
			d.setTeamScore(home ? i.homeScore : i.awayScore);
			d.setOpponentScore(home ? i.awayScore : i.homeScore);
			d.setWinProb((int) Math.round((home ? i.result.home : i.result.away) * 100));
			d.setSeq(i.row.getSeq());
			return d;
		}
		return null;
	}

	private DashboardFavoriteItemDTO fromFuture(DashboardFavoriteTeam t, String key, List<DashboardFutureRow> future) {
		for (DashboardFutureRow f : future) {
			boolean home = key.equals(DashboardSupport.norm(f.getHomeTeamName()));
			boolean away = key.equals(DashboardSupport.norm(f.getAwayTeamName()));
			if (!home && !away) {
				continue;
			}
			DashboardForecastService.Result r = this.upcomingService.forecast(f);
			String[] cl = DashboardSupport.splitCategory(f.getGameTeamCategory());
			DashboardFavoriteItemDTO d = new DashboardFavoriteItemDTO();
			d.setTeamName(home ? f.getHomeTeamName() : f.getAwayTeamName());
			d.setCountry(cl == null ? t.getCountry() : cl[0]);
			d.setLeague(cl == null ? t.getLeague() : cl[1]);
			d.setRank(DashboardSupport.toInt(home ? f.getHomeRank() : f.getAwayRank()));
			d.setStatus("NEXT");
			d.setOpponent(home ? f.getAwayTeamName() : f.getHomeTeamName());
			d.setHomeAway(home ? "H" : "A");
			d.setKickoff(f.getFutureTime());
			d.setWinProb((int) Math.round((home ? r.home : r.away) * 100));
			return d;
		}
		return null;
	}

	private static int order(DashboardFavoriteItemDTO d) {
		return "LIVE".equals(d.getStatus()) ? 0 : "NEXT".equals(d.getStatus()) ? 1 : 2;
	}
}
