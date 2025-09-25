package dev.application.common;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.common.entity.BookDataEntity;

/**
 * SurfaceOverviewStat のためのテストデータ組み立てユーティリティ。
 * 使い方:
 *   Map<String, Map<String, List<BookDataEntity>>> data =
 *       SurfaceOverviewTestData.builder()
 *         .addMatchSnapshots("Japan","J1 League","Kawasaki Frontale","Urawa Reds",
 *                            new int[]{0,0}, new int[]{1,0}, new int[]{2,1},
 *                            8, "2025-02-06 07:25:58",
 *                            "終了済", "cases/A.csv")
 *         .addMatchSnapshots("England","Premier League","Arsenal","Chelsea",
 *                            new int[]{0,0}, new int[]{0,1}, new int[]{2,1},
 *                            17, "2025-03-10 21:10:00",
 *                            "終了済", "cases/B.csv")
 *         .build();
 */
public final class SurfaceOverviewTestData {

    private SurfaceOverviewTestData() {}

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Map<String, List<BookDataEntity>>> outer = new LinkedHashMap<>();

        /**
         * 1試合分（最小/中間/最大＝序盤/HT/終了）のスナップショットを追加。
         *
         * @param country 国
         * @param league リーグ
         * @param home ホーム名
         * @param away アウェー名
         * @param minScore  序盤スコア [home, away]
         * @param midScore  HTスコア   [home, away]
         * @param maxScore  終了スコア [home, away]
         * @param roundNo   ラウンド番号（gameTeamCategory 用）
         * @param finalRecordTime 終了時刻（"YYYY-MM-DD HH:mm:ss"）
         * @param midValue BookMakersCommonConst.HALF_TIME 相当（例: "ハーフタイム"）
         * @param timeValue BookMakersCommonConst.FIN 相当（例: "終了済"）
         * @param filePath  任意のダミーパス
         */
        public Builder addMatchSnapshots(
                String country, String league, String home, String away,
                int[] minScore, int[] midScore, int[] maxScore,
                int roundNo, String finalRecordTime, String minValue,
                String midValue, String timeValue, String filePath) {

            String outerKey = roundCategory(country, league, roundNo);
            String innerKey = home + " - " + away;

            List<BookDataEntity> list = createSnapshots(
                    country, league, home, away,
                    minScore, midScore, maxScore,
                    roundNo, finalRecordTime, minValue,
                    midValue, timeValue, filePath
            );

            outer.computeIfAbsent(outerKey, k -> new LinkedHashMap<>())
                 .put(innerKey, list);
            return this;
        }

        /** 完成したデータ構造を返す */
        public Map<String, Map<String, List<BookDataEntity>>> build() {
            return outer;
        }
    }

    // ----------------- 内部ヘルパー -----------------

    private static List<BookDataEntity> createSnapshots(
            String country, String league, String home, String away,
            int[] minScore, int[] midScore, int[] maxScore,
            int roundNo, String finalRecordTime, String minValue,
            String midValue, String timeValue, String filePath) {

        String tMin = shiftMin(finalRecordTime, -2);
        String tMid = shiftMin(finalRecordTime, -1);
        String tMax = finalRecordTime;

        BookDataEntity eMin = newEntity("1", roundCategory(country, league, roundNo),
                home, away, s(minScore[0]), s(minScore[1]), minValue, tMin, filePath);
        BookDataEntity eMid = newEntity("2", roundCategory(country, league, roundNo),
                home, away, s(midScore[0]), s(midScore[1]), midValue, tMid, filePath);
        BookDataEntity eMax = newEntity("3", roundCategory(country, league, roundNo),
                home, away, s(maxScore[0]), s(maxScore[1]), timeValue, tMax, filePath);

        return Arrays.asList(eMin, eMid, eMax);
    }

    private static BookDataEntity newEntity(
            String seq, String gameTeamCategory,
            String homeTeam, String awayTeam,
            String homeScore, String awayScore,
            String time, String recordTime, String filePath) {

        BookDataEntity e = new BookDataEntity();
        e.setSeq(seq);
        e.setGameTeamCategory(gameTeamCategory);
        e.setHomeTeamName(homeTeam);
        e.setAwayTeamName(awayTeam);
        e.setHomeScore(homeScore);
        e.setAwayScore(awayScore);
        e.setTime(time);             // 例: BookMakersCommonConst.FIN or "終了済"
        e.setRecordTime(recordTime); // 例: "2025-02-06 07:25:58"
        e.setFilePath(filePath);
        return e;
    }

    /** "Country: League - ラウンド N"（SurfaceOverviewStat.tryGetRoundNo 互換） */
    private static String roundCategory(String country, String league, int roundNo) {
        return country + ": " + league + " - ラウンド " + roundNo;
    }

    private static String s(int v) { return Integer.toString(v); }

    /** 分だけずらす超簡易版（テスト用途）。フォーマットは維持。 */
    private static String shiftMin(String base, int minutes) {
        try {
            String[] a = base.split(" ");
            String date = a[0];
            String[] hms = a[1].split(":");
            int h = Integer.parseInt(hms[0]);
            int m = Integer.parseInt(hms[1]) + minutes;
            String s = hms[2];

            while (m < 0) { m += 60; h -= 1; }
            while (m >= 60) { m -= 60; h += 1; }
            if (h < 0) h = 0;
            if (h > 23) h = 23;

            return String.format("%s %02d:%02d:%s", date, h, m, s);
        } catch (Exception ignore) {
            return base;
        }
    }
}
