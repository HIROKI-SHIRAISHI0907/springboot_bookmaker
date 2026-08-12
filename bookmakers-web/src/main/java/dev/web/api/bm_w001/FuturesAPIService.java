package dev.web.api.bm_w001;

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
import lombok.extern.slf4j.Slf4j;

/**
 * FuturesAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
@AllArgsConstructor
@Slf4j
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

        // delayPostponeJsonData check: [DelayPostponeMatchDto(statusType=POSTPONED,
        // category=ウクライナ: プレミアリーグ, home=ﾁｮﾙﾉﾓﾚﾂ･ｵﾃﾞｯｻ, away=ｺﾛｽ･ｺｳﾞｧﾘﾌｶ,
        // sourceKey=delay_postpone_2026-08-08.json)]
        log.info("delayPostponeJsonData check: {}", delayPostponeJsonData);

        LocalDateTime now = LocalDateTime.now(); // ← システムのデフォルトタイムゾーン
        boolean targetDateIsToday = LocalDate.now(ZoneId.of("Asia/Tokyo")).toString().equals(date); // ← 明示的にJST

        for (FuturesResponseDTO dto : responseDTO) {

        	// =========================
            // 実データ（終了済み/ライブ）を優先して判定する
            //   JSONの延期/遅延情報は「実データがまだ無い試合」に対してのみ適用する
            // =========================

            // 終了済みデータがあれば FINISHED 優先、終了済みデータがなくても今の時間を過ぎていたら終了
            int dataFinCnt = bookDataRepository.countByFinData(
                    dto.getGameTeamCategory(),
                    dto.getHomeTeam(),
                    dto.getAwayTeam());

            log.info("countByFinData check: {},{},{},{}", dto.getGameTeamCategory(),dto.getHomeTeam(),dto.getAwayTeam(),dataFinCnt);

            if (isAfterScheduledTime(dto.getFutureTime(), now) && dataFinCnt > 0) {
                dto.setStatus(FutureScheduleConstant.FINISHED.getCode());
                continue;
            }

            // リアルタイムデータがあれば LIVE
            int dataRealCnt = bookDataRepository.countByLiveData(
                    dto.getGameTeamCategory(),
                    dto.getHomeTeam(),
                    dto.getAwayTeam());

            log.info("countByLiveData check: {},{},{},{}", dto.getGameTeamCategory(),dto.getHomeTeam(),dto.getAwayTeam(),dataRealCnt);

            if (isAfterScheduledTime(dto.getFutureTime(), now) && dataRealCnt > 0) {
                dto.setStatus(FutureScheduleConstant.LIVE.getCode());
                continue;
            }

            // =========================
            // 実データが無い場合のみ、延期/遅延 JSON をチェック
            // =========================
            String delayPostponeData = findDelayPostponeStatus(dto, delayPostponeJsonData);
            if (delayPostponeData != null) {
                dto.setStatus(delayPostponeData);
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
            // ここから先は「開始予定時刻を過ぎたが実データも延期情報も無い」
            // =========================

            // DELAYED は「今日の試合」にだけ付ける
            if (targetDateIsToday) {
                dto.setStatus(FutureScheduleConstant.DELAYED.getCode());
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

        String home = future.getHomeTeam();
        String away = future.getAwayTeam();

        for (DelayPostponeMatchDto dto : delayPostponeJsonData) {
            if (dto == null || !hasText(dto.getStatusType())) {
                continue;
            }

            // ホームチームとアウェーチームが同一キーとしてあるならそのステータスを取得
            if (home.equals(dto.getHome()) && away.equals(dto.getAway())) {
            	return dto.getStatusType();
            }
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

    private boolean isAfterScheduledTime(String futureTime, LocalDateTime now) {
        if (!hasText(futureTime)) {
            return false;
        }

        try {
            return OffsetDateTime.parse(futureTime).toLocalDateTime().isBefore(now);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

}
