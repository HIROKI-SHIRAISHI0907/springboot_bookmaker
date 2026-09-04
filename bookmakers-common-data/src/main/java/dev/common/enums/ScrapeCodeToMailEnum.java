package dev.common.enums;

/**
 * スクレイプコードとバッチ名の紐付け定数
 *
 * mail_send_manage.Bikouに埋め込まれる「SCRAPE_NAME=S012」のようなバッチコードから、
 * メール件名・本文に表示する日本語のバッチ名を解決するために使用する。
 *
 * ※S012の日本語名は仮です。実際のバッチ名に置き換え、他のバッチコードも
 *   同じ形式で追加してください。
 *
 * @author shiraishitoshio
 */
public enum ScrapeCodeToMailEnum {

	/** S002: 選手情報取得スクレイピング */
	S002("S002", "選手情報取得スクレイピング"),

	/** S003: シーズン情報取得スクレイピング */
	S003("S003", "シーズン情報取得スクレイピング"),

	/** S004: チーム情報取得スクレイピング */
	S004("S004", "チーム情報取得スクレイピング"),

	/** S005: 試合予定情報取得スクレイピング */
	S005("S005", "試合予定情報取得スクレイピング"),

	/** S008: リアルタイムデータ取得スクレイピング */
	S008("S008", "リアルタイムデータ取得スクレイピング"),

	/** S009: 翌日ECS非稼働情報取得スクレイピング */
	S009("S009", "翌日ECS非稼働情報取得スクレイピング"),

	/** S010: 終了済取得スクレイピング */
	S010("S010", "終了済取得スクレイピング"),

	/** S014:(現在未決定)  */

	/** S015: スタジアム情報取得スクレイピング */
	S015("S015", "スタジアム情報取得スクレイピング")

	;

	/** スクレイプコード */
	private final String ScrapeCode;

	/** スクレイプ名（日本語） */
	private final String ScrapeName;

	ScrapeCodeToMailEnum(String ScrapeCode, String ScrapeName) {
		this.ScrapeCode = ScrapeCode;
		this.ScrapeName = ScrapeName;
	}

	/**
	 * スクレイプコードを取得
	 */
	public String getScrapeCode() {
		return ScrapeCode;
	}

	/**
	 * スクレイプ名を取得
	 */
	public String getScrapeName() {
		return ScrapeName;
	}

	/**
	 * バッチコードからバッチ名を解決する。
	 * 該当するバッチコードが無い場合は、コードそのものを返す
	 * （紐付けが漏れていてもメール送信自体は止めないようにするため）。
	 *
	 * @param batchCode バッチコード（例: "S012"）
	 * @return バッチ名（該当なしの場合はbatchCodeをそのまま返す）
	 */
	public static String resolveScrapeName(String scrapeCode) {
		if (scrapeCode == null) {
			return null;
		}
		for (ScrapeCodeToMailEnum value : values()) {
			if (value.ScrapeCode.equals(scrapeCode)) {
				return value.ScrapeName;
			}
		}
		return scrapeCode;
	}
}