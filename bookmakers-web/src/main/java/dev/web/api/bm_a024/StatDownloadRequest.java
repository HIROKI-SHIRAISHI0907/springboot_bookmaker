package dev.web.api.bm_a024;

import lombok.Data;

@Data
public class StatDownloadRequest {

	/**
	 * 抽出対象の国リーグ名
	 * 例:
	 *  - サンプル国_サンプルリーグ
	 *  - サンプル国: サンプルリーグ
	 */
	private String countryLeagueName;

	/**
	 * S3上のフォルダ
	 * 省略時は EachCsvTransaction
	 */
	private String folder;

}
