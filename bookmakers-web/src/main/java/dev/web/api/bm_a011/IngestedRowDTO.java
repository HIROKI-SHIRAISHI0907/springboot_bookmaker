package dev.web.api.bm_a011;

import lombok.Data;

@Data
public class IngestedRowDTO {

    public enum TableName {
        FUTURE_MASTER,
        DATA
    }

    private TableName table;

    /** 主キー（future_master.seq / data.seq） */
    private String seq;

    /** future_master 側サマリ */
    private FutureMasterIngestSummaryDTO future;

    /** data 側サマリ */
    private DataIngestSummaryDTO data;

    /** matchKey */
    private String matchKey;

    /** 同一試合群に future_master が存在するか */
    private Boolean futureExists;

    /** 同一試合群に「終了済」data が存在するか */
    private Boolean hasFinishedData;

}
