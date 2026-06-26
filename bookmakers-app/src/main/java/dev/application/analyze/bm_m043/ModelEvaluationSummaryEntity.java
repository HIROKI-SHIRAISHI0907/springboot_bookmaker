package dev.application.analyze.bm_m043;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import dev.application.analyze.common.util.ModelTaskType;
import lombok.Data;

/**
 * 予測モデル評価サマリを表すEntityです。
 *
 * <p>分類モデル・回帰モデルの評価指標をモデル単位で保持します。</p>
 */
@Data
public class ModelEvaluationSummaryEntity {

    /**
     * 主キーです。
     */
    private Integer id;

    /**
     * モデル名です。
     */
    private String modelName;

    /**
     * モデルバージョンです。
     */
    private String modelVersion;

    /**
     * タスク種別です。
     */
    private ModelTaskType taskType;

    /**
     * 目的変数名です。
     */
    private String targetName;

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
     * 対象シーズン範囲です。
     */
    private String seasonRange;

    /**
     * 検証方法です。
     */
    private String validationMethod;

    /**
     * Accuracyです。
     */
    private BigDecimal accuracy;

    /**
     * Precisionです。
     */
    private BigDecimal precisionScore;

    /**
     * Recallです。
     */
    private BigDecimal recallScore;

    /**
     * F1値です。
     */
    private BigDecimal f1Score;

    /**
     * ROC-AUCです。
     */
    private BigDecimal rocAuc;

    /**
     * PR-AUCです。
     */
    private BigDecimal prAuc;

    /**
     * Brier Scoreです。
     */
    private BigDecimal brierScore;

    /**
     * MAEです。
     */
    private BigDecimal mae;

    /**
     * RMSEです。
     */
    private BigDecimal rmse;

    /**
     * MAPEです。
     */
    private BigDecimal mape;

    /**
     * 決定係数 R2 です。
     */
    private BigDecimal r2Score;

    /**
     * 評価実施日時です。
     */
    private LocalDateTime evaluatedAt;

    /**
     * 備考です。
     */
    private String note;

}
