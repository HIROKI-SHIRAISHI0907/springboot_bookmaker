package dev.web.api.bm_w001;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.common.enums.FutureScheduleEnum;
import dev.common.readfile.ReadDelayPostpone;
import dev.common.readfile.dto.DelayPostponeMatchDto;
import dev.common.util.DateOffsetDecisionUtil;
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

    /**
     * 「直近この時間内にrecord_timeが更新されていなければライブとはみなさない」しきい値
     * データ提供元(バッチ)の更新頻度に応じて調整してください（暫定30分）
     */
    private static final Duration LIVE_DATA_FRESHNESS_WINDOW = Duration.ofMinutes(30);

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

        log.info("responseDTO check: {}", responseDTO);

        // JSONファイル読み込み（無ければ空）
        List<DelayPostponeMatchDto> delayPostponeJsonData =
                readDelayPostpone.readAllDelayPostponeMatches(date);

        log.info("delayPostponeJsonData check: {}", delayPostponeJsonData);

        LocalDateTime now = LocalDateTime.now(DateOffsetDecisionUtil.getZoneId()); // ← JST
        boolean targetDateIsToday = LocalDate.now(DateOffsetDecisionUtil.getZoneId()).toString().equals(date); // ← 明示的にJST

        for (FuturesResponseDTO dto : responseDTO) {

            // =========================
            // 実データ（終了済み/ライブ）を優先して判定する
            //   JSONの延期/遅延情報は「実データがまだ無い試合」に対してのみ適用する
            // =========================

            // 終了済みデータ件数(表記ブレを防ぐためdataCategoryは検索から無視)
            int dataFinCnt = bookDataRepository.countByFinData(
                    dto.getHomeTeam(),
                    dto.getAwayTeam());

            // システム上「ライブ扱い」のデータ件数(鮮度不問。参考値・ログ用)
            int dataRealCnt = bookDataRepository.countByLiveData(
                    dto.getHomeTeam(),
                    dto.getAwayTeam());

            // 直近(LIVE_DATA_FRESHNESS_WINDOW以内)にrecord_timeが更新されているデータ件数・最終更新時刻
            BookDataRepository.CurrentLiveDataResult currentLive = bookDataRepository.findCurrentLiveData(
                    dto.getHomeTeam(),
                    dto.getAwayTeam(),
                    LIVE_DATA_FRESHNESS_WINDOW);

            // --- システムデータ側の内訳をDTOにセット ---
            FuturesResponseDTO.SystemDataStatus systemData = new FuturesResponseDTO.SystemDataStatus();
            systemData.setFinishedCount(dataFinCnt);
            systemData.setLiveCount(dataRealCnt);
            dto.setSystemData(systemData);

            // --- リアルタイムデータ側の内訳をDTOにセット(record_time基準) ---
            FuturesResponseDTO.RealtimeDataStatus realtimeData = new FuturesResponseDTO.RealtimeDataStatus();
            realtimeData.setCurrentLiveCount(currentLive.count);
            realtimeData.setLastUpdatedAt(
            	    currentLive.lastUpdatedAt != null ? currentLive.lastUpdatedAt.toString() : null
            	);
            dto.setRealtimeData(realtimeData);

            log.info("data check: {},{},{},fin={},sysLive={},rtLive={},lastUpdated={}",
                    dto.getGameTeamCategory(), dto.getHomeTeam(), dto.getAwayTeam(),
                    dataFinCnt, dataRealCnt, currentLive.count, realtimeData.getLastUpdatedAt());

            // 終了済みデータがあれば FINISHED 優先
            if (isAfterScheduledTime(dto.getFutureTime(), now) && systemData.getFinishedCount() > 0) {
                dto.setStatus(FutureScheduleEnum.FINISHED.getCode());
                continue;
            }

            // 直近で実際に更新され続けているデータがあれば LIVE
            if (isAfterScheduledTime(dto.getFutureTime(), now) && realtimeData.getCurrentLiveCount() > 0) {
                dto.setStatus(FutureScheduleEnum.LIVE.getCode());
                log.info("isAfterScheduledTime check: {},{},{},{}", dto.getGameTeamCategory(), dto.getHomeTeam(), dto.getAwayTeam(), "LIVE");
                continue;
            }

            // =========================
            // 実データが無い(もしくは古いデータしか無い)場合のみ、延期/遅延 JSON をチェック
            //   ※公式に延期/遅延/中断と判明しているデータがあれば、それを最優先で信用する
            // =========================
            String delayPostponeData = findDelayPostponeStatus(dto, delayPostponeJsonData);
            if (delayPostponeData != null) {
                dto.setStatus(delayPostponeData);
                continue;
            }

            // =========================
            // 開始予定時刻は過ぎているが、終了済データも直近の更新データも無いケース
            //   システムデータ上に非終了状態のデータ(systemData.liveCount)が過去に存在するなら、
            //   実際には試合が行われた（＝延期ではない）可能性が高く、単にバッチが
            //   「終了済」フラグを立てないまま更新を止めているだけと考えられる。
            //   これを「遅延」と区別するため STALLED（更新停止）とする。
            // =========================
            if (isAfterScheduledTime(dto.getFutureTime(), now)
                    && systemData.getFinishedCount() == 0
                    && realtimeData.getCurrentLiveCount() == 0
                    && systemData.getLiveCount() > 0) {
                dto.setStatus(FutureScheduleEnum.STALLED.getCode());
                log.info("stalled check: {},{},{},sysLive={}",
                        dto.getGameTeamCategory(), dto.getHomeTeam(), dto.getAwayTeam(), systemData.getLiveCount());
                continue;
            }

            // =========================
            // まだ試合開始前なら SCHEDULED
            // =========================
            if (isBeforeScheduledTime(dto.getFutureTime(), now)) {
                dto.setStatus(FutureScheduleEnum.SCHEDULED.getCode());
                continue;
            }

            // =========================
            // ここから先は「開始予定時刻を過ぎたが、終了済データも直近の更新データも延期情報も、
            // システム上の非終了データすらも一切無い」＝実データが一度も来ていないケース。
            // この場合のみ、真に「遅延」の可能性が高いとみなす。
            // =========================

            // DELAYED は「今日の試合」にだけ付ける
            if (targetDateIsToday) {
                dto.setStatus(FutureScheduleEnum.DELAYED.getCode());
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

    private LocalDateTime parseFutureTime(String futureTime) {
        if (!hasText(futureTime)) {
            return null;
        }

        ZoneId targetZone = DateOffsetDecisionUtil.getZoneId(); // JST

        try {
            // オフセット付き（例: 2026-08-08T19:00:00Z, +00:00 など）
            // toLocalDateTime()だけだとオフセットを無視した数値がそのまま
            // 返ってしまうため、必ず対象タイムゾーンへ変換してから取り出す
            return OffsetDateTime.parse(futureTime)
                    .atZoneSameInstant(targetZone)
                    .toLocalDateTime();
        } catch (Exception e) {
            // オフセットなし（例: 2026-08-08 19:00:00）
            // この値もUTC基準である前提で、明示的にUTCとみなしてから変換する
            try {
                LocalDateTime naiveUtc = LocalDateTime.parse(futureTime.trim(),
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                return naiveUtc.atZone(ZoneOffset.UTC)
                        .withZoneSameInstant(targetZone)
                        .toLocalDateTime();
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private boolean isBeforeScheduledTime(String futureTime, LocalDateTime now) {
        LocalDateTime scheduled = parseFutureTime(futureTime);
        return scheduled != null && scheduled.isAfter(now);
    }

    private boolean isAfterScheduledTime(String futureTime, LocalDateTime now) {
        LocalDateTime scheduled = parseFutureTime(futureTime);
        return scheduled != null && scheduled.isBefore(now);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

}