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

}
