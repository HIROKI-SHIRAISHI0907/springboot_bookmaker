package dev.application.analyze.bm_m023;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 最小値,最大値,平均,標準偏差を持つサブEntity
 * @author shiraishitoshio
 *
 */
@AllArgsConstructor
@Data
public class StatSummary {
	/** 最小値 */
    private String min;
    /** 最大値 */
    private String max;
    /** 平均値 */
    private String mean;
    /** 標準偏差 */
    private String sigma;
    /** 件数（平均や標準偏差の計算に使用されたデータ数） */
    private int count;
    /** 特徴量取得時間最小値 */
    private String featureTimeMin;
    /** 特徴量取得時間最大値 */
    private String featureTimeMax;
    /** 特徴量取得時間平均値 */
    private String featureTimeMean;
    /** 特徴量取得時間標準偏差 */
    private String featureTimeSigma;
    /** 特徴量取得時間件数（平均や標準偏差の計算に使用されたデータ数） */
    private int featureCount;
}
