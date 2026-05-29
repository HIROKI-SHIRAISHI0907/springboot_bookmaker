package dev.web.api.bm_w016;

import lombok.Data;

/**
 * 統計分析用コントローラーrequestリソース
 * @author shiraishitoshio
 *
 */
@Data
public class StatExecuteRequest {
	/** 国 */
	private String country;
	/** リーグ */
	private String league;
	/** シーズン*/
	private String season;
}
