package dev.application.analyze.bm_m036;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import dev.application.analyze.common.entity.StatMetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 試合単位の攻撃効率統計を表すEntityです。
 *
 * <p>少ない攻撃でどれだけ質の高い機会を作れているかを定量化します。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MatchTeamEfficiencyStatsEntity extends StatMetaEntity {

    /**
     * 主キーです。
     */
    private Integer id;

    /**
     * シーズンです。
     */
    private String season;

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
