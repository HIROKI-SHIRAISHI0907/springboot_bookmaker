package dev.web.api.bm_w003;

import java.util.List;

import org.apache.coyote.BadRequestException;
import org.apache.ibatis.javassist.NotFoundException;
import org.springframework.stereotype.Service;

import dev.web.repository.bm.OverviewsRepository;

/**
 * OverviewAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
public class OverviewAPIService {

    private final OverviewsRepository overviewsRepository;

    public OverviewAPIService(OverviewsRepository overviewsRepository) {
        this.overviewsRepository = overviewsRepository;
    }

    /**
     * 月次サマリ取得
     * @throws BadRequestException
     * @throws NotFoundException
     */
    public List<OverviewResponseDTO> getMonthlyOverview(
            String country,
            String league,
            String teamSlug
    ) throws BadRequestException, NotFoundException {
        if (isBlank(country) || isBlank(league) || isBlank(teamSlug)) {
            throw new BadRequestException("country, league, team are required");
        }

        String teamJa = overviewsRepository.findTeamJa(country, league, teamSlug);
        if (isBlank(teamJa)) {
            throw new NotFoundException("team not found: " + teamSlug);
        }

        return overviewsRepository.findMonthlyOverview(country, league, teamJa);
    }

    /**
     * 試合概要取得
     * @throws BadRequestException
     * @throws NotFoundException
     */
    public ScheduleOverviewResponse getScheduleOverview(
            String country,
            String league,
            long seq
    ) throws BadRequestException, NotFoundException {

        if (isBlank(country) || isBlank(league) || seq <= 0) {
            throw new BadRequestException("country, league, valid seq are required");
        }

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

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
