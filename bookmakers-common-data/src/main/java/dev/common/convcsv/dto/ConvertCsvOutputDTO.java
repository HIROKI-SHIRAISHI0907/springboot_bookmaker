package dev.common.convcsv.dto;

import dev.common.dto.AbstractResultErrDetailOutputDTO;
import lombok.Getter;
import lombok.Setter;

/**
 * CSV変換outputDTO
 * @author shiraishitoshio
 *
 */
@Setter
@Getter
public class ConvertCsvOutputDTO extends AbstractResultErrDetailOutputDTO {

	/**
	 * 結果コード(終了コード)
	 */
	private String resultCd;

}
