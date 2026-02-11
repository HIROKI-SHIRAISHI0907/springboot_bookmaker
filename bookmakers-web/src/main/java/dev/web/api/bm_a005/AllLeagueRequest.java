package dev.web.api.bm_a005;

import lombok.Data;

/**
 * AllLeagueRequestAPIリクエスト
 * @author shiraishitoshio
 *
 */
@Data
public class AllLeagueRequest {

	/** 国 */
    private String country;

    /** リーグ */
    private String league;

    /** 論理フラグ */
    private String logicFlg;

    /** 表示フラグ */
    private String dispFlg;

}
