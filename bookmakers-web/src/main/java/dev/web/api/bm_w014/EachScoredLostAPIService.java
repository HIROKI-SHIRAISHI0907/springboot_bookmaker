package dev.web.api.bm_w014;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.bm.BookDataRepository;
import dev.web.repository.bm.LeaguesRepository;
import dev.web.repository.bm.LeaguesRepository.TeamRow;
import dev.web.repository.master.FuturesRepository;
import lombok.AllArgsConstructor;

/**
 * EachScoredLostAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
@AllArgsConstructor
public class EachScoredLostAPIService {

	private final LeaguesRepository leagueRepo;

	private final FuturesRepository futuresRepository;

	private final BookDataRepository bookDataRepository;

    /**
     * 国・リーグ・チーム(slug)から、各チームの試合ごとの得点数失点を返す
     */
	@Transactional(readOnly = true)
	public List<EachScoreLostDataResponseDTO> getEachScoreLoseMatches(String teamEnglish, String teamHash) {
	    TeamRow teamInfo = leagueRepo.findTeamDetailByTeamAndHash(teamEnglish, teamHash);
	    if (teamInfo == null) return null;

	    // ① future_master 側（対象ラウンドの予定一覧）
	    List<FuturesRepository.DataEachScoreLostDataResponseDTO> masterChkList =
	            futuresRepository.findEachScoreLoseMatchesExistsList(
	                    teamInfo.getCountry(),
	                    teamInfo.getLeague(),
	                    teamInfo.getTeam()
	            );
	    if (masterChkList.isEmpty()) return null;

	    // ② masterChkList を 1件ずつ回して、data 側（終了済）を引き当てる
	    List<EachScoreLostDataResponseDTO> out = new java.util.ArrayList<>();

	    for (FuturesRepository.DataEachScoreLostDataResponseDTO m : masterChkList) {
	    	System.out.println(teamInfo);
	    	System.out.println(m);
	        // roundNo が DTO 側で String の場合は int に変換
	        if (m.getRoundNo() == null || m.getRoundNo().isBlank()) continue;

	        int roundNo;
	        try {
	            roundNo = Integer.parseInt(m.getRoundNo());
	        } catch (NumberFormatException e) {
	            continue;
	        }

	        // future_master の home/away で “試合” を特定して data を引く
	        var opt = bookDataRepository.findEachScoreLoseMatchFinishedByRoundAndTeams(
	                teamInfo.getCountry(),
	                teamInfo.getLeague(),
	                m.getHomeTeamName(),
	                m.getAwayTeamName(),
	                roundNo
	        );

	        opt.ifPresent(out::add);
	    }

	    return out;
	}

}
