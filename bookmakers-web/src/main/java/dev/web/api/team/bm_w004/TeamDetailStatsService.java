package dev.web.api.team.bm_w004;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.api.dashboard.support.DashboardSupport;
import dev.web.repository.bm.TeamDetailStatsRepository;
import lombok.RequiredArgsConstructor;

/**
 * チームの統計詳細データ（チームページ /team-results の「今季の詳細スタッツ」。2026-11 追加）
 * <ul>
 *   <li>試合数・平均評価点・直近の監督</li>
 *   <li>フォーメーションの使用回数</li>
 *   <li>選手ごとの集計（出場数・出場時間・ゴール・アシスト・平均評価点・xG など）</li>
 * </ul>
 * 統計詳細データが無いチーム（取得していないリーグなど）は matchCount = 0 で、一覧は空。
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class TeamDetailStatsService {

	private final TeamDetailStatsRepository repository;

	@Transactional(readOnly = true)
	public TeamDetailStatsResponse getStats(String country, String league, String team) {
		TeamDetailStatsResponse res = new TeamDetailStatsResponse();
		String c = DashboardSupport.norm(country);
		String l = DashboardSupport.norm(league);
		String t = DashboardSupport.norm(team);
		res.setCountry(c);
		res.setLeague(l);
		res.setTeam(t);
		if (c.isEmpty() || l.isEmpty() || t.isEmpty()) {
			return res;
		}
		res.setSummary(repository.findSummary(c, l, t));
		if (res.getSummary().getMatchCount() == 0) {
			return res;
		}
		res.setFormations(repository.findFormations(c, l, t));
		res.setPlayers(repository.findPlayers(c, l, t));
		return res;
	}
}
