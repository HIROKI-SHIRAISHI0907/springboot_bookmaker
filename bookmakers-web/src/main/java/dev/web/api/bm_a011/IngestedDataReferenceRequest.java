package dev.web.api.bm_a011;

import lombok.Data;

@Data
public class IngestedDataReferenceRequest {

	/** 国 */
    private String country;

    /** future_master も含めるか */
    private boolean includeFutureMaster = true;

    /** data も含めるか */
    private boolean includeData = true;

    /** page */
    private int offset = 0;

    /** size */
    private int limit = 100;

    /** keyword */
    private String keyword;               // 部分一致検索（任意）

    /** onlyNeedsAttention */
    private Boolean onlyNeedsAttention;   // trueなら要対応のみ（任意）

}
