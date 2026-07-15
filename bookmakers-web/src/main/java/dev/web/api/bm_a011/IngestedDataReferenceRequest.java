package dev.web.api.bm_a011;

import lombok.Data;

@Data
public class IngestedDataReferenceRequest {

    /** 国 */
    private String country;

    /**
     * true:
     *   「終了済」データがない、または未来データがない
     *   のいずれかを満たすものだけ返す
     *
     * false:
     *   条件を考慮せず、国に該当するものを全件返す
     */
    private boolean onlyMissingFinishedOrFuture = false;

    /** page offset */
    private int offset = 0;

    /** page size */
    private int limit = 100;
}
