package dev.application.analyze.bm_m040;

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
 * チームのプレースタイルプロファイルを算出するサービスです。
 *
 * <p>試合スナップショットの最終行をベースに、チーム単位で
 * ポゼッション率、パス傾向、ロングボール傾向、守備強度などを集計し、
 * 暫定的なスタイルラベルを付与します。</p>
 *
 * <p>本実装では styleClusterId / styleLabel / styleConfidence は
 * ルールベースで付与しています。将来的に教師なしクラスタリングへ
 * 置き換え可能な構成です。</p>
 */
@Component
@RequiredArgsConstructor
public class TeamStyleProfileStat implements AnalyzeEntityIF {

    /** 小数スケールです。 */
    private static final int SCALE = 6;

    /** 90分換算の基準分数です。 */
    private static final BigDecimal BASE_MINUTES = BigDecimal.valueOf(90);

    /** 記録日時フォーマット候補1です。 */
    private static final DateTimeFormatter RECORD_TIME_FORMATTER_1 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX");

    /** 記録日時フォーマット候補2です。 */
    private static final DateTimeFormatter RECORD_TIME_FORMATTER_2 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX");

    /** Writer です。 */
    private final TeamStyleProfileWriter teamStyleProfileWriter;

    /**
     * チームスタイルプロファイルを算出します。
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
     * リーグ単位のデータからチームごとのスタイルプロファイルを作成・保存します。
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

        Map<String, TeamStyleAccumulator> accumulatorMap = new LinkedHashMap<>();
        Map<String, List<BookDataEntity>> matchMap = groupByMatchId(matchEntities);

        for (Map.Entry<String, List<BookDataEntity>> matchEntry : matchMap.entrySet()) {

            List<BookDataEntity> oneMatchEntities = sortEntitiesByTime(matchEntry.getValue());
            BookDataEntity finalEntity = resolveFinalEntity(oneMatchEntities);

            if (finalEntity == null) {
                continue;
            }

            Integer actualMinutes = resolveActualMinutes(finalEntity);
            if (actualMinutes == null || actualMinutes <= 0) {
                actualMinutes = 90;
            }

            LocalDate matchDate = resolveMatchDate(finalEntity);

            accumulateHome(accumulatorMap, finalEntity, country, leagueId, leagueName, actualMinutes, matchDate);
            accumulateAway(accumulatorMap, finalEntity, country, leagueId, leagueName, actualMinutes, matchDate);
        }

        for (TeamStyleAccumulator accumulator : accumulatorMap.values()) {
            TeamStyleProfileEntity entity = buildEntity(accumulator);
            teamStyleProfileWriter.insert(entity);
        }
    }

    /**
     * ホームチーム分を集計します。
     */
    private void accumulateHome(
            Map<String, TeamStyleAccumulator> accumulatorMap,
            BookDataEntity entity,
            String country,
            String leagueId,
            String leagueName,
            Integer actualMinutes,
            LocalDate matchDate) {

        String teamName = readString(entity,
                "homeTeam", "homeTeamName", "home_team_name");
        String teamId = resolveTeamId(entity, true);
        String teamKey = buildTeamKey(country, leagueId, teamId, teamName);

        TeamStyleAccumulator acc = accumulatorMap.computeIfAbsent(
                teamKey,
                k -> new TeamStyleAccumulator(country, leagueId, leagueName, teamId, teamName));

        acc.accept(
                actualMinutes,
                matchDate,
                parsePercent(readString(entity, "homePossession", "homePossessionRate", "home_possession")),
                readInteger(entity, "homePassCount", "homePasses", "home_pass_count"),
                readInteger(entity, "homeLongPassCount", "homeLongPasses", "home_long_pass_count"),
                readInteger(entity, "homeFinalThirdPassCount", "homeFinalThirdPasses", "home_final_third_pass_count"),
                readInteger(entity, "homeCrossCount", "homeCrosses", "home_cross_count"),
                readInteger(entity, "homeShootAll", "homeShots", "homeShotsCount", "home_shoot_all"),
                readInteger(entity, "homeBoxTouch", "homeBoxTouches", "homeBoxTouchesCount", "home_box_touch"),
                readInteger(entity, "homeTackleCount", "homeTackles", "home_tackle_count"),
                readInteger(entity, "homeInterceptCount", "homeInterceptions", "home_intercept_count"),
                readInteger(entity, "homeClearCount", "homeClearances", "home_clear_count"),
                readInteger(entity, "homeDuelCount", "homeDuels", "home_duel_count"),
                buildStyleNoteFragment(entity));
    }

    /**
     * アウェーチーム分を集計します。
     */
    private void accumulateAway(
            Map<String, TeamStyleAccumulator> accumulatorMap,
            BookDataEntity entity,
            String country,
            String leagueId,
            String leagueName,
            Integer actualMinutes,
            LocalDate matchDate) {

        String teamName = readString(entity,
                "awayTeam", "awayTeamName", "away_team_name");
        String teamId = resolveTeamId(entity, false);
        String teamKey = buildTeamKey(country, leagueId, teamId, teamName);

        TeamStyleAccumulator acc = accumulatorMap.computeIfAbsent(
                teamKey,
                k -> new TeamStyleAccumulator(country, leagueId, leagueName, teamId, teamName));

        acc.accept(
                actualMinutes,
                matchDate,
                parsePercent(readString(entity, "awayPossession", "awayPossessionRate", "away_possession")),
                readInteger(entity, "awayPassCount", "awayPasses", "away_pass_count"),
                readInteger(entity, "awayLongPassCount", "awayLongPasses", "away_long_pass_count"),
                readInteger(entity, "awayFinalThirdPassCount", "awayFinalThirdPasses", "away_final_third_pass_count"),
                readInteger(entity, "awayCrossCount", "awayCrosses", "away_cross_count"),
                readInteger(entity, "awayShootAll", "awayShots", "awayShotsCount", "away_shoot_all"),
                readInteger(entity, "awayBoxTouch", "awayBoxTouches", "awayBoxTouchesCount", "away_box_touch"),
                readInteger(entity, "awayTackleCount", "awayTackles", "away_tackle_count"),
                readInteger(entity, "awayInterceptCount", "awayInterceptions", "away_intercept_count"),
                readInteger(entity, "awayClearCount", "awayClearances", "away_clear_count"),
                readInteger(entity, "awayDuelCount", "awayDuels", "away_duel_count"),
                buildStyleNoteFragment(entity));
    }

    /**
     * 集計結果から Entity を構築します。
     */
    private TeamStyleProfileEntity buildEntity(TeamStyleAccumulator acc) {

        TeamStyleProfileEntity entity = new TeamStyleProfileEntity();

        entity.setMatchId(null);
        entity.setCountry(acc.country);
        entity.setLeagueId(acc.leagueId);
        entity.setLeagueName(acc.leagueName);
        entity.setTeamId(acc.teamId);
        entity.setTeamName(acc.teamName);
        entity.setOpponentTeamId(null);
        entity.setOpponentTeamName(null);

        entity.setFromDate(acc.fromDate);
        entity.setToDate(acc.toDate);

        BigDecimal possessionRate = calcAverage(acc.possessionRateSum, acc.sampleMatchCount);
        BigDecimal passesPer90 = calcPer90(acc.totalPasses, acc.totalMinutes);
        BigDecimal longPassRate = calcRate(acc.totalLongPasses, acc.totalPasses);
        BigDecimal finalThirdPassRate = calcRate(acc.totalFinalThirdPasses, acc.totalPasses);
        BigDecimal crossRate = calcRate(acc.totalCrosses, acc.totalPasses);
        BigDecimal shotsPerBoxTouch = calcRate(acc.totalShots, acc.totalBoxTouches);

        int totalDefActions = safeInt(acc.totalTackles)
                + safeInt(acc.totalInterceptions)
                + safeInt(acc.totalClearances);

        BigDecimal defensiveActionIntensity = calcPer90(totalDefActions, acc.totalMinutes);
        BigDecimal clearanceRate = calcRate(acc.totalClearances, totalDefActions);
        BigDecimal duelIntensity = calcPer90(acc.totalDuels, acc.totalMinutes);

        entity.setPossessionRate(possessionRate);
        entity.setPassesPer90(passesPer90);
        entity.setLongPassRate(longPassRate);
        entity.setFinalThirdPassRate(finalThirdPassRate);
        entity.setCrossRate(crossRate);
        entity.setShotsPerBoxTouch(shotsPerBoxTouch);
        entity.setDefensiveActionIntensity(defensiveActionIntensity);
        entity.setClearanceRate(clearanceRate);
        entity.setDuelIntensity(duelIntensity);

        StyleDecision styleDecision = decideStyle(
                possessionRate,
                passesPer90,
                longPassRate,
                finalThirdPassRate,
                crossRate,
                shotsPerBoxTouch,
                defensiveActionIntensity,
                clearanceRate,
                duelIntensity);

        //TODO
        //k-means
        //Gaussian Mixture
        //PCA + clusteringに置き換えるのが理想
        entity.setStyleClusterId(styleDecision.clusterId);
        entity.setStyleLabel(styleDecision.label);
        entity.setStyleConfidence(styleDecision.confidence);

        entity.setSampleMatchCount(acc.sampleMatchCount);
        entity.setCalculatedAt(LocalDateTime.now());
        entity.setStyleNote(buildStyleNote(acc, styleDecision));

        return entity;
    }

    /**
     * スタイル判定を行います。
     *
     * <p>暫定のルールベースです。将来的にクラスタリングで置換可能です。</p>
     */
    private StyleDecision decideStyle(
            BigDecimal possessionRate,
            BigDecimal passesPer90,
            BigDecimal longPassRate,
            BigDecimal finalThirdPassRate,
            BigDecimal crossRate,
            BigDecimal shotsPerBoxTouch,
            BigDecimal defensiveActionIntensity,
            BigDecimal clearanceRate,
            BigDecimal duelIntensity) {

        double pos = toDouble(possessionRate);
        double pass90 = toDouble(passesPer90);
        double longRate = toDouble(longPassRate);
        double final3rd = toDouble(finalThirdPassRate);
        double cross = toDouble(crossRate);
        double shotBox = toDouble(shotsPerBoxTouch);
        double defInt = toDouble(defensiveActionIntensity);
        double clearRate = toDouble(clearanceRate);
        double duelInt = toDouble(duelIntensity);

        double possessionScore =
                scoreHigh(pos, 0.48, 0.62)
                + scoreHigh(pass90, 320, 620)
                + scoreHigh(final3rd, 0.14, 0.30)
                - scoreHigh(longRate, 0.10, 0.24) * 0.30;

        double longBallScore =
                scoreHigh(longRate, 0.10, 0.24)
                + scoreLow(pass90, 320, 620)
                + scoreHigh(shotBox, 0.12, 0.35) * 0.50;

        double counterScore =
                scoreLow(pos, 0.48, 0.62)
                + scoreHigh(defInt, 18, 42)
                + scoreHigh(clearRate, 0.18, 0.45)
                + scoreHigh(shotBox, 0.12, 0.35) * 0.50;

        double wingScore =
                scoreHigh(cross, 0.03, 0.12)
                + scoreHigh(final3rd, 0.14, 0.30) * 0.50
                + scoreHigh(duelInt, 10, 28) * 0.30;

        double balancedScore =
                0.80
                - Math.abs(pos - 0.50)
                - Math.abs(longRate - 0.15)
                - Math.abs(cross - 0.06);

        Map<Integer, ScoreLabel> scoreMap = new LinkedHashMap<>();
        scoreMap.put(1, new ScoreLabel("ポゼッション", possessionScore));
        scoreMap.put(2, new ScoreLabel("ロングボール", longBallScore));
        scoreMap.put(3, new ScoreLabel("堅守速攻", counterScore));
        scoreMap.put(4, new ScoreLabel("サイドアタック", wingScore));
        scoreMap.put(5, new ScoreLabel("バランス", balancedScore));

        Integer bestClusterId = null;
        String bestLabel = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double secondScore = Double.NEGATIVE_INFINITY;

        for (Map.Entry<Integer, ScoreLabel> e : scoreMap.entrySet()) {
            double current = e.getValue().score;
            if (current > bestScore) {
                secondScore = bestScore;
                bestScore = current;
                bestClusterId = e.getKey();
                bestLabel = e.getValue().label;
            } else if (current > secondScore) {
                secondScore = current;
            }
        }

        double gap = bestScore - secondScore;
        double confidence = clamp(0.55 + gap * 0.20, 0.55, 0.95);

        return new StyleDecision(
                bestClusterId,
                bestLabel,
                BigDecimal.valueOf(confidence).setScale(SCALE, RoundingMode.HALF_UP));
    }

    /**
     * スタイル備考を作成します。
     */
    private String buildStyleNote(TeamStyleAccumulator acc, StyleDecision styleDecision) {

        StringBuilder sb = new StringBuilder();
        sb.append("ruleBasedStyleClassification=true");
        sb.append(", sampleMatchCount=").append(acc.sampleMatchCount);

        if (acc.fromDate != null) {
            sb.append(", fromDate=").append(acc.fromDate);
        }
        if (acc.toDate != null) {
            sb.append(", toDate=").append(acc.toDate);
        }
        if (styleDecision != null) {
            sb.append(", styleLabel=").append(styleDecision.label);
            sb.append(", styleConfidence=").append(styleDecision.confidence);
        }
        if (acc.noteSamples != null && !acc.noteSamples.isEmpty()) {
            sb.append(", source=").append(String.join(" | ", acc.noteSamples));
        }

        return sb.toString();
    }

    /**
     * 試合一覧を matchId 単位にグルーピングします。
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
     */
    private List<BookDataEntity> sortEntitiesByTime(List<BookDataEntity> entities) {

        List<BookDataEntity> sorted = new ArrayList<>(entities);
        sorted.sort(Comparator.comparing(this::resolveTimeSortSecondsSafe));
        return sorted;
    }

    /**
     * 試合の最終行を解決します。
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
     * 実プレー分数を解決します。
     */
    private Integer resolveActualMinutes(BookDataEntity entity) {

        Integer timeSortSeconds = readInteger(entity, "timeSortSeconds", "時間ソート秒");
        if (timeSortSeconds != null && timeSortSeconds > 0) {
            return Math.max(1, (int) Math.ceil(timeSortSeconds / 60.0));
        }

        String matchTime = readString(entity, "times", "matchTime", "試合時間");
        Integer parsedSeconds = parseMatchTimeToSeconds(matchTime);
        if (parsedSeconds != null && parsedSeconds > 0) {
            return Math.max(1, (int) Math.ceil(parsedSeconds / 60.0));
        }

        return 90;
    }

    /**
     * 試合日を解決します。
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
     */
    private String resolveTeamId(BookDataEntity entity, boolean homeFlg) {

        String id = homeFlg
                ? readString(entity, "homeTeamId", "home_team_id")
                : readString(entity, "awayTeamId", "away_team_id");

        if (trimToNull(id) != null) {
            return trimToNull(id);
        }

        return homeFlg
                ? trimToNull(readString(entity, "homeTeam", "homeTeamName", "home_team_name"))
                : trimToNull(readString(entity, "awayTeam", "awayTeamName", "away_team_name"));
    }

    /**
     * リーグIDを解決します。
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
     */
    private String buildTeamKey(String country, String leagueId, String teamId, String teamName) {
        return String.valueOf(country) + "|" + String.valueOf(leagueId) + "|" + String.valueOf(teamId) + "|" + String.valueOf(teamName);
    }

    /**
     * 備考用断片を生成します。
     */
    private String buildStyleNoteFragment(BookDataEntity entity) {

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
     * 比率を計算します。
     */
    private BigDecimal calcRate(Integer numerator, Integer denominator) {

        if (numerator == null || denominator == null || denominator == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 平均を計算します。
     */
    private BigDecimal calcAverage(BigDecimal sum, Integer count) {

        if (sum == null || count == null || count == 0) {
            return BigDecimal.ZERO;
        }

        return sum.divide(BigDecimal.valueOf(count), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 90分換算値を計算します。
     */
    private BigDecimal calcPer90(Integer count, Integer actualMinutes) {

        if (count == null || actualMinutes == null || actualMinutes == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(count)
                .multiply(BASE_MINUTES)
                .divide(BigDecimal.valueOf(actualMinutes), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * パーセント文字列を数値化します。
     *
     * <p>例: "54%" → 0.540000</p>
     */
    private BigDecimal parsePercent(String value) {

        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return BigDecimal.ZERO;
        }

        try {
            String normalized = trimmed.replace("%", "").trim();
            return new BigDecimal(normalized)
                    .divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 時間ラベルを秒に変換します。
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
     * 時間ソート秒を安全に解決します。
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
     * 高いほど高スコアになる正規化関数です。
     */
    private double scoreHigh(double value, double min, double max) {
        return clamp((value - min) / (max - min), 0.0, 1.0);
    }

    /**
     * 低いほど高スコアになる正規化関数です。
     */
    private double scoreLow(double value, double min, double max) {
        return 1.0 - scoreHigh(value, min, max);
    }

    /**
     * 範囲制限です。
     */
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * BigDecimal を double に変換します。
     */
    private double toDouble(BigDecimal value) {
        return value == null ? 0.0d : value.doubleValue();
    }

    /**
     * null 安全な int 化です。
     */
    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 文字列を trim し、空なら null を返します。
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 文字列を取得します。
     */
    private String readString(Object target, String... fieldNames) {

        Object value = readField(target, fieldNames);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 整数を取得します。
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
     * スコアとラベルです。
     */
    private static class ScoreLabel {

        /** ラベルです。 */
        private final String label;

        /** スコアです。 */
        private final double score;

        /**
         * コンストラクタです。
         *
         * @param label ラベル
         * @param score スコア
         */
        private ScoreLabel(String label, double score) {
            this.label = label;
            this.score = score;
        }
    }

    /**
     * スタイル判定結果です。
     */
    private static class StyleDecision {

        /** クラスタIDです。 */
        private final Integer clusterId;

        /** ラベルです。 */
        private final String label;

        /** 信頼度です。 */
        private final BigDecimal confidence;

        /**
         * コンストラクタです。
         *
         * @param clusterId クラスタID
         * @param label ラベル
         * @param confidence 信頼度
         */
        private StyleDecision(Integer clusterId, String label, BigDecimal confidence) {
            this.clusterId = clusterId;
            this.label = label;
            this.confidence = confidence;
        }
    }

    /**
     * チーム集計用の内部クラスです。
     */
    private static class TeamStyleAccumulator {

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

        /** サンプル試合数です。 */
        private int sampleMatchCount;

        /** ポゼッション率合計です。 */
        private BigDecimal possessionRateSum = BigDecimal.ZERO;

        /** 合計分数です。 */
        private int totalMinutes;

        /** 合計パス数です。 */
        private int totalPasses;

        /** 合計ロングパス数です。 */
        private int totalLongPasses;

        /** 合計ファイナルサードパス数です。 */
        private int totalFinalThirdPasses;

        /** 合計クロス数です。 */
        private int totalCrosses;

        /** 合計シュート数です。 */
        private int totalShots;

        /** 合計ボックスタッチ数です。 */
        private int totalBoxTouches;

        /** 合計タックル数です。 */
        private int totalTackles;

        /** 合計インターセプト数です。 */
        private int totalInterceptions;

        /** 合計クリア数です。 */
        private int totalClearances;

        /** 合計デュエル数です。 */
        private int totalDuels;

        /** 集計開始日です。 */
        private LocalDate fromDate;

        /** 集計終了日です。 */
        private LocalDate toDate;

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
        private TeamStyleAccumulator(
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
         * 1試合分の集計を反映します。
         *
         * @param actualMinutes 実プレー分数
         * @param matchDate 試合日
         * @param possessionRate ポゼッション率
         * @param passes パス数
         * @param longPasses ロングパス数
         * @param finalThirdPasses ファイナルサードパス数
         * @param crosses クロス数
         * @param shots シュート数
         * @param boxTouches ボックスタッチ数
         * @param tackles タックル数
         * @param interceptions インターセプト数
         * @param clearances クリア数
         * @param duels デュエル数
         * @param note 備考
         */
        private void accept(
                Integer actualMinutes,
                LocalDate matchDate,
                BigDecimal possessionRate,
                Integer passes,
                Integer longPasses,
                Integer finalThirdPasses,
                Integer crosses,
                Integer shots,
                Integer boxTouches,
                Integer tackles,
                Integer interceptions,
                Integer clearances,
                Integer duels,
                String note) {

            this.sampleMatchCount++;
            this.possessionRateSum = this.possessionRateSum.add(possessionRate == null ? BigDecimal.ZERO : possessionRate);
            this.totalMinutes += (actualMinutes == null ? 0 : actualMinutes);
            this.totalPasses += (passes == null ? 0 : passes);
            this.totalLongPasses += (longPasses == null ? 0 : longPasses);
            this.totalFinalThirdPasses += (finalThirdPasses == null ? 0 : finalThirdPasses);
            this.totalCrosses += (crosses == null ? 0 : crosses);
            this.totalShots += (shots == null ? 0 : shots);
            this.totalBoxTouches += (boxTouches == null ? 0 : boxTouches);
            this.totalTackles += (tackles == null ? 0 : tackles);
            this.totalInterceptions += (interceptions == null ? 0 : interceptions);
            this.totalClearances += (clearances == null ? 0 : clearances);
            this.totalDuels += (duels == null ? 0 : duels);

            if (matchDate != null) {
                if (this.fromDate == null || matchDate.isBefore(this.fromDate)) {
                    this.fromDate = matchDate;
                }
                if (this.toDate == null || matchDate.isAfter(this.toDate)) {
                    this.toDate = matchDate;
                }
            }

            if (note != null && !note.isBlank() && this.noteSamples.size() < 5) {
                this.noteSamples.add(note);
            }
        }
    }
}
