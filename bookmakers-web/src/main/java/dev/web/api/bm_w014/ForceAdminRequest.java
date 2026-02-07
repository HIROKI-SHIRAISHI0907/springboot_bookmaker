package dev.web.api.bm_w014;

import lombok.Data;

/**
 * FavoriteRequestAPIリクエスト
 * @author shiraishitoshio
 *
 */
@Data
public class ForceAdminRequest {

	/** 国 */
    private String country;

    /** リーグ */
    private String league;

    /** チーム */
    private String team;

    /** 削除フラグ */
    private String delFlg;

}
