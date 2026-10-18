package dev.web.api.bm_a031;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CSV の行エラー
 *
 * @author shiraishitoshio
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CsvUploadRowError {

	/** 行番号（ヘッダーを 1 行目として数える） */
	private int rowNo;

	/** 処理区分（CSV に書かれていた値） */
	private String operation;

	/** キー（国・リーグなど、どの行か分かる値） */
	private String key;

	/** エラー内容 */
	private String message;
}
