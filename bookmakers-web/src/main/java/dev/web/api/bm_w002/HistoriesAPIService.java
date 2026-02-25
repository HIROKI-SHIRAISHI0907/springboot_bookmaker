package dev.web.api.bm_w002;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.bm.HistoriesRepository;
import dev.web.repository.bm.LeaguesRepository;
import dev.web.repository.bm.LeaguesRepository.TeamRow;
import dev.web.repository.master.FuturesRepository;
import lombok.AllArgsConstructor;

/**
 * HistoriesAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
@AllArgsConstructor
public class HistoriesAPIService {

	private final LeaguesRepository leagueRepo;

	private final FuturesRepository futuresRepository;

    private final HistoriesRepository historyRepository;

    /**
     * 過去試合一覧（teamSlug→teamJa解決込み）
     */
    @Transactional(readOnly = true)
    public List<HistoryResponseDTO> listHistory(String teamEnglish, String teamHash) {
	    TeamRow teamInfo = leagueRepo.findTeamDetailByTeamAndHash(teamEnglish, teamHash);
	    if (teamInfo == null) return null;
	    String country = teamInfo.getCountry();
	    String league = teamInfo.getLeague();
	    String teamJa = teamInfo.getTeam();
	    List<HistoryResponseDTO> rows = historyRepository.findPastMatches(
	    		country, league, teamJa);

	    List<String> links = rows.stream()
	        .map(HistoryResponseDTO::getLink)
	        .filter(s -> s != null && !s.isBlank())
	        .distinct()
	        .collect(Collectors.toList());

	    Map<String, String> kickoffByLink = futuresRepository.findFutureTimeByGameLinks(
	    		country, league, links);

	    for (HistoryResponseDTO r : rows) {
	        String link = r.getLink();
	        String kickoff = link == null ? null : kickoffByLink.get(link);
	        if (kickoff != null) {
	            // ★ これでフロント表示が future_time 基準になる
	            r.setMatchTime(kickoff);
	        }
	    }
	    return rows;

    }

    /**
     * 試合詳細（seqで取得）
     */
    @Transactional(readOnly = true)
    public Optional<HistoryDetailResponseDTO> getHistoryDetail(String country, String league, long seq) {
        return historyRepository.findHistoryDetail(country, league, seq);
    }
}
