package dev.common.enums;

/**
 * バッチコードとバッチ名の紐付け定数
 *
 * mail_send_manage.bikouに埋め込まれる「BATCH_NAME=B012」のようなバッチコードから、
 * メール件名・本文に表示する日本語のバッチ名を解決するために使用する。
 *
 * ※B012の日本語名は仮です。実際のバッチ名に置き換え、他のバッチコードも
 *   同じ形式で追加してください。
 *
 * @author shiraishitoshio
 */
public enum BatchCodeToMailEnum {

	/** B002: 選手情報登録バッチ */
	B002("B002", "選手情報登録バッチ"),

	/** B003: シーズン情報登録バッチ */
	B003("B003", "シーズン情報登録バッチ"),

	/** B004: チーム情報登録バッチ */
	B004("B004", "チーム情報登録バッチ"),

	/** B005: 試合予定情報登録バッチ */
	B005("B005", "試合予定情報登録バッチ"),

	/** B007: 全リーグ情報登録バッチ */
	B007("B007", "全リーグ情報登録バッチ"),

	/** B010: 終了済情報登録バッチ */
	B010("B010", "終了済情報登録バッチ"),

	/** B011: CSV作成バッチ */
	B011("B011", "CSV作成バッチ"),

	/** B012: 終了済情報自動アップロードバッチ */
	B012("B012", "終了済情報自動アップロードバッチ"),

	/** B013: シーズン終了情報削除バッチ */
	B013("B013", "シーズン終了情報削除バッチ"),

	/** B014: スタジアム情報登録バッチ */
	B014("B014", "スタジアム情報登録バッチ"),

	/** B015: 不要データ削除バッチ */
	B015("B015", "不要データ削除バッチ"),

	/** B095: その他メール送信バッチ */
	B095("B095", "その他メール送信バッチ"),

	/** B096: メール送信バッチ */
	B096("B096", "メール送信バッチ"),

	/** B097: データカテゴリ自動付与バッチ */
	B097("B097", "データカテゴリ自動付与バッチ"),

	/** B098: DBコネクションクリアバッチ */
	B098("B098", "DBコネクションクリアバッチ")
	;

	/** バッチコード */
	private final String batchCode;

	/** バッチ名（日本語） */
	private final String batchName;

	BatchCodeToMailEnum(String batchCode, String batchName) {
		this.batchCode = batchCode;
		this.batchName = batchName;
	}

	/**
	 * バッチコードを取得
	 */
	public String getBatchCode() {
		return batchCode;
	}

	/**
	 * バッチ名を取得
	 */
	public String getBatchName() {
		return batchName;
	}

	/**
	 * バッチコードからバッチ名を解決する。
	 * 該当するバッチコードが無い場合は、コードそのものを返す
	 * （紐付けが漏れていてもメール送信自体は止めないようにするため）。
	 *
	 * @param batchCode バッチコード（例: "B012"）
	 * @return バッチ名（該当なしの場合はbatchCodeをそのまま返す）
	 */
	public static String resolveBatchName(String batchCode) {
		if (batchCode == null) {
			return null;
		}
		for (BatchCodeToMailEnum value : values()) {
			if (value.batchCode.equals(batchCode)) {
				return value.batchName;
			}
		}
		return batchCode;
	}
}