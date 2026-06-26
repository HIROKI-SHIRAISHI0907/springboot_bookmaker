package dev.application.analyze.bm_m042;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 試合前レポート用の要約統計プロファイルを表すEntityです。
 *
 * <p>試合前総評生成に必要な直近成績や先制率などの要約指標を保持します。</p>
 */
@Data
public class PreMatchSummaryProfileEntity {

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
     * 直近5試合の勝敗文字列です。
     */
    private String last5ResultString;

    /**
     * 直近5試合平均得点です。
     */
    private BigDecimal last5AvgGoals;

    /**
     * 直近5試合平均失点です。
     */
    private BigDecimal last5AvgGoalsConceded;

    /**
     * 先制率です。
     */
    private BigDecimal firstGoalRate;

    /**
     * 先制時勝率です。
     */
    private BigDecimal winAfterScoringFirstRate;

    /**
     * 追いつき率です。
     */
    private BigDecimal comebackRate;

    /**
     * 終盤得点率です。
     */
    private BigDecimal lateScoringRate;

    /**
     * 終盤失点率です。
     */
    private BigDecimal lateConcedingRate;

    /**
     * セットプレー得点関与率です。
     */
    private BigDecimal setPieceGoalInvolvementRate;

    /**
     * クリーンシート率です。
     */
    private BigDecimal cleanSheetRate;

    /**
     * 両チーム得点率です。
     */
    private BigDecimal bothTeamsToScoreRate;

    /**
     * 算出日時です。
     */
    private LocalDateTime calculatedAt;

    /**
     * 備考です。
     */
    private String note;

}
