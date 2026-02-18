package dev.application.analyze.bm_m098;

import java.io.Serializable;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class CsvSeqManageEntity extends MetaEntity implements Serializable {

	/** ID */
    private Integer id;

    /** ジョブクラス名 */
    private String jobName;

    /** 最終成功CSV番号 */
    private Integer lastSuccessCsv;

    /** 取り戻し範囲 */
    private Integer backRange;

}
