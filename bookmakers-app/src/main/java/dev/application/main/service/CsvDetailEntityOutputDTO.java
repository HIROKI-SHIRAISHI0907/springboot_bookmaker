package dev.application.main.service;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class CsvDetailEntityOutputDTO {

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

    /** 存在フラグ */
    private boolean existFlg;

}
