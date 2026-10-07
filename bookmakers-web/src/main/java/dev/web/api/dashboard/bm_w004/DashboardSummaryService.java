package dev.web.api.dashboard.bm_w004;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import dev.web.api.dashboard.bm_w003.DashboardLiveService;
import dev.web.api.dashboard.futureDTO.DashboardFutureRow;
import dev.web.api.dashboard.matchDTO.DashboardMatchRow;
import dev.web.api.dashboard.support.DashboardSupport;
import dev.web.repository.master.DashboardFutureRepository;
import lombok.RequiredArgsConstructor;

/**
 * トップ画面の数字カード（ライブ数・今日のゴール・これから・終了）
 *
 * <ul>
 *   <li>番狂わせ: 順位が {@value #UPSET_RANK_GAP} 以上下のチームが勝った試合（今日終了した試合）。</li>
 *   <li>未ログインでも同じ内容（個人に関係する値・見込みの数値は含まない）。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class DashboardSummaryService {

	/** 番狂わせとみなす順位差 */
	static final int UPSET_RANK_GAP = 5;

	private final DashboardLiveService liveService;

	private final DashboardFutureRepository futureRepository;

	/**
	 * GET /v1/api/dashboard/summary
	 */
	public DashboardSummaryResponse getSummary() {
		DashboardLiveService.Snapshot snap = this.liveService.snapshot();
		DashboardSummaryResponse res = new DashboardSummaryResponse();

		Set<String> leagues = new HashSet<>();
		int goals = 0;
		int highGoal = 0;
		for (DashboardLiveService.LiveItem i : snap.live) {
			leagues.add(i.country + "|" + i.league);
			goals += i.homeScore + i.awayScore;
			if (i.result.expectedTotal >= 3.0) {
				highGoal++;
			}
		}
		int upsets = 0;
		int draws = 0;
		for (DashboardMatchRow r : snap.finishedToday) {
			Integer hs = DashboardSupport.toInt(r.getHomeScore());
			Integer as = DashboardSupport.toInt(r.getAwayScore());
			if (hs == null || as == null) {
				continue;
			}
			goals += hs + as;
			if (hs.equals(as)) {
				draws++;
				continue;
			}
			Integer hr = DashboardSupport.toInt(r.getHomeRank());
			Integer ar = DashboardSupport.toInt(r.getAwayRank());
			if (hr != null && ar != null) {
				int winnerRank = hs > as ? hr : ar;
				int loserRank = hs > as ? ar : hr;
				if (winnerRank - loserRank >= UPSET_RANK_GAP) {
					upsets++;
				}
			}
		}
		int matchesToday = snap.live.size() + snap.finishedToday.size();

		res.setLiveCount(snap.live.size());
		res.setLiveLeagueCount(leagues.size());
		res.setHighGoalLiveCount(highGoal);
		res.setGoalsToday(goals);
		res.setAvgGoalsToday(matchesToday == 0 ? null : Math.round(goals * 10.0 / matchesToday) / 10.0);
		res.setFinishedCount(snap.finishedToday.size());
		res.setUpsetCount(upsets);
		res.setDrawCount(draws);

		// 今日このあとの試合（JST の 24 時まで）
		LocalDateTime now = LocalDateTime.now(DashboardSupport.JST);
		int hours = (int) Math.max(1, Math.ceil(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMinutes() / 60.0));
		List<DashboardFutureRow> upcoming = this.futureRepository.findUpcoming(hours, 500);
		res.setUpcomingTodayCount(upcoming.size());
		if (!upcoming.isEmpty()) {
			DashboardFutureRow next = upcoming.get(0);
			res.setNextKickoff(next.getFutureTime());
			res.setNextHomeTeam(next.getHomeTeamName());
			res.setNextAwayTeam(next.getAwayTeamName());
		}
		return res;
	}
}
