package dev.application.analyze.bm_m044;

import java.math.BigDecimal;

import lombok.Data;

/**
 * モデルのキャリブレーション詳細を表すEntityです。
 *
 * <p>予測確率のビンごとの平均予測値と実測率を保持し、
 * 確率の校正確認に利用します。</p>
 */
@Data
public class ModelCalibrationDetailEntity {

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
     * 目的変数名です。
     */
    private String targetName;

    /**
     * ビン番号です。
     */
    private Integer binIndex;

    /**
     * 平均予測確率です。
     */
    private BigDecimal predictedProbAvg;

    /**
     * 実測率です。
     */
    private BigDecimal actualRate;

    /**
     * サンプル件数です。
     */
    private Integer sampleCount;

}
