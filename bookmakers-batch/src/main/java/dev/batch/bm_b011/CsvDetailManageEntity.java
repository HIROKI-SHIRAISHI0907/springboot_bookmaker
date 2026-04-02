package dev.batch.bm_b011;

import java.io.Serializable;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class CsvDetailManageEntity extends MetaEntity implements Serializable {

	/** ID */
    private Long id;

    /** CSVID */
    private String csvId;

    /** データカテゴリ */
    private String dataCategory;

    /** シーズン */
    private String season;

    /** ホームチーム */
    private String homeTeamName;

    /** アウェーチーム */
    private String awayTeamName;

    /** 統計反映フラグ */
    private String checkFinFlg;

}
