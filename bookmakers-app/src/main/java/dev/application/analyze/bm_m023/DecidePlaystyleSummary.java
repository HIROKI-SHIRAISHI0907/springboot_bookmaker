package dev.application.analyze.bm_m023;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * プレースタイルを決定する閾値を指定するSummary
 * @author shiraishitoshio
 *
 */
@AllArgsConstructor
@Data
public class DecidePlaystyleSummary {
    /** 平均値+標準偏差の閾値(上振れ) */
    private String thresHoldMaxInAvePlusOneSigma;
    /** 平均値-標準偏差の閾値(下振れ) */
    private String thresHoldMinInAvePlusOneSigma;
}
