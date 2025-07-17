package dev.application.analyze.bm_m099;

import lombok.Data;

/**
 * file_chkテーブルentity
 * @author shiraishitoshio
 *
 */
@Data
public class CsvImportHistoryEntity {

	/** ファイル名 */
	private String fileName;

	/** ハッシュ */
	private String fileHash;

	/** 登録ID */
	private String registerId;

	/** 登録時間 */
	private String registerTime;

	/** 更新ID */
	private String updateId;

	/** 更新時間 */
	private String updateTime;

}
