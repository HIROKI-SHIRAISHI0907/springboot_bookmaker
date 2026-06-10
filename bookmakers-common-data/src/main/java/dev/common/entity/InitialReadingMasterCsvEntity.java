package dev.common.entity;

import lombok.Data;

@Data
public class InitialReadingMasterCsvEntity {

	/** 通番 */
	private String id;

	/** マスタ名 */
    private String masterName;

    /** 国 */
    private String country;

    /** リーグ */
    private String league;

    /** 初回フラグ（0: 初回、1:初回以外） */
    private String initialFlg;

}
