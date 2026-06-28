package dev.application.analyze.bm_m037;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import dev.application.analyze.common.entity.StatMetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 試合単位の守備力・被圧力統計を表すEntityです。
 *
 * <p>被シュート、セーブ率、ブロック率、イベント後の被圧上昇などを管理します。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MatchTeamDefenseStatsEntity extends StatMetaEntity {

    /**
     * 主キーです。
     */
    private Integer id;

    /**
     * シーズンです。
     */
    private String season;

    /**
     * 被シュート数です。
     */
    private Integer shotsConcededCount;

    /**
     * 被枠内シュート数です。
     */
    private Integer shotsOnTargetConcededCount;

    /**
     * 被ボックスタッチ数です。
     */
    private Integer boxTouchesConcededCount;

    /**
     * キーパーセーブ数です。
     */
    private Integer keeperSavesCount;

    /**
     * セーブ率です。
     */
    private BigDecimal saveRate;

    /**
     * ブロックシュート数です。
     */
    private Integer blockedShotsCount;

    /**
     * ブロック率です。
     */
    private BigDecimal blockRate;

    /**
     * クリア数です。
     */
    private Integer clearancesCount;

    /**
     * クリア頻度です。
     */
    private BigDecimal clearanceFrequency;

    /**
     * タックル数です。
     */
    private Integer tacklesCount;

    /**
     * インターセプト数です。
     */
    private Integer interceptionsCount;

    /**
     * 守備アクション率です。
     */
    private BigDecimal defensiveActionRate;

    /**
     * リード時の被シュート増加率です。
     */
    private BigDecimal leadStateShotsConcededIncreaseRate;

    /**
     * 失点後10分の被攻撃量です。
     */
    private BigDecimal postConceded10mPressure;

    /**
     * 退場後10分の被圧力です。
     */
    private BigDecimal postRedCard10mPressure;

    /**
     * 算出日時です。
     */
    private LocalDateTime calculatedAt;

    /**
     * 備考です。
     */
    private String note;

}
