package dev.web.api.bm_w001;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

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
        if (teamInfo == null) {
            return null;
        }
        return futuresRepository.findFutureMatches(
                teamInfo.getCountry(),
                teamInfo.getLeague(),
                teamInfo.getTeam());
    }

    /**
     * 管理画面用
     */
    @Transactional(readOnly = true)
    public List<FuturesResponseDTO> getFutureMatchesForAdmin(String country, String league, int limit) {
        return futuresRepository.findFutureMatchesFromNextDay(country, league, limit);
    }

    /**
     * 管理画面用（試合予定データ取得画面）
     */
    @Transactional(readOnly = true)
    public List<FuturesResponseDTO> getFutureMatchesForDate(String date, int offset) {

        List<FuturesResponseDTO> responseDTO = futuresRepository.findFutureMasterByDate(date, offset);

        // JSONファイル読み込み（無ければ空）
        List<DelayPostponeMatchDto> delayPostponeJsonData =
                readDelayPostpone.readAllDelayPostponeMatches(date);

        LocalDateTime now = LocalDateTime.now();
        boolean targetDateIsToday = LocalDate.now(ZoneId.of("Asia/Tokyo")).toString().equals(date);

        for (FuturesResponseDTO dto : responseDTO) {

            String currentStatus = trim(dto.getStatus());

            // 明示的に確定済みは維持
            if (FutureScheduleConstant.FINISHED.is(currentStatus)
                    || FutureScheduleConstant.INTERRUPTED.is(currentStatus)
                    || FutureScheduleConstant.POSTPONED.is(currentStatus)) {
                continue;
            }

            // =========================
            // 延期 / 遅延 JSON チェック
            // =========================
            String delayPostponeStatus = findDelayPostponeStatus(dto, delayPostponeJsonData);
            if (delayPostponeStatus != null) {
                dto.setStatus(delayPostponeStatus);
                continue;
            }

            // =========================
            // 終了済みデータがあれば FINISHED 優先
            // =========================
            int dataFinCnt = bookDataRepository.countByFinData(
                    dto.getGameTeamCategory(),
                    dto.getHomeTeam(),
                    dto.getAwayTeam());

            if (dataFinCnt > 0) {
                dto.setStatus(FutureScheduleConstant.FINISHED.getCode());
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

            // リアルタイムデータがあれば LIVE
            if (dataRealCnt > 0) {
                dto.setStatus(FutureScheduleConstant.LIVE.getCode());
                continue;
            }

            // DELAYED は「今日の試合」にだけ付ける
            if (targetDateIsToday) {
                dto.setStatus(FutureScheduleConstant.DELAYED.getCode());
            } else {
                // 今日以外は安易に DELAYED にしない
                if (hasText(currentStatus)) {
                    dto.setStatus(currentStatus);
                } else {
                    dto.setStatus(FutureScheduleConstant.SCHEDULED.getCode());
                }
            }
        }

        return responseDTO;
    }

    /**
     * JSON の延期/遅延情報を、チーム名一致 + カテゴリゆるめ一致で探す
     */
    private String findDelayPostponeStatus(
            FuturesResponseDTO future,
            List<DelayPostponeMatchDto> delayPostponeJsonData) {

        if (delayPostponeJsonData == null || delayPostponeJsonData.isEmpty()) {
            return null;
        }

        String futureCategory = future.getGameTeamCategory();
        String futureHome = future.getHomeTeam();
        String futureAway = future.getAwayTeam();

        for (DelayPostponeMatchDto dto : delayPostponeJsonData) {
            if (dto == null || !hasText(dto.getStatusType())) {
                continue;
            }

            if (!isSameTeamName(futureHome, dto.getHome())) {
                continue;
            }

            if (!isSameTeamName(futureAway, dto.getAway())) {
                continue;
            }

            if (!isEquivalentCategory(futureCategory, dto.getCategory())) {
                continue;
            }

            return dto.getStatusType();
        }

        return null;
    }

    private boolean isBeforeScheduledTime(String futureTime, LocalDateTime now) {
        if (!hasText(futureTime)) {
            return false;
        }

        try {
            return OffsetDateTime.parse(futureTime).toLocalDateTime().isAfter(now);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isSameTeamName(String a, String b) {
        return normalizeMatchKeyPart(a).equals(normalizeMatchKeyPart(b));
    }

    /**
     * category のゆるめ一致
     * 例:
     * - ウクライナ: プレミアリーグ
     * - ウクライナ: プレミアリーグ Round 1
     * - ウクライナ: プレミアリーグ ラウンド1
     */
    private boolean isEquivalentCategory(String a, String b) {
        String na = normalizeCategory(a);
        String nb = normalizeCategory(b);

        if (!hasText(na) || !hasText(nb)) {
            return false;
        }

        return na.equals(nb) || na.contains(nb) || nb.contains(na);
    }

    private String normalizeCategory(String value) {
        String normalized = normalizeMatchKeyPart(value);

        // Round / ラウンド以降を落とす
        normalized = normalized.replaceAll("(?i)round\\d+.*$", "");
        normalized = normalized.replaceAll("ラウンド\\d+.*$", "");

        return normalized.trim();
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
