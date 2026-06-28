package dev.application.analyze.bm_m034;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * <p>BM_M034 試合中スナップショットFact作成ロジックです。</p>
 *
 * <ul>
 *   <li>入力: 国×リーグ×カード単位の {@link BookDataEntity} 群</li>
 *   <li>処理: 各時系列行をホーム側・アウェー側の2レコードへ展開し、
 *       {@link MatchTeamSnapshotFactEntity} を生成</li>
 *   <li>出力: {@link MatchTeamSnapshotFactWriter} を介して登録</li>
 * </ul>
 *
 * <h3>設計方針</h3>
 * <p>
 * 1つの {@link BookDataEntity} は「1試合・1時点」の両チーム累積情報を持っています。
 * 本ロジックではこれを、
 * </p>
 * <ul>
 *   <li>ホームチーム視点のスナップショット</li>
 *   <li>アウェーチーム視点のスナップショット</li>
 * </ul>
 * <p>
 * に分解して保存します。
 * </p>
 *
 * <h3>補足</h3>
 * <p>
 * 現時点では team master 未連携のため、{@code teamId} には暫定的にチーム名を設定します。
 * 将来的にチームマスタが整備されたら、ID解決処理へ差し替えてください。
 * </p>
 *
 * @author shiraishitoshio
 * @since 1.0
 */
@Component
public class MatchTeamSnapshotFactStat implements AnalyzeEntityIF {

    /** プロジェクト名（ログ用） */
    private static final String PROJECT_NAME = MatchTeamSnapshotFactStat.class
            .getProtectionDomain().getCodeSource().getLocation().getPath();

    /** クラス名（ログ用） */
    private static final String CLASS_NAME = MatchTeamSnapshotFactStat.class.getName();

    /** 実行モード（ログ用） */
    private static final String EXEC_MODE = "BM_M034_MATCH_TEAM_SNAPSHOT_FACT";

    /** 記録時間の日時フォーマットです。例: 2026-02-28 05:08:51+00 */
    private static final DateTimeFormatter RECORD_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX");

    /** スナップショットFact永続化サービス */
    @Autowired
    private MatchTeamSnapshotFactWriter matchTeamSnapshotFactWriter;

    /** ログ管理コンポーネント */
    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

    /**
     * {@inheritDoc}
     *
     * <p>
     * 全ての国・リーグ・カードを走査し、
     * {@link BookDataEntity} から {@link MatchTeamSnapshotFactEntity} を生成して登録します。
     * </p>
     *
     * @param entities 国×リーグ単位にまとめられた試合データ
     */
    @Override
    public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
        final String METHOD_NAME = "calcStat";
        manageLoggerComponent.init(EXEC_MODE, null);
        manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

        try {
            for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
                String[] dataCategory = ExecuteMainUtil.splitLeagueInfo(entry.getKey());
                String country = dataCategory != null && dataCategory.length > 0 ? dataCategory[0] : null;
                String league  = dataCategory != null && dataCategory.length > 1 ? dataCategory[1] : null;

                Map<String, List<BookDataEntity>> entrySub = entry.getValue();
                for (List<BookDataEntity> entityList : entrySub.values()) {
                    if (entityList == null || entityList.isEmpty()) {
                        continue;
                    }

                    decideBasedMain(entityList, country, league);
                }
            }
        } finally {
            manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
            manageLoggerComponent.clear();
        }
    }

    /**
     * 1カード分の時系列データを処理し、スナップショットFactを生成・登録します。
     *
     * @param entities 1試合分の時系列データ
     * @param country 国
     * @param league リーグ
     */
    private void decideBasedMain(List<BookDataEntity> entities, String country, String league) {
        final String METHOD_NAME = "decideBasedMain";

        try {
            if (entities == null || entities.isEmpty()) {
                return;
            }

            manageLoggerComponent.debugInfoLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, entities.get(0).getFilePath());

            List<MatchTeamSnapshotFactEntity> insertList = new ArrayList<>();
            basedEntities(insertList, entities, country, league);

            for (MatchTeamSnapshotFactEntity entity : insertList) {
                matchTeamSnapshotFactWriter.insert(entity);
            }
        } catch (Exception e) {
            String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
            manageLoggerComponent.debugErrorLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
                    "スナップショットFact作成中に例外が発生しました。");
        }
    }

    /**
     * 時系列エンティティ群からホーム側・アウェー側のスナップショットFactを生成し、
     * 出力リストへ格納します。
     *
     * @param insertList 出力先リスト
     * @param entities 時系列エンティティ一覧
     * @param country 国
     * @param league リーグ
     */
    private void basedEntities(List<MatchTeamSnapshotFactEntity> insertList,
                               List<BookDataEntity> entities,
                               String country,
                               String league) {
        final String METHOD_NAME = "basedEntities";

        for (BookDataEntity book : entities) {
            try {
                MatchTeamSnapshotFactEntity homeEntity = createHomeEntity(book, country, league);
                MatchTeamSnapshotFactEntity awayEntity = createAwayEntity(book, country, league);

                insertList.add(homeEntity);
                insertList.add(awayEntity);

            } catch (Exception e) {
                String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
                manageLoggerComponent.debugErrorLog(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
                        "スナップショット変換失敗. matchId=" + safe(book.getMatchId())
                                + ", filePath=" + safe(book.getFilePath()));
            }
        }
    }

    /**
     * ホームチーム視点のスナップショットFactを生成します。
     *
     * @param book 元データ
     * @param country 国
     * @param league リーグ
     * @return ホームチーム視点のFact
     */
    private MatchTeamSnapshotFactEntity createHomeEntity(BookDataEntity book, String country, String league) {
        MatchTeamSnapshotFactEntity entity = new MatchTeamSnapshotFactEntity();

        entity.setMatchId(trimToNull(book.getMatchId()));
        entity.setSeason(resolveSeason(book));
        entity.setCountry(trimToNull(country));
        entity.setLeagueId(trimToNull(league));
        entity.setLeagueName(trimToNull(league));
        entity.setTeamId(resolveTeamId(book.getHomeTeamName()));
        entity.setTeamName(trimToNull(book.getHomeTeamName()));
        entity.setOpponentTeamId(null);
        entity.setOpponentTeamName(trimToNull(book.getAwayTeamName()));
        entity.setHomeFlg(Boolean.TRUE);
        entity.setAsOfSeconds(resolveAsOfSeconds(book));
        entity.setMatchTimeLabel(trimToNull(book.getTime()));

        entity.setTeamScore(parseInteger(book.getHomeScore()));
        entity.setOpponentScore(parseInteger(book.getAwayScore()));
        entity.setScoreDiff(calculateScoreDiff(book.getHomeScore(), book.getAwayScore()));

        entity.setPossessionRate(parseRate(book.getHomeBallPossesion()));
        entity.setShotsCount(parseInteger(book.getHomeShootAll()));
        entity.setShotsOnTargetCount(parseInteger(book.getHomeShootIn()));
        entity.setShotsOffTargetCount(parseInteger(book.getHomeShootOut()));
        entity.setBlockedShotsCount(parseInteger(book.getHomeShootBlocked()));
        entity.setBigChancesCount(parseInteger(book.getHomeBigChance()));
        entity.setCornersCount(parseInteger(book.getHomeCornerKick()));
        entity.setBoxTouchesCount(parseInteger(book.getHomeBoxTouch()));
        entity.setPassesCount(parseInteger(book.getHomePassCount()));
        entity.setLongPassesCount(parseInteger(book.getHomeLongPassCount()));
        entity.setFinalThirdPassesCount(parseInteger(book.getHomeFinalThirdPassCount()));
        entity.setCrossesCount(parseInteger(book.getHomeCrossCount()));
        entity.setTacklesCount(parseInteger(book.getHomeTackleCount()));
        entity.setClearancesCount(parseInteger(book.getHomeClearCount()));
        entity.setDuelsWonCount(parseInteger(book.getHomeDuelCount()));
        entity.setInterceptionsCount(parseInteger(book.getHomeInterceptCount()));
        entity.setYellowCardsCount(parseInteger(book.getHomeYellowCard()));
        entity.setRedCardsCount(parseInteger(book.getHomeRedCard()));

        entity.setSnapshotRecordedAt(parseLocalDateTime(book.getRecordTime()));
        entity.setSourceCount(book.getFileCount());
        entity.setDataQualityFlag(resolveDataQualityFlag(book));
        entity.setNote(buildNote(book));

        return entity;
    }

    /**
     * アウェーチーム視点のスナップショットFactを生成します。
     *
     * @param book 元データ
     * @param country 国
     * @param league リーグ
     * @return アウェーチーム視点のFact
     */
    private MatchTeamSnapshotFactEntity createAwayEntity(BookDataEntity book, String country, String league) {
        MatchTeamSnapshotFactEntity entity = new MatchTeamSnapshotFactEntity();

        entity.setMatchId(trimToNull(book.getMatchId()));
        entity.setSeason(resolveSeason(book));
        entity.setCountry(trimToNull(country));
        entity.setLeagueId(trimToNull(league));
        entity.setLeagueName(trimToNull(league));
        entity.setTeamId(resolveTeamId(book.getAwayTeamName()));
        entity.setTeamName(trimToNull(book.getAwayTeamName()));
        entity.setOpponentTeamId(null);
        entity.setOpponentTeamName(trimToNull(book.getHomeTeamName()));
        entity.setHomeFlg(Boolean.FALSE);
        entity.setAsOfSeconds(resolveAsOfSeconds(book));
        entity.setMatchTimeLabel(trimToNull(book.getTime()));

        entity.setTeamScore(parseInteger(book.getAwayScore()));
        entity.setOpponentScore(parseInteger(book.getHomeScore()));
        entity.setScoreDiff(calculateScoreDiff(book.getAwayScore(), book.getHomeScore()));

        entity.setPossessionRate(parseRate(book.getAwayBallPossesion()));
        entity.setShotsCount(parseInteger(book.getAwayShootAll()));
        entity.setShotsOnTargetCount(parseInteger(book.getAwayShootIn()));
        entity.setShotsOffTargetCount(parseInteger(book.getAwayShootOut()));
        entity.setBlockedShotsCount(parseInteger(book.getAwayShootBlocked()));
        entity.setBigChancesCount(parseInteger(book.getAwayBigChance()));
        entity.setCornersCount(parseInteger(book.getAwayCornerKick()));
        entity.setBoxTouchesCount(parseInteger(book.getAwayBoxTouch()));
        entity.setPassesCount(parseInteger(book.getAwayPassCount()));
        entity.setLongPassesCount(parseInteger(book.getAwayLongPassCount()));
        entity.setFinalThirdPassesCount(parseInteger(book.getAwayFinalThirdPassCount()));
        entity.setCrossesCount(parseInteger(book.getAwayCrossCount()));
        entity.setTacklesCount(parseInteger(book.getAwayTackleCount()));
        entity.setClearancesCount(parseInteger(book.getAwayClearCount()));
        entity.setDuelsWonCount(parseInteger(book.getAwayDuelCount()));
        entity.setInterceptionsCount(parseInteger(book.getAwayInterceptCount()));
        entity.setYellowCardsCount(parseInteger(book.getAwayYellowCard()));
        entity.setRedCardsCount(parseInteger(book.getAwayRedCard()));

        entity.setSnapshotRecordedAt(parseLocalDateTime(book.getRecordTime()));
        entity.setSourceCount(book.getFileCount());
        entity.setDataQualityFlag(resolveDataQualityFlag(book));
        entity.setNote(buildNote(book));

        return entity;
    }

    /**
     * シーズン文字列を解決します。
     *
     * <p>
     * 現時点では記録時間の年を暫定的にシーズンとして使用します。
     * シーズンマスタが整備されたら差し替えてください。TODO
     * </p>
     *
     * @param book 元データ
     * @return シーズン
     */
    private String resolveSeason(BookDataEntity book) {
        LocalDateTime record = parseLocalDateTime(book.getRecordTime());
        if (record != null) {
            return String.valueOf(record.getYear());
        }
        return null;
    }

    /**
     * 暫定チームIDを解決します。
     *
     * <p>
     * 現時点ではチーム名をそのままIDとして利用します。
     * 将来的に team master 連携へ差し替えてください。
     * </p>
     *
     * @param teamName チーム名
     * @return 暫定チームID
     */
    private String resolveTeamId(String teamName) {
        return trimToNull(teamName);
    }

    /**
     * 試合経過秒を解決します。
     *
     * <p>
     * {@code timeSortSeconds} が存在する場合はそれを優先し、
     * なければ {@code time} の表記から補完します。
     * </p>
     *
     * @param book 元データ
     * @return 試合経過秒
     */
    private Integer resolveAsOfSeconds(BookDataEntity book) {
        Integer timeSortSeconds = parseInteger(book.getTimeSortSeconds());
        if (timeSortSeconds != null) {
            return timeSortSeconds;
        }
        return parseMatchTimeToSeconds(book.getTime());
    }

    /**
     * データ品質フラグを解決します。
     *
     * @param book 元データ
     * @return データ品質フラグ
     */
    private String resolveDataQualityFlag(BookDataEntity book) {
        if (trimToNull(book.getTimeSortSeconds()) == null) {
            return "TIME_FALLBACK";
        }
        return "NORMAL";
    }

    /**
     * 備考文字列を生成します。
     *
     * @param book 元データ
     * @return 備考
     */
    private String buildNote(BookDataEntity book) {
        StringBuilder sb = new StringBuilder();

        if (trimToNull(book.getFilePath()) != null) {
            sb.append("filePath=").append(book.getFilePath());
        }
        if (trimToNull(book.getGameLink()) != null) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("gameLink=").append(book.getGameLink());
        }
        if (trimToNull(book.getGameId()) != null) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("gameId=").append(book.getGameId());
        }

        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * スコア差を計算します。
     *
     * @param score 自チーム得点
     * @param opponentScore 相手チーム得点
     * @return スコア差
     */
    private Integer calculateScoreDiff(String score, String opponentScore) {
        Integer self = parseInteger(score);
        Integer opp = parseInteger(opponentScore);
        if (self == null || opp == null) {
            return null;
        }
        return self - opp;
    }

    /**
     * パーセント文字列を 0.0～1.0 の比率へ変換します。
     *
     * @param value パーセント文字列
     * @return 比率
     */
    private BigDecimal parseRate(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }

        try {
            if (normalized.contains("%")) {
                String num = normalized.replace("%", "").trim();
                return new BigDecimal(num).divide(new BigDecimal("100"));
            }
            return new BigDecimal(normalized);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 文字列を整数へ変換します。
     *
     * @param value 対象文字列
     * @return 変換結果。変換不能時は null
     */
    private Integer parseInteger(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }

        try {
            return Integer.parseInt(normalized.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 記録時間文字列を {@link LocalDateTime} へ変換します。
     *
     * @param value 記録時間文字列
     * @return 変換結果。変換不能時は null
     */
    private LocalDateTime parseLocalDateTime(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }

        try {
            return OffsetDateTime.parse(normalized, RECORD_TIME_FORMATTER).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 試合時間表記を秒へ変換します。
     *
     * <p>
     * 例:
     * </p>
     * <ul>
     *   <li>7:50 → 470</li>
     *   <li>45+2:10 → 2830</li>
     *   <li>ハーフタイム → 2700</li>
     *   <li>終了済 → 5400</li>
     * </ul>
     *
     * @param time 試合時間表記
     * @return 秒
     */
    private Integer parseMatchTimeToSeconds(String time) {
        String normalized = trimToNull(time);
        if (normalized == null) {
            return 0;
        }

        if ("ハーフタイム".equals(normalized)) {
            return 45 * 60;
        }
        if ("終了済".equals(normalized)) {
            return 90 * 60;
        }

        try {
            if (normalized.contains(":")) {
                String[] parts = normalized.split(":");
                String minutePart = parts[0].trim();
                int seconds = Integer.parseInt(parts[1].trim());

                int minutes;
                if (minutePart.contains("+")) {
                    String[] minParts = minutePart.split("\\+");
                    minutes = 0;
                    for (String p : minParts) {
                        minutes += Integer.parseInt(p.trim());
                    }
                } else {
                    minutes = Integer.parseInt(minutePart);
                }

                return minutes * 60 + seconds;
            }

            return Integer.parseInt(normalized);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 文字列をtrimし、空文字の場合はnullを返します。
     *
     * @param value 対象文字列
     * @return trim後文字列。空文字の場合は null
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * null安全な文字列化を行います。
     *
     * @param value 対象値
     * @return 文字列
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }
}
