package dev.web.api.bm_w020;

import lombok.Data;

/**
 * export_csvAPIレスポンス
 *
 * @author shiraishitoshio
 */
@Data
public class ExportCsvResponse {

	/** レスポンスコード */
	private String responseCode; // "0"=成功, "9"=失敗 など運用に合わせて

	/** メッセージ */
    private String message;

}
