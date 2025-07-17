package dev.common.readtext.dto;

import java.util.List;

import lombok.Data;

/**
 * テキスト読み込みinputDTO
 * @author shiraishitoshio
 *
 */
@Data
public class ReadTextInputDTO {

	/**
	 * ブック存在パス
	 */
	private String dataPath;

	/**
	 * 置換対象リスト
	 */
	private List<String> convertTagList;

	/**
	 * 分割対象文字
	 */
	private String splitTag;

	/**
	 * ヘッダーありフラグ
	 */
	private boolean headerFlg;

}
