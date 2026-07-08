package dev.web.api.bm_w013;

import lombok.Data;

/**
 * 統計分析用コントローラーrequestリソース
 * @author shiraishitoshio
 *
 */
@Data
public class StatRequestResource {
	/** 国 */
	private String country;
	/** リーグ */
	private String league;
	/** シーズン */
	private String season;
	/** B014用 readyFlg */
    private Boolean readyFlg;
}
