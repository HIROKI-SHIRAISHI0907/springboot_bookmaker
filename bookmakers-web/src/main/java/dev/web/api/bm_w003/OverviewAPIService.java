package dev.web.api.bm_w003;

import java.util.List;

import org.apache.coyote.BadRequestException;
import org.apache.ibatis.javassist.NotFoundException;
import org.springframework.stereotype.Service;

import dev.web.repository.bm.LeaguesRepository;
import dev.web.repository.bm.LeaguesRepository.TeamRow;
import dev.web.repository.bm.OverviewsRepository;
import lombok.RequiredArgsConstructor;

/**
 * OverviewAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class OverviewAPIService {

	private final LeaguesRepository leagueRepo;

    private final OverviewsRepository overviewsRepository;

    /**
     * 月次サマリ取得
     * @throws BadRequestException
     * @throws NotFoundException
     */
    public List<OverviewResponse> getMonthlyOverview(
    		String teamEnglish, String teamHash
    ) throws BadRequestException, NotFoundException {
        if (isBlank(teamEnglish) || isBlank(teamHash)) {
            throw new BadRequestException("country, league, team are required");
        }

        TeamRow teamInfo = leagueRepo.findTeamDetailByTeamAndHash(teamEnglish, teamHash);
        if (teamInfo == null) return null;
        return overviewsRepository.findMonthlyOverview(teamInfo.getCountry(),
        		teamInfo.getLeague(), teamInfo.getTeam());
    }

    /**
     * 試合概要取得
     * @throws BadRequestException
     * @throws NotFoundException
     */
    public ScheduleOverviewResponse getScheduleOverview(
    		String teamEnglish, String teamHash,
            long seq
    ) throws BadRequestException, NotFoundException {

        if (isBlank(teamEnglish) || isBlank(teamHash) || seq <= 0) {
            throw new BadRequestException("teamEnglish, teamHash, valid seq are required");
        }

        TeamRow teamInfo = leagueRepo.findTeamDetailByTeamAndHash(teamEnglish, teamHash);
    	if (teamInfo == null) return null;

    	String country = teamInfo.getCountry();
    	String league = teamInfo.getLeague();

        // ① 試合取得
        ScheduleMatchDTO match =
                overviewsRepository.findMatch(country, league, seq);

        if (match == null) {
            throw new NotFoundException("match not found: seq=" + seq);
        }

        // ② surface取得
        List<SurfaceSnapshotDTO> surfaces =
                overviewsRepository.findSurfacesForMatch(
                        country,
                        league,
                        match.getGameYear(),
                        match.getGameMonth(),
                        match.getHomeTeam(),
                        match.getAwayTeam()
                );

        // ★ 既存 Wrapper をそのまま返す
        return new ScheduleOverviewResponse(match, surfaces);
    }

    /**
     * 統計サマリ取得
     * @throws BadRequestException
     * @throws NotFoundException
     */
    public List<OverviewSummaryDTO> getOverviewSummary(
    		String teamEnglish, String teamHash
    ) throws BadRequestException, NotFoundException {
        if (isBlank(teamEnglish) || isBlank(teamHash)) {
            throw new BadRequestException("country, league, team are required");
        }

        TeamRow teamInfo = leagueRepo.findTeamDetailByTeamAndHash(teamEnglish, teamHash);
        if (teamInfo == null) return null;
        return overviewsRepository.findOverviewSummary(teamInfo.getCountry(),
        		teamInfo.getLeague(), teamInfo.getTeam());
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
