package dev.web.api.bm_a015;

import lombok.Data;

@Data
public class PointSettingEntity {

	/** 通番 */
	private String id;

	/** 国 */
    private String country;

    /** リーグ */
    private String league;

    /** 勝ち */
    private Integer win;

    /** 負け */
    private Integer lose;

    /** 引き分け */
    private Integer draw;

    /** 備考 */
    private String remarks;

    /** 削除フラグ */
    private String delFlg;

}
