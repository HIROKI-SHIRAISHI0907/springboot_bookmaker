package dev.batch.bm_b015;

/**
 * CSV の「処理区分」（web の CsvOperation と同じ）
 * <p>番号（1〜5）でも名前（登録・更新・削除・非表示・再表示）でも書ける。前後の空白は無視する。</p>
 * @author shiraishitoshio
 */
public enum UploadOperation {

	/** キーが重複しなければ登録（del_flg = '0'） */
	INSERT("1", "登録"),
	/** 対象の行の、値が入っている列だけ更新 */
	UPDATE("2", "更新"),
	/** 対象の行を物理削除 */
	DELETE("3", "削除"),
	/** del_flg = '1' */
	HIDE("4", "非表示"),
	/** del_flg = '0' */
	SHOW("5", "再表示");

	private final String code;
	private final String label;

	UploadOperation(String code, String label) {
		this.code = code;
		this.label = label;
	}

	public String getCode() {
		return code;
	}

	public String getLabel() {
		return label;
	}

	/** CSV の値から求める（当てはまらなければ null） */
	public static UploadOperation of(String value) {
		if (value == null) {
			return null;
		}
		String v = value.strip();
		for (UploadOperation op : values()) {
			if (op.code.equals(v) || op.label.equals(v)) {
				return op;
			}
		}
		return null;
	}
}
