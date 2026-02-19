package dev.web.api.bm_a011;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.Data;

@Data
public class IngestedDataReferenceResponse {

    private OffsetDateTime from;

    private OffsetDateTime to;

    /** 新しい順 */
    private List<IngestedRowDTO> rows;

    /** 全件数（ページング用） */
    private long total;

}
