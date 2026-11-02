package dev.web.api.bm_a036;

import java.util.ArrayList;
import java.util.List;

import dev.web.api.bm_a031.CsvUploadRowError;
import lombok.Data;

/**
 * 大会一括アップロードの結果（CsvUploadConsole.tsx の表示に合わせた形）
 * @author shiraishitoshio
 */
@Data
public class CupCsvUploadResponse {

	public static final String MASTER_NAME = "cup";
	public static final String STATUS_SUCCESS = "SUCCESS";
	public static final String STATUS_ERROR = "ERROR";

	private String masterName = MASTER_NAME;

	/** ファイルアップロード状況管理の ID（file_upload_status_manage.id） */
	private Long uploadId;

	/** SUCCESS（反映した）/ ERROR（どの行も反映していない） */
	private String status;

	private String message;

	private String fileName;

	/** CSV の行数（見出し・空行を除く） */
	private int totalCount;

	/** 登録した大会の数 */
	private int insertCount;

	/** 更新した大会の数（同じ URL の大会があった） */
	private int updateCount;

	/** 行エラー（ファイル全体のエラーは rowNo = 0） */
	private List<CsvUploadRowError> errors = new ArrayList<>();
}
