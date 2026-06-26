package dev.application.analyze.bm_m041;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * チームの強度・安定性評価プロファイルを表すEntityです。
 *
 * <p>直近成績、ホーム/アウェー強度、上位相手成績などを管理します。</p>
 */
@Data
public class TeamStrengthProfileEntity {

    /**
     * 主キーです。
     */
    private Integer id;

    /**
     * シーズンです。
     */
    private String season;

    /**
     * 国です。
     */
    private String country;

    /**
     * リーグIDです。
     */
    private String leagueId;

    /**
     * リーグ名です。
     */
    private String leagueName;

    /**
     * チームIDです。
     */
    private String teamId;

    /**
     * チーム名です。
     */
    private String teamName;

    /**
     * 集計基準日です。
     */
    private LocalDate snapshotDate;

    /**
     * 直近5試合勝点です。
     */
    private Integer last5Points;

    /**
     * 直近5試合得失点差です。
     */
    private Integer last5GoalDiff;

    /**
     * ホーム専用強度指数です。
     */
    private BigDecimal homeStrengthIndex;

    /**
     * アウェー専用強度指数です。
     */
    private BigDecimal awayStrengthIndex;

    /**
     * 上位相手時パフォーマンスです。
     */
    private BigDecimal vsUpperPerformance;

    /**
     * 下位相手取りこぼし率です。
     */
    private BigDecimal vsLowerDropRate;

    /**
     * Elo風レーティングです。
     */
    private BigDecimal eloLikeRating;

    /**
     * フォーム指数です。
     */
    private BigDecimal formIndex;

    /**
     * 算出日時です。
     */
    private LocalDateTime calculatedAt;

    /**
     * 備考です。
     */
    private String note;

}
