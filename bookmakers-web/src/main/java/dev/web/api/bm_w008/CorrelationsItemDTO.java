package dev.web.api.bm_w008;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 相関係数のDTO
 * /api/{country}/{league}/{team}/correlations
 *
 * @author shiraishitoshio
 */
@Data
@AllArgsConstructor
public class CorrelationsItemDTO {

	/** メトリック */
    private String metric;

    /** 値 */
    private double value;
}
