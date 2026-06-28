package dev.application.analyze.bm_m042;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.entity.BookDataEntity;
import lombok.RequiredArgsConstructor;

/**
 * 試合前レポート用の要約統計プロファイルを算出するサービスです。
 *
 * <p>試合終了時点の最終スナップショットを用いて、チームごとの
 * 直近成績、先制率、先制時勝率、追いつき率、終盤得点率、
 * 終盤失点率、クリーンシート率、両チーム得点率などを集計します。</p>
 *
 * <p>1チームにつき1レコードの集約プロファイルを生成し、
 * PreMatchSummaryProfileWriter で永続化します。</p>
 */
@Component
@RequiredArgsConstructor
public class PreMatchSummaryProfileStat implements AnalyzeEntityIF {

    /** 小数スケールです。 */
    private static final int SCALE = 6;

    /** 終盤開始秒です。 */
    private static final int LATE_GAME_START_SECONDS = 76 * 60;

    /** 記録日時フォーマット候補1です。 */
    private static final DateTimeFormatter RECORD_TIME_FORMATTER_1 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX");

    /** 記録日時フォーマット候補2です。 */
    private static final DateTimeFormatter RECORD_TIME_FORMATTER_2 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX");

    /** Writer です。 */
    private final PreMatchSummaryProfileWriter preMatchSummaryProfileWriter;

    /**
     * 試合前要約プロファイルを算出します。
     *
     * @param entities 国・リーグ単位にグルーピングされた試合データ
     */
    @Override
    public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {

        if (entities == null || entities.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Map<String, List<BookDataEntity>>> countryEntry : entities.entrySet()) {

            String country = trimToNull(countryEntry.getKey());
            Map<String, List<BookDataEntity>> leagueMap = countryEntry.getValue();

            if (leagueMap == null || leagueMap.isEmpty()) {
                continue;
            }

            for (Map.Entry<String, List<BookDataEntity>> leagueEntry : leagueMap.entrySet()) {

                String leagueKey = trimToNull(leagueEntry.getKey());
                List<BookDataEntity> matchEntities = leagueEntry.getValue();

                if (matchEntities == null || matchEntities.isEmpty()) {
                    continue;
                }

                String leagueId = resolveLeagueId(leagueKey);
                String leagueName = resolveLeagueName(leagueKey);

                decideBasedMain(country, leagueId, leagueName, matchEntities);
            }
        }
    }

    /**
     * リーグ単位のデータからチームごとの試合前要約プロファイルを作成・保存します。
     *
     * @param country 国
     * @param leagueId リーグID
     * @param leagueName リーグ名
     * @param matchEntities 試合データ一覧
     */
    private void decideBasedMain(
            String country,
            String leagueId,
            String leagueName,
            List<BookDataEntity> matchEntities) {

        Map<String, PreMatchSummaryAccumulator> accumulatorMap = new LinkedHashMap<>();
        Map<String, List<BookDataEntity>> matchMap = groupByMatchId(matchEntities);

        for (Map.Entry<String, List<BookDataEntity>> matchEntry : matchMap.entrySet()) {

            List<BookDataEntity> oneMatchEntities = sortEntitiesByTime(matchEntry.getValue());
            BookDataEntity finalEntity = resolveFinalEntity(oneMatchEntities);

            if (finalEntity == null || !isFinishedMatch(finalEntity)) {
                continue;
            }

            Integer homeScore = readInteger(finalEntity,
                    "homeScore", "homeTeamScore", "ホームスコア");
            Integer awayScore = readInteger(finalEntity,
                    "awayScore", "awayTeamScore", "アウェースコア");

            if (homeScore == null || awayScore == null) {
                continue;
            }

            LocalDate matchDate = resolveMatchDate(finalEntity);

            String homeTeamId = resolveTeamId(finalEntity, true);
            String awayTeamId = resolveTeamId(finalEntity, false);
            String homeTeamName = trimToNull(readString(finalEntity,
                    "homeTeam", "homeTeamName", "ホームチーム"));
            String awayTeamName = trimToNull(readString(finalEntity,
                    "awayTeam", "awayTeamName", "アウェーチーム"));

            MatchEventSummary matchEventSummary = resolveMatchEventSummary(oneMatchEntities);

            Integer homeSetPieceGoals = readInteger(finalEntity,
                    "homeSetPieceGoals", "homeSetPieceGoalCount", "home_set_piece_goals");
            Integer awaySetPieceGoals = readInteger(finalEntity,
                    "awaySetPieceGoals", "awaySetPieceGoalCount", "away_set_piece_goals");

            String noteFragment = buildNoteFragment(finalEntity);

            PreMatchSummaryAccumulator homeAcc = accumulatorMap.computeIfAbsent(
                    buildTeamKey(country, leagueId, homeTeamId, homeTeamName),
                    k -> new PreMatchSummaryAccumulator(country, leagueId, leagueName, homeTeamId, homeTeamName));

            PreMatchSummaryAccumulator awayAcc = accumulatorMap.computeIfAbsent(
                    buildTeamKey(country, leagueId, awayTeamId, awayTeamName),
                    k -> new PreMatchSummaryAccumulator(country, leagueId, leagueName, awayTeamId, awayTeamName));

            homeAcc.accept(
                    matchDate,
                    true,
                    homeScore,
                    awayScore,
                    matchEventSummary.firstGoalSide,
                    matchEventSummary.homeLateGoal,
                    matchEventSummary.awayLateGoal,
                    homeSetPieceGoals,
                    noteFragment);

            awayAcc.accept(
                    matchDate,
                    false,
                    awayScore,
                    homeScore,
                    reverseSide(matchEventSummary.firstGoalSide),
                    matchEventSummary.awayLateGoal,
                    matchEventSummary.homeLateGoal,
                    awaySetPieceGoals,
                    noteFragment);
        }

        for (PreMatchSummaryAccumulator accumulator : accumulatorMap.values()) {
            PreMatchSummaryProfileEntity entity = buildEntity(accumulator);
            preMatchSummaryProfileWriter.insert(entity);
        }
    }

    /**
     * 集計結果から Entity を構築します。
     *
     * @param acc 集計データ
     * @return Entity
     */
    private PreMatchSummaryProfileEntity buildEntity(PreMatchSummaryAccumulator acc) {

        PreMatchSummaryProfileEntity entity = new PreMatchSummaryProfileEntity();

        entity.setMatchId(null);
        entity.setCountry(acc.country);
        entity.setLeagueId(acc.leagueId);
        entity.setLeagueName(acc.leagueName);
        entity.setTeamId(acc.teamId);
        entity.setTeamName(acc.teamName);
        entity.setOpponentTeamId(null);
        entity.setOpponentTeamName(null);

        entity.setSnapshotDate(acc.snapshotDate);

        List<MatchSummary> recent5 = resolveRecent5(acc.matches);

        entity.setLast5ResultString(buildLast5ResultString(recent5));
        entity.setLast5AvgGoals(calcAverageGoals(recent5, true));
        entity.setLast5AvgGoalsConceded(calcAverageGoals(recent5, false));
        entity.setFirstGoalRate(calcRate(acc.scoredFirstMatches, acc.knownFirstGoalMatches));
        entity.setWinAfterScoringFirstRate(calcRate(acc.wonAfterScoringFirstMatches, acc.scoredFirstMatches));
        entity.setComebackRate(calcRate(acc.comebackSuccessMatches, acc.concededFirstMatches));
        entity.setLateScoringRate(calcRate(acc.lateScoringMatches, acc.totalMatches));
        entity.setLateConcedingRate(calcRate(acc.lateConcedingMatches, acc.totalMatches));
        entity.setSetPieceGoalInvolvementRate(calcRate(acc.setPieceGoalMatches, acc.setPieceDataMatches));
        entity.setCleanSheetRate(calcRate(acc.cleanSheetMatches, acc.totalMatches));
        entity.setBothTeamsToScoreRate(calcRate(acc.bothTeamsToScoreMatches, acc.totalMatches));
        entity.setCalculatedAt(LocalDateTime.now());
        entity.setNote(buildNote(acc, recent5));

        return entity;
    }

    /**
     * 直近5試合を取得します。
     *
     * @param matches 試合一覧
     * @return 直近5試合
     */
    private List<MatchSummary> resolveRecent5(List<MatchSummary> matches) {

        List<MatchSummary> sorted = new ArrayList<>(matches);
        sorted.sort(Comparator.comparing(MatchSummary::getMatchDate,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return sorted.subList(0, Math.min(5, sorted.size()));
    }

    /**
     * 直近5試合勝敗文字列を生成します。
     *
     * <p>最新試合→過去試合の順で W/D/L を "-" 連結します。</p>
     *
     * @param recent5 直近5試合
     * @return 勝敗文字列
     */
    private String buildLast5ResultString(List<MatchSummary> recent5) {

        if (recent5 == null || recent5.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < recent5.size(); i++) {
            if (i > 0) {
                sb.append("-");
            }
            sb.append(recent5.get(i).getResultSymbol());
        }

        return sb.toString();
    }

    /**
     * 直近平均得点または平均失点を算出します。
     *
     * @param recent5 直近5試合
     * @param goalsForFlg true:平均得点, false:平均失点
     * @return 平均値
     */
    private BigDecimal calcAverageGoals(List<MatchSummary> recent5, boolean goalsForFlg) {

        if (recent5 == null || recent5.isEmpty()) {
            return BigDecimal.ZERO;
        }

        int total = 0;
        for (MatchSummary match : recent5) {
            total += goalsForFlg ? safeInt(match.getGoalsFor()) : safeInt(match.getGoalsAgainst());
        }

        return BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(recent5.size()), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 比率を算出します。
     *
     * @param numerator 分子
     * @param denominator 分母
     * @return 比率
     */
    private BigDecimal calcRate(Integer numerator, Integer denominator) {

        if (numerator == null || denominator == null || denominator == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 備考を生成します。
     *
     * @param acc 集計データ
     * @param recent5 直近5試合
     * @return 備考
     */
    private String buildNote(PreMatchSummaryAccumulator acc, List<MatchSummary> recent5) {

        StringBuilder sb = new StringBuilder();

        sb.append("ruleBasedPreMatchSummary=true");
        sb.append(", totalMatches=").append(acc.totalMatches);
        sb.append(", recent5Count=").append(recent5 == null ? 0 : recent5.size());
        sb.append(", firstGoalRateDenominator=").append(acc.knownFirstGoalMatches);
        sb.append(", scoredFirstMatches=").append(acc.scoredFirstMatches);
        sb.append(", concededFirstMatches=").append(acc.concededFirstMatches);
        sb.append(", setPieceDataMatches=").append(acc.setPieceDataMatches);
        sb.append(", lateDefinition=76plus");
        sb.append(", last5Order=latestFirst");

        if (acc.noteSamples != null && !acc.noteSamples.isEmpty()) {
            sb.append(", source=").append(String.join(" | ", acc.noteSamples));
        }

        return sb.toString();
    }

    /**
     * 1試合分のイベント要約を解決します。
     *
     * @param entities 試合時系列
     * @return イベント要約
     */
    private MatchEventSummary resolveMatchEventSummary(List<BookDataEntity> entities) {

        MatchEventSummary summary = new MatchEventSummary();

        if (entities == null || entities.isEmpty()) {
            return summary;
        }

        int prevHome = 0;
        int prevAway = 0;

        for (BookDataEntity entity : entities) {

            Integer currentHome = readInteger(entity,
                    "homeScore", "homeTeamScore", "ホームスコア");
            Integer currentAway = readInteger(entity,
                    "awayScore", "awayTeamScore", "アウェースコア");
            Integer currentSeconds = resolveTimeSortSecondsSafe(entity);

            if (currentHome == null || currentAway == null || currentSeconds == null) {
                continue;
            }

            int homeDelta = currentHome - prevHome;
            int awayDelta = currentAway - prevAway;

            if (homeDelta > 0 || awayDelta > 0) {

                if (summary.firstGoalSide == GoalSide.NONE) {
                    if (homeDelta > 0 && awayDelta == 0) {
                        summary.firstGoalSide = GoalSide.SCORED_FIRST;
                    } else if (awayDelta > 0 && homeDelta == 0) {
                        summary.firstGoalSide = GoalSide.CONCEDED_FIRST;
                    } else {
                        summary.firstGoalSide = GoalSide.UNKNOWN;
                    }
                }

                if (homeDelta > 0 && currentSeconds >= LATE_GAME_START_SECONDS) {
                    summary.homeLateGoal = true;
                }
                if (awayDelta > 0 && currentSeconds >= LATE_GAME_START_SECONDS) {
                    summary.awayLateGoal = true;
                }
            }

            prevHome = currentHome;
            prevAway = currentAway;
        }

        return summary;
    }

    /**
     * ゴールサイドを反転します。
     *
     * @param side ホーム視点の結果
     * @return アウェー視点の結果
     */
    private GoalSide reverseSide(GoalSide side) {

        if (side == null) {
            return GoalSide.UNKNOWN;
        }

        switch (side) {
            case SCORED_FIRST:
                return GoalSide.CONCEDED_FIRST;
            case CONCEDED_FIRST:
                return GoalSide.SCORED_FIRST;
            default:
                return side;
        }
    }

    /**
     * matchId 単位にグルーピングします。
     *
     * @param entities 試合データ
     * @return matchIdごとのMap
     */
    private Map<String, List<BookDataEntity>> groupByMatchId(List<BookDataEntity> entities) {

        Map<String, List<BookDataEntity>> result = new LinkedHashMap<>();

        for (BookDataEntity entity : entities) {
            String matchId = trimToNull(readString(entity, "matchId", "match_id", "gameId", "game_id"));
            if (matchId == null) {
                matchId = "UNKNOWN_MATCH";
            }
            result.computeIfAbsent(matchId, k -> new ArrayList<>()).add(entity);
        }

        return result;
    }

    /**
     * 時間順にソートします。
     *
     * @param entities 試合データ
     * @return ソート済み一覧
     */
    private List<BookDataEntity> sortEntitiesByTime(List<BookDataEntity> entities) {

        List<BookDataEntity> sorted = new ArrayList<>(entities);
        sorted.sort(Comparator.comparing(this::resolveTimeSortSecondsSafe));
        return sorted;
    }

    /**
     * 最終行を解決します。
     *
     * @param entities 試合データ
     * @return 最終行
     */
    private BookDataEntity resolveFinalEntity(List<BookDataEntity> entities) {

        if (entities == null || entities.isEmpty()) {
            return null;
        }

        for (BookDataEntity entity : entities) {
            String matchTime = trimToNull(readString(entity, "times", "matchTime", "試合時間"));
            if (BookMakersCommonConst.FIN.equals(matchTime)) {
                return entity;
            }
        }

        return entities.stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparing(this::resolveTimeSortSecondsSafe))
                .orElse(entities.get(entities.size() - 1));
    }

    /**
     * 試合終了済みかを判定します。
     *
     * @param entity 対象
     * @return 終了済みなら true
     */
    private boolean isFinishedMatch(BookDataEntity entity) {

        String matchTime = trimToNull(readString(entity, "times", "matchTime", "試合時間"));
        if (BookMakersCommonConst.FIN.equals(matchTime)) {
            return true;
        }

        Integer seconds = resolveTimeSortSecondsSafe(entity);
        return seconds != null && seconds >= (90 * 60);
    }

    /**
     * 試合日を解決します。
     *
     * @param entity 対象
     * @return 試合日
     */
    private LocalDate resolveMatchDate(BookDataEntity entity) {

        String recordTime = readString(entity, "recordTime", "record_time");
        if (recordTime == null) {
            return null;
        }

        LocalDateTime ldt = parseLocalDateTime(recordTime);
        return ldt != null ? ldt.toLocalDate() : null;
    }

    /**
     * チームIDを解決します。
     *
     * <p>チームマスタ未連携の間はチーム名を代用します。</p>
     *
     * @param entity 対象
     * @param homeFlg ホームか
     * @return チームID
     */
    private String resolveTeamId(BookDataEntity entity, boolean homeFlg) {

        String id = homeFlg
                ? readString(entity, "homeTeamId", "home_team_id")
                : readString(entity, "awayTeamId", "away_team_id");

        if (trimToNull(id) != null) {
            return trimToNull(id);
        }

        return homeFlg
                ? trimToNull(readString(entity, "homeTeam", "homeTeamName", "ホームチーム"))
                : trimToNull(readString(entity, "awayTeam", "awayTeamName", "アウェーチーム"));
    }

    /**
     * リーグIDを解決します。
     *
     * @param leagueKey リーグキー
     * @return リーグID
     */
    private String resolveLeagueId(String leagueKey) {

        if (leagueKey == null) {
            return null;
        }

        int idx = leagueKey.indexOf("_");
        if (idx < 0) {
            return leagueKey;
        }
        return trimToNull(leagueKey.substring(0, idx));
    }

    /**
     * リーグ名を解決します。
     *
     * @param leagueKey リーグキー
     * @return リーグ名
     */
    private String resolveLeagueName(String leagueKey) {

        if (leagueKey == null) {
            return null;
        }

        int idx = leagueKey.indexOf("_");
        if (idx < 0 || idx + 1 >= leagueKey.length()) {
            return leagueKey;
        }
        return trimToNull(leagueKey.substring(idx + 1));
    }

    /**
     * チームキーを生成します。
     *
     * @param country 国
     * @param leagueId リーグID
     * @param teamId チームID
     * @param teamName チーム名
     * @return チームキー
     */
    private String buildTeamKey(String country, String leagueId, String teamId, String teamName) {
        return String.valueOf(country) + "|" + String.valueOf(leagueId) + "|" + String.valueOf(teamId) + "|" + String.valueOf(teamName);
    }

    /**
     * 備考断片を生成します。
     *
     * @param entity 対象
     * @return 備考断片
     */
    private String buildNoteFragment(BookDataEntity entity) {

        StringBuilder sb = new StringBuilder();

        String gameLink = trimToNull(readString(entity, "gameLink", "試合リンク"));
        String matchId = trimToNull(readString(entity, "matchId", "match_id", "gameId", "game_id"));

        if (gameLink != null) {
            sb.append("gameLink=").append(gameLink);
        }
        if (matchId != null) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("matchId=").append(matchId);
        }

        return sb.toString();
    }

    /**
     * 文字列を取得します。
     *
     * @param target 対象
     * @param fieldNames 候補フィールド名
     * @return 文字列
     */
    private String readString(Object target, String... fieldNames) {

        Object value = readField(target, fieldNames);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 整数を取得します。
     *
     * @param target 対象
     * @param fieldNames 候補フィールド名
     * @return 整数
     */
    private Integer readInteger(Object target, String... fieldNames) {

        Object value = readField(target, fieldNames);
        if (value == null) {
            return null;
        }

        try {
            if (value instanceof Integer) {
                return (Integer) value;
            }
            if (value instanceof Long) {
                return ((Long) value).intValue();
            }
            if (value instanceof BigDecimal) {
                return ((BigDecimal) value).intValue();
            }
            String str = String.valueOf(value).replace("%", "").replace(",", "").trim();
            if (str.isEmpty()) {
                return null;
            }
            return Integer.valueOf(str);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * フィールド値をリフレクションで取得します。
     *
     * @param target 対象
     * @param fieldNames 候補フィールド名
     * @return 値
     */
    private Object readField(Object target, String... fieldNames) {

        if (target == null || fieldNames == null) {
            return null;
        }

        Class<?> clazz = target.getClass();

        for (String fieldName : fieldNames) {
            if (fieldName == null) {
                continue;
            }

            Field field = findField(clazz, fieldName);
            if (field == null) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object value = field.get(target);
                if (value != null) {
                    return value;
                }
            } catch (Exception e) {
                // no-op
            }
        }

        return null;
    }

    /**
     * フィールドを探索します。
     *
     * @param clazz クラス
     * @param fieldName フィールド名
     * @return フィールド
     */
    private Field findField(Class<?> clazz, String fieldName) {

        Class<?> current = clazz;

        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }

        return null;
    }

    /**
     * 時間ソート秒を安全に解決します。
     *
     * @param entity 対象
     * @return 秒
     */
    private Integer resolveTimeSortSecondsSafe(BookDataEntity entity) {

        Integer seconds = readInteger(entity, "timeSortSeconds", "時間ソート秒");
        if (seconds != null) {
            return seconds;
        }

        String matchTime = readString(entity, "times", "matchTime", "試合時間");
        Integer parsed = parseMatchTimeToSeconds(matchTime);
        return parsed != null ? parsed : Integer.MAX_VALUE;
    }

    /**
     * 時間ラベルを秒に変換します。
     *
     * @param matchTime 時間ラベル
     * @return 秒
     */
    private Integer parseMatchTimeToSeconds(String matchTime) {

        String value = trimToNull(matchTime);
        if (value == null) {
            return null;
        }

        if (BookMakersCommonConst.FIN.equals(value)) {
            return 90 * 60;
        }
        if (BookMakersCommonConst.HALF_TIME.equals(value)) {
            return 45 * 60;
        }

        try {
            String normalized = value.replace("+", ":");
            String[] parts = normalized.split(":");
            if (parts.length >= 2) {
                int minutes = Integer.parseInt(parts[0].trim());
                int seconds = Integer.parseInt(parts[1].trim());
                return (minutes * 60) + seconds;
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    /**
     * LocalDateTime に変換します。
     *
     * @param value 文字列
     * @return LocalDateTime
     */
    private LocalDateTime parseLocalDateTime(String value) {

        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }

        try {
            return OffsetDateTime.parse(trimmed).toLocalDateTime();
        } catch (Exception e) {
            // no-op
        }

        try {
            return OffsetDateTime.parse(trimmed, RECORD_TIME_FORMATTER_1).toLocalDateTime();
        } catch (Exception e) {
            // no-op
        }

        try {
            return OffsetDateTime.parse(trimmed, RECORD_TIME_FORMATTER_2).toLocalDateTime();
        } catch (Exception e) {
            // no-op
        }

        try {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 文字列を trim し、空なら null を返します。
     *
     * @param value 値
     * @return trim後文字列
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * null安全なint化です。
     *
     * @param value 値
     * @return int値
     */
    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 先制イベントの向きです。
     */
    private enum GoalSide {

        /** 得点先行です。 */
        SCORED_FIRST,

        /** 失点先行です。 */
        CONCEDED_FIRST,

        /** ゴールなしです。 */
        NONE,

        /** 不明です。 */
        UNKNOWN
    }

    /**
     * 試合イベント要約です。
     */
    private static class MatchEventSummary {

        /** 先制向きです。 */
        private GoalSide firstGoalSide = GoalSide.NONE;

        /** ホーム終盤得点有無です。 */
        private boolean homeLateGoal;

        /** アウェー終盤得点有無です。 */
        private boolean awayLateGoal;
    }

    /**
     * 試合サマリです。
     */
    private static class MatchSummary {

        /** 試合日です。 */
        private final LocalDate matchDate;

        /** 結果記号です。 */
        private final String resultSymbol;

        /** 得点です。 */
        private final Integer goalsFor;

        /** 失点です。 */
        private final Integer goalsAgainst;

        /**
         * コンストラクタです。
         *
         * @param matchDate 試合日
         * @param resultSymbol 結果記号
         * @param goalsFor 得点
         * @param goalsAgainst 失点
         */
        private MatchSummary(
                LocalDate matchDate,
                String resultSymbol,
                Integer goalsFor,
                Integer goalsAgainst) {
            this.matchDate = matchDate;
            this.resultSymbol = resultSymbol;
            this.goalsFor = goalsFor;
            this.goalsAgainst = goalsAgainst;
        }

        /**
         * 試合日を返します。
         *
         * @return 試合日
         */
        public LocalDate getMatchDate() {
            return matchDate;
        }

        /**
         * 結果記号を返します。
         *
         * @return 結果記号
         */
        public String getResultSymbol() {
            return resultSymbol;
        }

        /**
         * 得点を返します。
         *
         * @return 得点
         */
        public Integer getGoalsFor() {
            return goalsFor;
        }

        /**
         * 失点を返します。
         *
         * @return 失点
         */
        public Integer getGoalsAgainst() {
            return goalsAgainst;
        }
    }

    /**
     * チーム集計用の内部クラスです。
     */
    private static class PreMatchSummaryAccumulator {

        /** 国です。 */
        private final String country;

        /** リーグIDです。 */
        private final String leagueId;

        /** リーグ名です。 */
        private final String leagueName;

        /** チームIDです。 */
        private final String teamId;

        /** チーム名です。 */
        private final String teamName;

        /** スナップショット日です。 */
        private LocalDate snapshotDate;

        /** 総試合数です。 */
        private int totalMatches;

        /** 先制方向が判別できた試合数です。 */
        private int knownFirstGoalMatches;

        /** 先制した試合数です。 */
        private int scoredFirstMatches;

        /** 先制時に勝利した試合数です。 */
        private int wonAfterScoringFirstMatches;

        /** 先制された試合数です。 */
        private int concededFirstMatches;

        /** 先制されたあと追いついた試合数です。 */
        private int comebackSuccessMatches;

        /** 終盤得点試合数です。 */
        private int lateScoringMatches;

        /** 終盤失点試合数です。 */
        private int lateConcedingMatches;

        /** セットプレー得点あり試合数です。 */
        private int setPieceGoalMatches;

        /** セットプレー得点データがある試合数です。 */
        private int setPieceDataMatches;

        /** クリーンシート試合数です。 */
        private int cleanSheetMatches;

        /** 両チーム得点試合数です。 */
        private int bothTeamsToScoreMatches;

        /** 試合一覧です。 */
        private final List<MatchSummary> matches = new ArrayList<>();

        /** 備考サンプルです。 */
        private final List<String> noteSamples = new ArrayList<>();

        /**
         * コンストラクタです。
         *
         * @param country 国
         * @param leagueId リーグID
         * @param leagueName リーグ名
         * @param teamId チームID
         * @param teamName チーム名
         */
        private PreMatchSummaryAccumulator(
                String country,
                String leagueId,
                String leagueName,
                String teamId,
                String teamName) {
            this.country = country;
            this.leagueId = leagueId;
            this.leagueName = leagueName;
            this.teamId = teamId;
            this.teamName = teamName;
        }

        /**
         * 1試合分を反映します。
         *
         * @param matchDate 試合日
         * @param homeFlg ホームフラグ
         * @param goalsFor 得点
         * @param goalsAgainst 失点
         * @param firstGoalSide 先制向き
         * @param lateScored 終盤得点したか
         * @param lateConceded 終盤失点したか
         * @param setPieceGoals セットプレー得点数
         * @param note 備考
         */
        private void accept(
                LocalDate matchDate,
                boolean homeFlg,
                Integer goalsFor,
                Integer goalsAgainst,
                GoalSide firstGoalSide,
                boolean lateScored,
                boolean lateConceded,
                Integer setPieceGoals,
                String note) {

            this.totalMatches++;

            int gf = safe(goalsFor);
            int ga = safe(goalsAgainst);

            this.matches.add(new MatchSummary(matchDate, resolveResultSymbol(gf, ga), gf, ga));

            if (firstGoalSide == GoalSide.SCORED_FIRST || firstGoalSide == GoalSide.CONCEDED_FIRST) {
                this.knownFirstGoalMatches++;
            }

            if (firstGoalSide == GoalSide.SCORED_FIRST) {
                this.scoredFirstMatches++;
                if (gf > ga) {
                    this.wonAfterScoringFirstMatches++;
                }
            }

            if (firstGoalSide == GoalSide.CONCEDED_FIRST) {
                this.concededFirstMatches++;
                if (gf >= ga) {
                    this.comebackSuccessMatches++;
                }
            }

            if (lateScored) {
                this.lateScoringMatches++;
            }

            if (lateConceded) {
                this.lateConcedingMatches++;
            }

            if (setPieceGoals != null) {
                this.setPieceDataMatches++;
                if (setPieceGoals > 0) {
                    this.setPieceGoalMatches++;
                }
            }

            if (ga == 0) {
                this.cleanSheetMatches++;
            }

            if (gf > 0 && ga > 0) {
                this.bothTeamsToScoreMatches++;
            }

            if (matchDate != null) {
                if (this.snapshotDate == null || matchDate.isAfter(this.snapshotDate)) {
                    this.snapshotDate = matchDate;
                }
            }

            if (note != null && !note.isBlank() && this.noteSamples.size() < 5) {
                this.noteSamples.add(note);
            }
        }

        /**
         * null安全なint化です。
         *
         * @param value 値
         * @return int値
         */
        private static int safe(Integer value) {
            return value == null ? 0 : value;
        }

        /**
         * 勝敗記号を返します。
         *
         * @param goalsFor 得点
         * @param goalsAgainst 失点
         * @return W/D/L
         */
        private static String resolveResultSymbol(int goalsFor, int goalsAgainst) {
            if (goalsFor > goalsAgainst) {
                return "W";
            }
            if (goalsFor == goalsAgainst) {
                return "D";
            }
            return "L";
        }
    }
}
