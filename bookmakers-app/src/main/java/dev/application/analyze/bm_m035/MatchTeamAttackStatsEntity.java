package dev.application.analyze.bm_m035;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import dev.application.analyze.common.entity.StatMetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 試合単位の攻撃生成力統計を表すEntityです。
 *
 * <p>1行が「1試合・1チーム」の攻撃ボリュームを表します。
 * シュート生成力や攻撃量指数の分析に利用します。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MatchTeamAttackStatsEntity extends StatMetaEntity {

    /**
     * 主キーです。
     */
    private Integer id;

    /**
     * シーズンです。
     */
    private String season;

    /**
     * 実プレー換算分数です。
     */
    private Integer actualMinutes;

    /**
     * シュート数です。
     */
    private Integer shotsCount;

    /**
     * 90分換算シュート数です。
     */
    private BigDecimal shotsPer90;

    /**
     * 枠内シュート数です。
     */
    private Integer shotsOnTargetCount;

    /**
     * 90分換算枠内シュート数です。
     */
    private BigDecimal shotsOnTargetPer90;

    /**
     * ボックスタッチ数です。
     */
    private Integer boxTouchesCount;

    /**
     * 90分換算ボックスタッチ数です。
     */
    private BigDecimal boxTouchesPer90;

    /**
     * コーナーキック数です。
     */
    private Integer cornersCount;

    /**
     * 90分換算コーナーキック数です。
     */
    private BigDecimal cornersPer90;

    /**
     * ファイナルサードパス数です。
     */
    private Integer finalThirdPassesCount;

    /**
     * 90分換算ファイナルサードパス数です。
     */
    private BigDecimal finalThirdPassesPer90;

    /**
     * クロス数です。
     */
    private Integer crossesCount;

    /**
     * 90分換算クロス数です。
     */
    private BigDecimal crossesPer90;

    /**
     * 攻撃量指数です。
     */
    private BigDecimal attackVolumeIndex;

    /**
     * 算出日時です。
     */
    private LocalDateTime calculatedAt;

    /**
     * 元データ件数です。
     */
    private Integer sourceCount;

    /**
     * 備考です。
     */
    private String note;

}
