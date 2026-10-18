package dev.web.api.bm_a031;

/**
 * CSV の「処理区分」
 *
 * <p>CSV には番号（1〜5）でも名前（登録・更新・削除・非表示・再表示）でも書ける。</p>
 *
 * <table border="1">
 *   <tr><th>番号</th><th>名前</th><th>内容</th></tr>
 *   <tr><td>1</td><td>登録</td><td>キーが重複しなければ INSERT（del_flg='0'）</td></tr>
 *   <tr><td>2</td><td>更新</td><td>対象の行を UPDATE（空欄の項目は変更しない）</td></tr>
 *   <tr><td>3</td><td>削除</td><td>対象の行を DELETE（物理削除）</td></tr>
 *   <tr><td>4</td><td>非表示</td><td>del_flg='1'</td></tr>
 *   <tr><td>5</td><td>再表示</td><td>del_flg='0'</td></tr>
 * </table>
 *
 * @author shiraishitoshio
 */
public enum CsvOperation {

	INSERT("1", "登録"),
	UPDATE("2", "更新"),
	DELETE("3", "削除"),
	HIDE("4", "非表示"),
	SHOW("5", "再表示");

	private final String code;
	private final String label;

	CsvOperation(String code, String label) {
		this.code = code;
		this.label = label;
	}

	public String getCode() {
		return code;
	}

	public String getLabel() {
		return label;
	}

	/**
	 * CSV の値から処理区分を求める（番号・名前のどちらでもよい。前後の空白は無視）
	 * @param value CSV の値
	 * @return 処理区分。当てはまらなければ null
	 */
	public static CsvOperation of(String value) {
		if (value == null) {
			return null;
		}
		String v = value.strip();
		for (CsvOperation op : values()) {
			if (op.code.equals(v) || op.label.equals(v)) {
				return op;
			}
		}
		return null;
	}
}
