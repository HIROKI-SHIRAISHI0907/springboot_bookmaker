package dev.application.analyze.bm_m041;

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
 * チームの強度・安定性評価プロファイルを算出するサービスです。
 *
 * <p>試合終了時点の最終スナップショットを用いて、チームごとの
 * 直近成績、ホーム/アウェー強度、上位相手成績、下位相手取りこぼし率、
 * Elo風レーティング、フォーム指数を集計します。</p>
 *
 * <p>なお、各指標は暫定的なルールベース算出です。
 * 将来的に高度なレーティングモデルへ置き換え可能な構成です。</p>
 */
@Component
@RequiredArgsConstructor
public class TeamStrengthProfileStat implements AnalyzeEntityIF {

    /** 小数スケールです。 */
    private static final int SCALE = 6;

    /** 記録日時フォーマット候補1です。 */
    private static final DateTimeFormatter RECORD_TIME_FORMATTER_1 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX");

    /** 記録日時フォーマット候補2です。 */
    private static final DateTimeFormatter RECORD_TIME_FORMATTER_2 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX");

    /** Writer です。 */
    private final TeamStrengthProfileWriter teamStrengthProfileWriter;

    /**
     * チーム強度プロファイルを算出します。
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
     * リーグ単位のデータからチームごとの強度プロファイルを作成・保存します。
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

        Map<String, TeamStrengthAccumulator> accumulatorMap = new LinkedHashMap<>();
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

            Integer homeRank = readInteger(finalEntity,
                    "homeRank", "homeTeamRank", "ホーム順位");
            Integer awayRank = readInteger(finalEntity,
                    "awayRank", "awayTeamRank", "アウェー順位");

            String noteFragment = buildNoteFragment(finalEntity);

            TeamStrengthAccumulator homeAcc = accumulatorMap.computeIfAbsent(
                    buildTeamKey(country, leagueId, homeTeamId, homeTeamName),
                    k -> new TeamStrengthAccumulator(country, leagueId, leagueName, homeTeamId, homeTeamName));

            TeamStrengthAccumulator awayAcc = accumulatorMap.computeIfAbsent(
                    buildTeamKey(country, leagueId, awayTeamId, awayTeamName),
                    k -> new TeamStrengthAccumulator(country, leagueId, leagueName, awayTeamId, awayTeamName));

            int homePoints = calcPoints(homeScore, awayScore);
            int awayPoints = calcPoints(awayScore, homeScore);

            int homeGoalDiff = homeScore - awayScore;
            int awayGoalDiff = awayScore - homeScore;

            homeAcc.accept(
                    matchDate,
                    true,
                    homeScore,
                    awayScore,
                    homePoints,
                    homeGoalDiff,
                    homeRank,
                    awayRank,
                    noteFragment);

            awayAcc.accept(
                    matchDate,
                    false,
                    awayScore,
                    homeScore,
                    awayPoints,
                    awayGoalDiff,
                    awayRank,
                    homeRank,
                    noteFragment);
        }

        for (TeamStrengthAccumulator accumulator : accumulatorMap.values()) {
            TeamStrengthProfileEntity entity = buildEntity(accumulator);
            teamStrengthProfileWriter.insert(entity);
        }
    }

    /**
     * 集計結果から Entity を構築します。
     *
     * @param acc 集計データ
     * @return TeamStrengthProfileEntity
     */
    private TeamStrengthProfileEntity buildEntity(TeamStrengthAccumulator acc) {

        TeamStrengthProfileEntity entity = new TeamStrengthProfileEntity();

        entity.setMatchId(null);
        entity.setCountry(acc.country);
        entity.setLeagueId(acc.leagueId);
        entity.setLeagueName(acc.leagueName);
        entity.setTeamId(acc.teamId);
        entity.setTeamName(acc.teamName);
        entity.setOpponentTeamId(null);
        entity.setOpponentTeamName(null);

        entity.setSnapshotDate(acc.snapshotDate);

        List<MatchResult> recent5 = resolveRecent5(acc.results);

        Integer last5Points = recent5.stream()
                .map(MatchResult::getPoints)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);

        Integer last5GoalDiff = recent5.stream()
                .map(MatchResult::getGoalDiff)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);

        BigDecimal homeStrengthIndex = calcStrengthIndex(
                acc.homePoints, acc.homeGoalDiff, acc.homeWins, acc.homeMatches);

        BigDecimal awayStrengthIndex = calcStrengthIndex(
                acc.awayPoints, acc.awayGoalDiff, acc.awayWins, acc.awayMatches);

        BigDecimal vsUpperPerformance = calcAveragePoints(acc.upperPoints, acc.upperMatches);
        BigDecimal vsLowerDropRate = calcDropRate(acc.lowerDroppedPoints, acc.lowerMatches);
        BigDecimal formIndex = calcFormIndex(last5Points, last5GoalDiff);
        BigDecimal eloLikeRating = calcEloLikeRating(acc, formIndex);

        entity.setLast5Points(last5Points);
        entity.setLast5GoalDiff(last5GoalDiff);
        entity.setHomeStrengthIndex(homeStrengthIndex);
        entity.setAwayStrengthIndex(awayStrengthIndex);
        entity.setVsUpperPerformance(vsUpperPerformance);
        entity.setVsLowerDropRate(vsLowerDropRate);
        entity.setEloLikeRating(eloLikeRating);
        entity.setFormIndex(formIndex);
        entity.setCalculatedAt(LocalDateTime.now());
        entity.setNote(buildNote(acc, recent5, homeStrengthIndex, awayStrengthIndex, vsUpperPerformance, vsLowerDropRate));

        return entity;
    }

    /**
     * 直近5試合を取得します。
     *
     * @param results 試合結果一覧
     * @return 直近5試合
     */
    private List<MatchResult> resolveRecent5(List<MatchResult> results) {

        List<MatchResult> sorted = new ArrayList<>(results);
        sorted.sort(Comparator.comparing(MatchResult::getMatchDate,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return sorted.subList(0, Math.min(5, sorted.size()));
    }

    /**
     * 強度指数を算出します。
     *
     * <p>ポイント/試合、得失点差/試合、勝率を合成した暫定指標です。</p>
     *
     * @param points 勝点
     * @param goalDiff 得失点差
     * @param wins 勝利数
     * @param matches 試合数
     * @return 強度指数
     */
    private BigDecimal calcStrengthIndex(Integer points, Integer goalDiff, Integer wins, Integer matches) {

        if (matches == null || matches == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal pointsPerGame = divide(points, matches);
        BigDecimal goalDiffPerGame = divide(goalDiff, matches);
        BigDecimal winRate = divide(wins, matches);

        return pointsPerGame.multiply(BigDecimal.valueOf(0.60d))
                .add(goalDiffPerGame.multiply(BigDecimal.valueOf(0.30d)))
                .add(winRate.multiply(BigDecimal.valueOf(0.10d)))
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 上位相手時パフォーマンスを算出します。
     *
     * <p>上位相手戦の平均勝点です。</p>
     *
     * @param upperPoints 上位相手戦勝点合計
     * @param upperMatches 上位相手戦試合数
     * @return 平均勝点
     */
    private BigDecimal calcAveragePoints(Integer upperPoints, Integer upperMatches) {
        return divide(upperPoints, upperMatches);
    }

    /**
     * 下位相手取りこぼし率を算出します。
     *
     * <p>下位相手戦で失った勝点 / 最大勝点(3×試合数) です。</p>
     *
     * @param droppedPoints 取りこぼし勝点
     * @param lowerMatches 下位相手戦試合数
     * @return 取りこぼし率
     */
    private BigDecimal calcDropRate(Integer droppedPoints, Integer lowerMatches) {

        if (lowerMatches == null || lowerMatches == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(safeInt(droppedPoints))
                .divide(BigDecimal.valueOf(lowerMatches * 3L), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * フォーム指数を算出します。
     *
     * <p>直近5試合勝点と得失点差から 0～1 の範囲で正規化します。</p>
     *
     * @param last5Points 直近5試合勝点
     * @param last5GoalDiff 直近5試合得失点差
     * @return フォーム指数
     */
    private BigDecimal calcFormIndex(Integer last5Points, Integer last5GoalDiff) {

        double pointScore = clamp(safeInt(last5Points) / 15.0d, 0.0d, 1.0d);
        double goalDiffScore = clamp((safeInt(last5GoalDiff) + 10.0d) / 20.0d, 0.0d, 1.0d);

        double form = (pointScore * 0.70d) + (goalDiffScore * 0.30d);

        return BigDecimal.valueOf(form).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Elo風レーティングを算出します。TODO
     *
     * <p>ベース1500に、平均勝点、平均得失点差、勝率、フォーム指数を加味した暫定値です。</p>
     *
     * @param acc 集計データ
     * @param formIndex フォーム指数
     * @return Elo風レーティング
     */
    private BigDecimal calcEloLikeRating(TeamStrengthAccumulator acc, BigDecimal formIndex) {

        if (acc.totalMatches == 0) {
            return BigDecimal.valueOf(1500).setScale(SCALE, RoundingMode.HALF_UP);
        }

        double pointsPerGame = divide(acc.totalPoints, acc.totalMatches).doubleValue();
        double goalDiffPerGame = divide(acc.totalGoalDiff, acc.totalMatches).doubleValue();
        double winRate = divide(acc.totalWins, acc.totalMatches).doubleValue();
        double form = formIndex == null ? 0.0d : formIndex.doubleValue();

        double rating = 1500.0d
                + ((pointsPerGame - 1.5d) * 100.0d)
                + (goalDiffPerGame * 25.0d)
                + (winRate * 50.0d)
                + (form * 30.0d);

        return BigDecimal.valueOf(rating).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 備考を作成します。
     *
     * @param acc 集計データ
     * @param recent5 直近5試合
     * @param homeStrengthIndex ホーム強度
     * @param awayStrengthIndex アウェー強度
     * @param vsUpperPerformance 上位相手成績
     * @param vsLowerDropRate 下位相手取りこぼし率
     * @return 備考
     */
    private String buildNote(
            TeamStrengthAccumulator acc,
            List<MatchResult> recent5,
            BigDecimal homeStrengthIndex,
            BigDecimal awayStrengthIndex,
            BigDecimal vsUpperPerformance,
            BigDecimal vsLowerDropRate) {

        StringBuilder sb = new StringBuilder();

        sb.append("ruleBasedStrengthProfile=true");
        sb.append(", totalMatches=").append(acc.totalMatches);
        sb.append(", homeMatches=").append(acc.homeMatches);
        sb.append(", awayMatches=").append(acc.awayMatches);
        sb.append(", upperMatches=").append(acc.upperMatches);
        sb.append(", lowerMatches=").append(acc.lowerMatches);
        sb.append(", recent5Count=").append(recent5 == null ? 0 : recent5.size());
        sb.append(", homeStrengthIndex=").append(homeStrengthIndex);
        sb.append(", awayStrengthIndex=").append(awayStrengthIndex);
        sb.append(", vsUpperPerformance=").append(vsUpperPerformance);
        sb.append(", vsLowerDropRate=").append(vsLowerDropRate);

        if (acc.noteSamples != null && !acc.noteSamples.isEmpty()) {
            sb.append(", source=").append(String.join(" | ", acc.noteSamples));
        }

        return sb.toString();
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
     * 試合の最終行を解決します。
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
     * 勝点を算出します。
     *
     * @param goalsFor 得点
     * @param goalsAgainst 失点
     * @return 勝点
     */
    private int calcPoints(Integer goalsFor, Integer goalsAgainst) {

        int gf = safeInt(goalsFor);
        int ga = safeInt(goalsAgainst);

        if (gf > ga) {
            return 3;
        }
        if (gf == ga) {
            return 1;
        }
        return 0;
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
     * 安全な除算です。
     *
     * @param numerator 分子
     * @param denominator 分母
     * @return 除算結果
     */
    private BigDecimal divide(Integer numerator, Integer denominator) {

        if (numerator == null || denominator == null || denominator == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 範囲制限です。
     *
     * @param value 値
     * @param min 最小
     * @param max 最大
     * @return 制限後
     */
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
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
     * null 安全な int 化です。
     *
     * @param value 値
     * @return int値
     */
    private int safeInt(Integer value) {
        return value == null ? 0 : value;
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
     * 試合結果の内部クラスです。
     */
    private static class MatchResult {

        /** 試合日です。 */
        private final LocalDate matchDate;

        /** 勝点です。 */
        private final Integer points;

        /** 得失点差です。 */
        private final Integer goalDiff;

        /**
         * コンストラクタです。
         *
         * @param matchDate 試合日
         * @param points 勝点
         * @param goalDiff 得失点差
         */
        private MatchResult(LocalDate matchDate, Integer points, Integer goalDiff) {
            this.matchDate = matchDate;
            this.points = points;
            this.goalDiff = goalDiff;
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
         * 勝点を返します。
         *
         * @return 勝点
         */
        public Integer getPoints() {
            return points;
        }

        /**
         * 得失点差を返します。
         *
         * @return 得失点差
         */
        public Integer getGoalDiff() {
            return goalDiff;
        }
    }

    /**
     * チーム集計用の内部クラスです。
     */
    private static class TeamStrengthAccumulator {

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

        /** 総勝点です。 */
        private int totalPoints;

        /** 総勝利数です。 */
        private int totalWins;

        /** 総得失点差です。 */
        private int totalGoalDiff;

        /** ホーム試合数です。 */
        private int homeMatches;

        /** ホーム勝点です。 */
        private int homePoints;

        /** ホーム勝利数です。 */
        private int homeWins;

        /** ホーム得失点差です。 */
        private int homeGoalDiff;

        /** アウェー試合数です。 */
        private int awayMatches;

        /** アウェー勝点です。 */
        private int awayPoints;

        /** アウェー勝利数です。 */
        private int awayWins;

        /** アウェー得失点差です。 */
        private int awayGoalDiff;

        /** 上位相手戦試合数です。 */
        private int upperMatches;

        /** 上位相手戦勝点です。 */
        private int upperPoints;

        /** 下位相手戦試合数です。 */
        private int lowerMatches;

        /** 下位相手取りこぼし勝点です。 */
        private int lowerDroppedPoints;

        /** 試合結果一覧です。 */
        private final List<MatchResult> results = new ArrayList<>();

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
        private TeamStrengthAccumulator(
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
         * @param points 勝点
         * @param goalDiff 得失点差
         * @param teamRank 自順位
         * @param opponentRank 相手順位
         * @param note 備考
         */
        private void accept(
                LocalDate matchDate,
                boolean homeFlg,
                Integer goalsFor,
                Integer goalsAgainst,
                Integer points,
                Integer goalDiff,
                Integer teamRank,
                Integer opponentRank,
                String note) {

            this.totalMatches++;
            this.totalPoints += safe(points);
            this.totalGoalDiff += safe(goalDiff);

            if (safe(points) == 3) {
                this.totalWins++;
            }

            if (homeFlg) {
                this.homeMatches++;
                this.homePoints += safe(points);
                this.homeGoalDiff += safe(goalDiff);
                if (safe(points) == 3) {
                    this.homeWins++;
                }
            } else {
                this.awayMatches++;
                this.awayPoints += safe(points);
                this.awayGoalDiff += safe(goalDiff);
                if (safe(points) == 3) {
                    this.awayWins++;
                }
            }

            if (teamRank != null && opponentRank != null) {
                if (opponentRank < teamRank) {
                    this.upperMatches++;
                    this.upperPoints += safe(points);
                } else if (opponentRank > teamRank) {
                    this.lowerMatches++;
                    this.lowerDroppedPoints += (3 - safe(points));
                }
            }

            this.results.add(new MatchResult(matchDate, points, goalDiff));

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
    }
}
