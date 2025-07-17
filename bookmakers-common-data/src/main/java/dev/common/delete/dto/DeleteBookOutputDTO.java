package dev.common.delete.dto;

import dev.common.dto.AbstractResultErrDetailOutputDTO;
import lombok.Getter;
import lombok.Setter;

/**
 * ブック削除outputDTO
 * @author shiraishitoshio
 *
 */
@Setter
@Getter
public class DeleteBookOutputDTO extends AbstractResultErrDetailOutputDTO {

	/**
	 * 結果コード(終了コード)
	 */
	private String resultCd;

}
