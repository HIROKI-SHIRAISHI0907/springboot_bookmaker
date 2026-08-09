package dev.web.api.bm_w001;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.common.constant.FutureScheduleConstant;
import dev.common.readfile.ReadDelayPostpone;
import dev.common.readfile.dto.DelayPostponeMatchDto;
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

	private final ReadDelayPostpone readDelayPostpone;

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

        // JSONファイル読み込み
        List<DelayPostponeMatchDto> delayPostponeJsonData =
                readDelayPostpone.readAllDelayPostponeMatches(date);

        // JSON検索しやすいように Map 化
        Map<String, String> delayPostponeMap =
                buildDelayPostponeStatusMap(delayPostponeJsonData);

        LocalDateTime now = LocalDateTime.now();

        for (FuturesResponseDTO dto : responseDTO) {

            String currentStatus = trim(dto.getStatus());

            // 明示的に固定したいステータスがあれば最初に維持
            // POSTPONED をDB優先で固定したい場合はここに追加
            if (FutureScheduleConstant.FINISHED.is(currentStatus)
                    || FutureScheduleConstant.INTERRUPTED.is(currentStatus)) {
                continue;
            }

            // =========================
            // 延期 / 遅延 JSON チェック
            // =========================
            String delayPostponeKey = buildDelayPostponeKey(
                    dto.getGameTeamCategory(),
                    dto.getHomeTeam(),
                    dto.getAwayTeam());

            String delayPostponeStatus = delayPostponeMap.get(delayPostponeKey);
            if (delayPostponeStatus != null) {
                dto.setStatus(delayPostponeStatus);
                continue;
            }

            // =========================
            // まだ試合開始前なら SCHEDULED
            // =========================
            if (isBeforeScheduledTime(dto.getFutureTime(), now)) {
                dto.setStatus(FutureScheduleConstant.SCHEDULED.getCode());
                continue;
            }

            // =========================
            // ここから先は「開始予定時刻を過ぎた」前提
            // =========================
            int dataRealCnt = bookDataRepository.countByLiveData(
                    dto.getGameTeamCategory(),
                    dto.getHomeTeam(),
                    dto.getAwayTeam());

            int dataFinCnt = bookDataRepository.countByFinData(
                    dto.getGameTeamCategory(),
                    dto.getHomeTeam(),
                    dto.getAwayTeam());

            // 終了済みデータがあれば FINISHED を優先
            if (dataFinCnt > 0) {
                dto.setStatus(FutureScheduleConstant.FINISHED.getCode());
                continue;
            }

            // リアルタイムデータがあれば LIVE
            if (dataRealCnt > 0) {
                dto.setStatus(FutureScheduleConstant.LIVE.getCode());
                continue;
            }

            // 予定時刻は過ぎたがデータが無い -> DELAYED
            dto.setStatus(FutureScheduleConstant.DELAYED.getCode());
        }

        return responseDTO;
    }

    private boolean isBeforeScheduledTime(String futureTime, LocalDateTime now) {
        if (futureTime == null || futureTime.trim().isEmpty()) {
            return false;
        }

        try {
            return OffsetDateTime.parse(futureTime).toLocalDateTime().isAfter(now);
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, String> buildDelayPostponeStatusMap(
            List<DelayPostponeMatchDto> delayPostponeJsonData) {

        Map<String, String> result = new HashMap<String, String>();

        if (delayPostponeJsonData == null || delayPostponeJsonData.isEmpty()) {
            return result;
        }

        for (DelayPostponeMatchDto dto : delayPostponeJsonData) {
            if (dto == null || dto.getStatusType() == null) {
                continue;
            }

            String key = buildDelayPostponeKey(
                    dto.getCategory(),
                    dto.getHome(),
                    dto.getAway());

            // 後勝ちにしたい場合は put のままでOK
            // 先勝ちにしたい場合は containsKey チェックを入れる
            result.put(key, dto.getStatusType());
        }

        return result;
    }

    private String buildDelayPostponeKey(String category, String home, String away) {
        return normalizeMatchKeyPart(category)
                + "|"
                + normalizeMatchKeyPart(home)
                + "|"
                + normalizeMatchKeyPart(away);
    }

    private String normalizeMatchKeyPart(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        normalized = normalized.replace('\u3000', ' ');
        normalized = normalized.replaceAll("\\s+", "");
        return normalized.trim();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

}
