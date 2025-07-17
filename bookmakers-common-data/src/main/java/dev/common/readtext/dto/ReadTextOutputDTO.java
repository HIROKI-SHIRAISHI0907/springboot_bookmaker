package dev.common.readtext.dto;

import java.util.List;

import dev.common.dto.AbstractResultErrDetailOutputDTO;
import lombok.Getter;
import lombok.Setter;

/**
 * テキスト読み込みoutputDTO
 * @author shiraishitoshio
 *
 */
@Setter
@Getter
public class ReadTextOutputDTO extends AbstractResultErrDetailOutputDTO {

	/**
	 * 結果コード(終了コード)
	 */
	private String resultCd;

	/**
	 * 多重リスト
	 */
	private List<List<String>> readDataList;

}
