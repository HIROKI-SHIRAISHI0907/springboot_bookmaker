package dev.application.analyze.bm_m040;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * チームのプレースタイルプロファイルを表すEntityです。
 *
 * <p>シーズンや期間単位で集計したスタイル特徴量と、
 * クラスタリング結果のラベルを保持します。</p>
 */
@Data
public class TeamStyleProfileEntity {

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
