package dev.common.delete.dto;

import lombok.Data;

/**
 * ブック削除inputDTO
 * @author shiraishitoshio
 *
 */
@Data
public class DeleteBookInputDTO {

	/**
	 * ブック存在パス
	 */
	private String dataPath;

	/**
	 * csv存在パス
	 */
	private String copyPath;

	/**
	 * 原本パス
	 */
	private String originalPath;

}
