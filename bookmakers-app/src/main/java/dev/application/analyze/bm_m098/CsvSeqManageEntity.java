package dev.application.analyze.bm_m098;

import lombok.Data;

@Data
public class CsvSeqManageEntity {

    private Integer id;

    private String jobName;

    private Integer lastSuccessCsv;

    private Integer backRange;

}
