package dev.mng.dto;

import lombok.Data;

/**
 * CSV情報OUTPUTDTO
 * @author shiraishitoshio
 *
 */
@Data
public class CsvOutputDTO {

	/** CSV存在フラグ */
	private boolean existFlg;

	/** CSV番号 */
	private Integer csvNumber;

}
