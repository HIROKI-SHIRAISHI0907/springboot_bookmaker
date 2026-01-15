package dev.web.api.bm_u001;

import lombok.Data;

/**
 * サービス共通用
 * @author shiraishitoshio
 *
 */
@Data
public class SubInput {

	/** 選択肢No. */
	private String optionNum;

	/** 選択肢 */
	private String options;

	/** フラグ(0:有効,1:無効) */
	private String flg;

}
