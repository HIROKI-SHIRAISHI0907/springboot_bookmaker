package dev.application.analyze.bm_m036;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 試合単位の攻撃効率統計を表すEntityです。
 *
 * <p>少ない攻撃でどれだけ質の高い機会を作れているかを定量化します。</p>
 */
@Data
public class MatchTeamEfficiencyStatsEntity {

    /**
     * 主キーです。
     */
    private Integer id;

    /**
     * 試合IDです。
     */
    private String matchId;

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
     * 対戦相手チームIDです。
     */
    private Long opponentTeamId;

    /**
     * 対戦相手チーム名です。
     */
    private String opponentTeamName;

    /**
     * 得点数です。
     */
    private Integer goalsCount;

    /**
     * シュート数です。
     */
    private Integer shotsCount;

    /**
     * 枠内シュート数です。
     */
    private Integer shotsOnTargetCount;

    /**
     * 枠外シュート数です。
     */
    private Integer shotsOffTargetCount;

    /**
     * ボックス内シュート数です。
     */
    private Integer boxShotsCount;

    /**
     * ボックス外シュート数です。
     */
    private Integer nonBoxShotsCount;

    /**
     * ボックスタッチ数です。
     */
    private Integer boxTouchesCount;

    /**
     * セットプレー由来シュート数です。
     */
    private Integer setPieceShotsCount;

    /**
     * セットプレー由来得点数です。
     */
    private Integer setPieceGoalsCount;

    /**
     * 枠内率です。
     */
    private BigDecimal onTargetRate;

    /**
     * 枠外率です。
     */
    private BigDecimal offTargetRate;

    /**
     * ボックス内シュート率です。
     */
    private BigDecimal boxShotRate;

    /**
     * ボックスタッチからシュートへの変換率です。
     */
    private BigDecimal boxTouchToShotRate;

    /**
     * シュートから得点への変換率です。
     */
    private BigDecimal shotToGoalRate;

    /**
     * 枠内シュートから得点への変換率です。
     */
    private BigDecimal onTargetToGoalRate;

    /**
     * セットプレー依存率です。
     */
    private BigDecimal setPieceDependencyRate;

    /**
     * 効率評価備考です。
     */
    private String efficiencyNote;

    /**
     * 算出日時です。
     */
    private LocalDateTime calculatedAt;

}
