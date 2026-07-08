package dev.web.api.bm_a024;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * statDownload レスポンス
 */
@Data
@NoArgsConstructor
public class StatDownloadResponse {

	/**
	 * ZIPファイル本体
	 * ※ Jackson で JSON 返却時は Base64 文字列としてシリアライズされる
	 */
	private byte[] zipFile;

	/**
	 * ダウンロード用ファイル名
	 */
	private String fileName;

	/**
	 * メッセージ
	 */
	private String message;

}
