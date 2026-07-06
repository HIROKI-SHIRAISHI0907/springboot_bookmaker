package dev.common.upload;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RecordFileOperationOutputDTO {

	/** 返却コード */
	private int infoCd;

	/** メッセージ */
	private String message;

}
