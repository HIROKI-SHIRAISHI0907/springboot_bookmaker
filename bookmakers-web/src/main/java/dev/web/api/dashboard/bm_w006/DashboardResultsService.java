package dev.web.api.dashboard.bm_w006;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.api.dashboard.bm_w003.DashboardLiveService;
import dev.web.api.dashboard.matchDTO.DashboardMatchRow;
import dev.web.api.dashboard.support.DashboardSupport;
import dev.web.repository.bm.MatchDetailDataWebRepository;
import lombok.RequiredArgsConstructor;

/**
 * トップ画面「本日の試合結果」
 * <ul>
 *   <li>今日（JST）終了した試合（static_data の試合ごとの最新行が「終了済」）を、リーグごとにまとめて返す。
 *       ライブ・数字カードと同じ {@link DashboardLiveService#snapshot()} を使う。</li>
 *   <li>各試合に、統計詳細データ（match_detail_match）があるかを付ける（試合詳細のタブが出るかの目印）。</li>
 *   <li>リーグはラベル順、リーグ内は終わった時刻の新しい順。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class DashboardResultsService {

	private final DashboardLiveService liveService;

	private final MatchDetailDataWebRepository matchDetailDataWebRepository;

	@Transactional(readOnly = true)
	public DashboardResultsResponse getResults() {
		List<DashboardMatchRow> rows = liveService.snapshot().finishedToday;

		Set<String> detailIds = matchDetailDataWebRepository.findRegisteredMatchIds(
				rows.stream().map(DashboardMatchRow::getMatchId).filter(Objects::nonNull)
						.map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()));

		Map<String, DashboardResultsResponse.League> byLabel = new LinkedHashMap<>();
		int detailCount = 0;
		for (DashboardMatchRow r : rows) {
			String[] cl = DashboardSupport.splitCategory(r.getDataCategory());
			String country = cl == null ? "その他" : cl[0];
			String league = cl == null ? DashboardSupport.norm(r.getDataCategory()) : cl[1];
			String label = DashboardSupport.leagueLabel(country, league);

			DashboardResultsResponse.League lg = byLabel.computeIfAbsent(label, k -> {
				DashboardResultsResponse.League x = new DashboardResultsResponse.League();
				x.setLabel(k);
				x.setCountry(country);
				x.setLeague(league);
				return x;
			});

			DashboardResultsResponse.Match m = new DashboardResultsResponse.Match();
			m.setSeqKey(r.getSeqKey());
			m.setMatchId(r.getMatchId());
			m.setRound(cl == null ? null : cl[2]);
			m.setHomeTeam(r.getHomeTeamName());
			m.setAwayTeam(r.getAwayTeamName());
			m.setHomeScore(DashboardSupport.toInt(r.getHomeScore()));
			m.setAwayScore(DashboardSupport.toInt(r.getAwayScore()));
			m.setHalftimeHomeScore(DashboardSupport.toInt(r.getHtHomeScore()));
			m.setHalftimeAwayScore(DashboardSupport.toInt(r.getHtAwayScore()));
			m.setRecordTime(r.getRecordTime());
			String mid = r.getMatchId() == null ? null : r.getMatchId().trim();
			m.setHasDetail(mid != null && detailIds.contains(mid));
			if (m.isHasDetail()) {
				detailCount++;
			}
			lg.getMatches().add(m);
		}

		List<DashboardResultsResponse.League> leagues = new ArrayList<>(byLabel.values());
		leagues.sort(Comparator.comparing(DashboardResultsResponse.League::getLabel));
		for (DashboardResultsResponse.League lg : leagues) {
			lg.getMatches().sort(Comparator.comparing(
					(DashboardResultsResponse.Match m) -> m.getRecordTime() == null ? "" : m.getRecordTime())
					.reversed());
		}

		DashboardResultsResponse res = new DashboardResultsResponse();
		res.setDate(LocalDate.now(DashboardSupport.JST).toString());
		res.setCount(rows.size());
		res.setDetailCount(detailCount);
		res.setLeagues(leagues);
		return res;
	}
}
