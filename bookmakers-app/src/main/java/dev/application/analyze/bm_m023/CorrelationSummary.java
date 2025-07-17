package dev.application.analyze.bm_m023;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 相関係数詳細データ指定するSummary
 * @author shiraishitoshio
 *
 */
@AllArgsConstructor
@Data
public class CorrelationSummary {
	/** 2乗の和(新規データが登録された際にすぐに計算するように保存) */
    private String summationOfSecondPowerX;
    /** 積の和(新規データが登録された際にすぐに計算するように保存) */
    private String summationOfTimes;
    /** 2乗の和(新規データが登録された際にすぐに計算するように保存) */
    private String summationOfSecondPowerY;
    /** 相関係数 */
    private String correlation;
    /** 件数 */
    private int count;
}
