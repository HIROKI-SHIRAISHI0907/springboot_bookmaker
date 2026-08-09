package dev.web.api.bm_w001;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.common.constant.FutureScheduleConstant;
import dev.web.repository.bm.BookDataRepository;
import dev.web.repository.bm.LeaguesRepository;
import dev.web.repository.bm.LeaguesRepository.TeamRow;
import dev.web.repository.master.FuturesRepository;
import lombok.AllArgsConstructor;

/**
 * FuturesAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
@AllArgsConstructor
public class FuturesAPIService {

	private final LeaguesRepository leagueRepo;

	private final FuturesRepository futuresRepository;

	private final BookDataRepository bookDataRepository;

    /**
     * 国・リーグ・チーム(slug)から、予定試合（SCHEDULED）一覧を返す
     */
    @Transactional(readOnly = true)
    public List<FuturesResponseDTO> getFutureMatches(String teamEnglish, String teamHash) {
    	TeamRow teamInfo = leagueRepo.findTeamDetailByTeamAndHash(teamEnglish, teamHash);
    	if (teamInfo == null) return null;
        return futuresRepository.findFutureMatches(teamInfo.getCountry(), teamInfo.getLeague(),
        		teamInfo.getTeam());
    }

    /**
     * 管理画面用
     * @param country
     * @param league
     * @param limit
     * @return
     */
    @Transactional(readOnly = true)
    public List<FuturesResponseDTO> getFutureMatchesForAdmin(String country, String league, int limit) {
        return futuresRepository.findFutureMatchesFromNextDay(country, league, limit);
    }

    /**
     * 管理画面用（試合予定データ取得画面）
     * @param date
     * @param limit
     * @return
     */
    @Transactional(readOnly = true)
    public List<FuturesResponseDTO> getFutureMatchesForDate(String date, int offset) {
        List<FuturesResponseDTO> responseDTO = futuresRepository.findFutureMasterByDate(date, offset);

        LocalDateTime now = LocalDateTime.now();

        for (FuturesResponseDTO dto : responseDTO) {
            String currentStatus = dto.getStatus();

            // 明示的に確定しているものは維持
            if (FutureScheduleConstant.FINISHED.equals(currentStatus)
                    || FutureScheduleConstant.POSTPONED.equals(currentStatus)
                    || FutureScheduleConstant.INTERRUPTED.equals(currentStatus)) {
                continue;
            }

            int dataCnt = bookDataRepository.countByLiveData(
                    dto.getGameTeamCategory(),
                    dto.getHomeTeam(),
                    dto.getAwayTeam());

            if (dataCnt > 0) {
                dto.setStatus(FutureScheduleConstant.LIVE);
                continue;
            }

            if (isPastScheduledTime(dto.getFutureTime(), now)) {
                dto.setStatus(FutureScheduleConstant.DELAYED);
            } else {
                dto.setStatus(FutureScheduleConstant.SCHEDULED);
            }
        }

        return responseDTO;
    }

    private boolean isPastScheduledTime(String futureTime, LocalDateTime now) {
        if (futureTime == null || futureTime.isBlank()) {
            return false;
        }

        try {
            return OffsetDateTime.parse(futureTime).toLocalDateTime().isBefore(now);
        } catch (Exception e) {
            return false;
        }
    }

}
