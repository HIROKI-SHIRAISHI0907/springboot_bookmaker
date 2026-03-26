package dev.web.api.bm_w015;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * /api/each-team-score API レスポンスDTO
 */
@Data
@NoArgsConstructor
public class EachTeamScoreResponseDTO {

    private String id = "";
    private String situation = "";
    private String score = "";
    private String country = "";
    private String league = "";
    private String team = "";

    private StatSummaryDTO homeExpStat = new StatSummaryDTO();
    private StatSummaryDTO awayExpStat = new StatSummaryDTO();

    private StatSummaryDTO homeInGoalExpStat = new StatSummaryDTO();
    private StatSummaryDTO awayInGoalExpStat = new StatSummaryDTO();

    private StatSummaryDTO homeDonationStat = new StatSummaryDTO();
    private StatSummaryDTO awayDonationStat = new StatSummaryDTO();

    private StatSummaryDTO homeShootAllStat = new StatSummaryDTO();
    private StatSummaryDTO awayShootAllStat = new StatSummaryDTO();

    private StatSummaryDTO homeShootInStat = new StatSummaryDTO();
    private StatSummaryDTO awayShootInStat = new StatSummaryDTO();

    private StatSummaryDTO homeShootOutStat = new StatSummaryDTO();
    private StatSummaryDTO awayShootOutStat = new StatSummaryDTO();

    private StatSummaryDTO homeBlockShootStat = new StatSummaryDTO();
    private StatSummaryDTO awayBlockShootStat = new StatSummaryDTO();

    private StatSummaryDTO homeBigChanceStat = new StatSummaryDTO();
    private StatSummaryDTO awayBigChanceStat = new StatSummaryDTO();

    private StatSummaryDTO homeCornerStat = new StatSummaryDTO();
    private StatSummaryDTO awayCornerStat = new StatSummaryDTO();

    private StatSummaryDTO homeBoxShootInStat = new StatSummaryDTO();
    private StatSummaryDTO awayBoxShootInStat = new StatSummaryDTO();

    private StatSummaryDTO homeBoxShootOutStat = new StatSummaryDTO();
    private StatSummaryDTO awayBoxShootOutStat = new StatSummaryDTO();

    private StatSummaryDTO homeGoalPostStat = new StatSummaryDTO();
    private StatSummaryDTO awayGoalPostStat = new StatSummaryDTO();

    private StatSummaryDTO homeGoalHeadStat = new StatSummaryDTO();
    private StatSummaryDTO awayGoalHeadStat = new StatSummaryDTO();

    private StatSummaryDTO homeKeeperSaveStat = new StatSummaryDTO();
    private StatSummaryDTO awayKeeperSaveStat = new StatSummaryDTO();

    private StatSummaryDTO homeFreeKickStat = new StatSummaryDTO();
    private StatSummaryDTO awayFreeKickStat = new StatSummaryDTO();

    private StatSummaryDTO homeOffsideStat = new StatSummaryDTO();
    private StatSummaryDTO awayOffsideStat = new StatSummaryDTO();

    private StatSummaryDTO homeFoulStat = new StatSummaryDTO();
    private StatSummaryDTO awayFoulStat = new StatSummaryDTO();

    private StatSummaryDTO homeYellowCardStat = new StatSummaryDTO();
    private StatSummaryDTO awayYellowCardStat = new StatSummaryDTO();

    private StatSummaryDTO homeRedCardStat = new StatSummaryDTO();
    private StatSummaryDTO awayRedCardStat = new StatSummaryDTO();

    private StatSummaryDTO homeSlowInStat = new StatSummaryDTO();
    private StatSummaryDTO awaySlowInStat = new StatSummaryDTO();

    private StatSummaryDTO homeBoxTouchStat = new StatSummaryDTO();
    private StatSummaryDTO awayBoxTouchStat = new StatSummaryDTO();

    private StatSummaryDTO homePassCountStat = new StatSummaryDTO();
    private StatSummaryDTO awayPassCountStat = new StatSummaryDTO();

    private StatSummaryDTO homeLongPassCountStat = new StatSummaryDTO();
    private StatSummaryDTO awayLongPassCountStat = new StatSummaryDTO();

    private StatSummaryDTO homeFinalThirdPassCountStat = new StatSummaryDTO();
    private StatSummaryDTO awayFinalThirdPassCountStat = new StatSummaryDTO();

    private StatSummaryDTO homeCrossCountStat = new StatSummaryDTO();
    private StatSummaryDTO awayCrossCountStat = new StatSummaryDTO();

    private StatSummaryDTO homeTackleCountStat = new StatSummaryDTO();
    private StatSummaryDTO awayTackleCountStat = new StatSummaryDTO();

    private StatSummaryDTO homeClearCountStat = new StatSummaryDTO();
    private StatSummaryDTO awayClearCountStat = new StatSummaryDTO();

    private StatSummaryDTO homeDuelCountStat = new StatSummaryDTO();
    private StatSummaryDTO awayDuelCountStat = new StatSummaryDTO();

    private StatSummaryDTO homeInterceptCountStat = new StatSummaryDTO();
    private StatSummaryDTO awayInterceptCountStat = new StatSummaryDTO();

    private String logicFlg = "";
    private String registerId = "";
    private LocalDateTime registerTime;
    private String updateId = "";
    private LocalDateTime updateTime;

    /**
     * 1セルに格納された統計文字列を表現するDTO
     *
     * 並び順:
     * min, count,
     * max, count,
     * avg, count,
     * stddev, count,
     * minTime, count,
     * maxTime, count,
     * skewness, count,
     * kurtosis, count
     */
    @Data
    public static class StatSummaryDTO {

        private String min = "";
        private String minCount = "";

        private String max = "";
        private String maxCount = "";

        private String avg = "";
        private String avgCount = "";

        private String stddev = "";
        private String stddevCount = "";

        private String minTime = "";
        private String minTimeCount = "";

        private String maxTime = "";
        private String maxTimeCount = "";

        private String skewness = "";
        private String skewnessCount = "";

        private String kurtosis = "";
        private String kurtosisCount = "";

        public StatSummaryDTO() {
        }


        /**
         * DBの "3,10,18,10,11.2,10,2.8,10,5,10,88,10,0.91,10,3.12,10"
         * のような文字列から DTO を生成
         */
        public static StatSummaryDTO fromRaw(String raw) {
            StatSummaryDTO dto = new StatSummaryDTO();

            if (raw == null || raw.isBlank()) {
                return dto;
            }

            String[] arr = raw.split(",");

            dto.setMin(get(arr, 0));
            dto.setMinCount(get(arr, 1));

            dto.setMax(get(arr, 2));
            dto.setMaxCount(get(arr, 3));

            dto.setAvg(get(arr, 4));
            dto.setAvgCount(get(arr, 5));

            dto.setStddev(get(arr, 6));
            dto.setStddevCount(get(arr, 7));

            dto.setMinTime(get(arr, 8));
            dto.setMinTimeCount(get(arr, 9));

            dto.setMaxTime(get(arr, 10));
            dto.setMaxTimeCount(get(arr, 11));

            dto.setSkewness(get(arr, 12));
            dto.setSkewnessCount(get(arr, 13));

            dto.setKurtosis(get(arr, 14));
            dto.setKurtosisCount(get(arr, 15));

            return dto;
        }

        private static String get(String[] arr, int index) {
            if (arr == null || index >= arr.length || arr[index] == null) {
                return "";
            }
            return arr[index].trim();
        }
    }
}
