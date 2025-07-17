package dev.common.find.dto;

import lombok.Data;

/**
 * ブック探索inputDTO
 * @author shiraishitoshio
 *
 */
@Data
public class FindBookInputDTO {

	/**
	 * ブック存在パス(フルパスからファイル名を除いた部分)
	 */
	private String dataPath;

	/**
	 * コピー先パス(フルパスからファイル名を除いた部分)
	 */
	private String copyPath;

	/**
	 * コピーフラグ
	 */
	private boolean copyFlg;

	/**
	 * 情報パス取得フラグ
	 */
	private boolean getBookFlg;

	/**
	 * 取得対象物
	 */
	private String targetFile;

	/**
	 * 先頭情報
	 */
	private String prefixFile;

	/**
	 * 拡張子情報(.xlsx, .csvなど)
	 */
	private String suffixFile;

	/**
	 * ファイル情報含有リスト
	 */
	private String[] containsList;

	/**
	 * CSV番号
	 */
	private String csvNumber;

}
