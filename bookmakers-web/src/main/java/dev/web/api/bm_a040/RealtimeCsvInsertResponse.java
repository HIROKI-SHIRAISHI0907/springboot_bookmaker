package dev.web.api.bm_a040;

import java.util.ArrayList;
import java.util.List;

import dev.web.api.bm_a031.CsvUploadRowError;
import lombok.Data;

/**
 * リアルタイムデータ CSV アップロードの結果（CsvUploadConsole.tsx の表示に合わせた形。2026-11-28 追加）
 * @author shiraishitoshio
 */
@Data
public class RealtimeCsvInsertResponse {

	public static final String MASTER_NAME = "realtimeData";
	public static final String STATUS_SUCCESS = "SUCCESS";
	public static final String STATUS_ERROR = "ERROR";

	private String masterName = MASTER_NAME;

	/** ファイルアップロード状況管理の ID */
	private Long uploadId;

	/** SUCCESS（反映した）/ ERROR（どの行も反映していない） */
	private String status;

	private String message;

	private String fileName;

	/** CSV の行数（見出し・空行を除く） */
	private int totalCount;

	/** 登録した行数 */
	private int insertCount;

	/** 更新（このアップロードでは使わない。0） */
	private int updateCount;

	/** 登録済みだったので飛ばした行数 */
	private int skipCount;

	/** 行エラー（ファイル全体のエラーは rowNo = 0） */
	private List<CsvUploadRowError> errors = new ArrayList<>();
}
