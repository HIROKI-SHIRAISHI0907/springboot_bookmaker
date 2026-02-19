package dev.web.api.bm_a011;

import java.time.OffsetDateTime;

import lombok.Data;

@Data
public class IngestedDataReferenceRequest {

    /** 期間開始（未指定なら to-7days） */
    private OffsetDateTime from;

    /** 期間終了（未指定なら now） */
    private OffsetDateTime to;

    /** future_master も含めるか */
    private boolean includeFutureMaster = true;

    /** data も含めるか */
    private boolean includeData = true;

    /** page */
    private int offset = 0;

    /** size */
    private int limit = 100;

}
