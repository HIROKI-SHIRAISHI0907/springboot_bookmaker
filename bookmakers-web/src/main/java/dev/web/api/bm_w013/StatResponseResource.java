package dev.web.api.bm_w013;

import lombok.Data;

/**
 * 統計分析用コントローラーresponseリソース
 * @author shiraishitoshio
 *
 */
@Data
public class StatResponseResource {

	/**
	 * 返却コード
	 */
	private String returnCd;

	/**
	 * エイリアスName
	 */
	private String taskArn;

}
