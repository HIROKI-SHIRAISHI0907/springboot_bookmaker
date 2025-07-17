package dev.common.convertcsvandread.dto;

import java.util.List;

import dev.common.entity.BookDataEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * CSV変換&読み取りoutputDTO
 * @author shiraishitoshio
 *
 */
@Setter
@Getter
public class ConvertCsvAndReadFileOutputDTO {

	/**
	 * CSV変換結果フラグ
	 */
	private boolean convertCsvFlg;

	/**
	 * CSV変換後パス
	 */
	private String afterCsvPath;

	/**
	 * CSV読み取り結果フラグ
	 */
	private boolean readCsvFlg;

	/**
	 * 読み取り結果リスト
	 */
	private List<BookDataEntity> readDataList;

}
