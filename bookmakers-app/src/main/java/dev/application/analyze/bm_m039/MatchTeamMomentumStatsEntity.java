package dev.application.analyze.bm_m039;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import dev.application.analyze.common.entity.StatMetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 試合中の勢い・モメンタム統計を表すEntityです。
 *
 * <p>1行が「1試合・1チーム・1時点・1窓幅」を表します。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MatchTeamMomentumStatsEntity extends StatMetaEntity {

    /**
     * 主キーです。
     */
    private Integer id;

    /**
     * シーズンです。
     */
    private String season;

    /**
     * 集計時点の経過秒です。
     */
    private Integer asOfSeconds;

    /**
     * 集計窓の分数です。
     */
    private Integer windowMinutes;

    /**
     * 直近窓のシュート差です。
     */
    private Integer recentShotsDiff;

    /**
     * 直近窓の枠内シュート差です。
     */
    private Integer recentShotsOnTargetDiff;

    /**
     * 直近窓のボックスタッチ差です。
     */
    private Integer recentBoxTouchesDiff;

    /**
     * 直近窓のコーナー差です。
     */
    private Integer recentCornersDiff;

    /**
     * 直近窓の前進量差です。
     */
    private BigDecimal recentProgressionDiff;

    /**
     * 得点後の攻撃反応値です。
     */
    private BigDecimal postGoalAttackResponse;

    /**
     * 失点後の攻撃反応値です。
     */
    private BigDecimal postConcededAttackResponse;

    /**
     * モメンタム指数です。
     */
    private BigDecimal momentumIndex;

    /**
     * モメンタム傾向です。
     */
    private String momentumTrend;

    /**
     * 算出日時です。
     */
    private LocalDateTime calculatedAt;
}
