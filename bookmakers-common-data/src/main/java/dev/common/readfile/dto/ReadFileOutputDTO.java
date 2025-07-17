package dev.common.readfile.dto;

import java.util.List;

import dev.common.dto.AbstractResultErrDetailOutputDTO;
import dev.common.entity.BookDataEntity;
import lombok.Data;

/**
 * ブック読み取りoutputDTO
 * @author shiraishitoshio
 *
 */
@Data
public class ReadFileOutputDTO extends AbstractResultErrDetailOutputDTO {

	/**
	 * 結果コード(終了コード)
	 */
	private String resultCd;

	/**
	 * 読み取り結果リスト
	 */
	private List<BookDataEntity> readHoldDataList;

	/**
	 * 読み取り結果リスト
	 */
	private List<BookDataEntity> readDataList;

}
