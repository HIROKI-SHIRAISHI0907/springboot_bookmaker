package dev.common.entity;

import java.io.Serializable;

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

    /** 統計反映フラグ（0: 未反映, 1: 反映済み, 2:手動データのためダミー用） */
    private String checkFinFlg;

}
