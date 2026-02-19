package dev.web.api.bm_a011;

import java.time.OffsetDateTime;

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

    /** 投入（登録）日時 */
    private OffsetDateTime registerTime;

    /** 更新日時（あれば） */
    private OffsetDateTime updateTime;

    /** 一覧用サマリ */
    private FutureMasterIngestSummaryDTO future;
    private DataIngestSummaryDTO data;

}
