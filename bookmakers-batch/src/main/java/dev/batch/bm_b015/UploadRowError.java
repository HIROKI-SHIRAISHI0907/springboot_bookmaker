package dev.batch.bm_b015;

/**
 * 行エラー（file_upload_status_manage.error_detail の JSON 1 件）
 * <p>web の CsvUploadRowError と同じ項目（rowNo / operation / key / message）。状況管理画面がそのまま表示する。</p>
 * @author shiraishitoshio
 */
public class UploadRowError {

	/** CSV の行番号（見出しが 1 行目なのでデータは 2〜。ファイル全体のエラーは 0） */
	private final int rowNo;

	/** 処理区分（CSV の値のまま） */
	private final String operation;

	/** キー（例: 日本 / J1リーグ） */
	private final String key;

	/** エラー内容 */
	private final String message;

	public UploadRowError(int rowNo, String operation, String key, String message) {
		this.rowNo = rowNo;
		this.operation = operation;
		this.key = key;
		this.message = message;
	}

	public int getRowNo() {
		return rowNo;
	}

	public String getOperation() {
		return operation;
	}

	public String getKey() {
		return key;
	}

	public String getMessage() {
		return message;
	}
}
