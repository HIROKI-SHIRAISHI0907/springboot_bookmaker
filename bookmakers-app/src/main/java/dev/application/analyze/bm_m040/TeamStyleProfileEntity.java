package dev.application.analyze.bm_m040;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import dev.application.analyze.common.entity.StatMetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * チームのプレースタイルプロファイルを表すEntityです。
 *
 * <p>シーズンや期間単位で集計したスタイル特徴量と、
 * クラスタリング結果のラベルを保持します。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TeamStyleProfileEntity extends StatMetaEntity {

    /**
     * 主キーです。
     */
    private Integer id;

    /**
     * 集計対象期間開始日です。
     */
    private LocalDate fromDate;

    /**
     * 集計対象期間終了日です。
     */
    private LocalDate toDate;

    /**
     * ポゼッション率です。
     */
    private BigDecimal possessionRate;

    /**
     * 90分換算パス数です。
     */
    private BigDecimal passesPer90;

    /**
     * ロングパス率です。
     */
    private BigDecimal longPassRate;

    /**
     * ファイナルサードパス率です。
     */
    private BigDecimal finalThirdPassRate;

    /**
     * クロス率です。
     */
    private BigDecimal crossRate;

    /**
     * ボックスタッチあたりシュート数です。
     */
    private BigDecimal shotsPerBoxTouch;

    /**
     * 守備強度です。
     */
    private BigDecimal defensiveActionIntensity;

    /**
     * クリア率です。
     */
    private BigDecimal clearanceRate;

    /**
     * デュエル強度です。
     */
    private BigDecimal duelIntensity;

    /**
     * スタイルクラスタIDです。
     */
    private Integer styleClusterId;

    /**
     * スタイルラベルです。
     */
    private String styleLabel;

    /**
     * スタイル判定信頼度です。
     */
    private BigDecimal styleConfidence;

    /**
     * 集計対象試合数です。
     */
    private Integer sampleMatchCount;

    /**
     * 算出日時です。
     */
    private LocalDateTime calculatedAt;

    /**
     * 備考です。
     */
    private String styleNote;

}
