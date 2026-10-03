package dev.web.api.bm_a099;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 明細ダウンロード（CSV）の結果
 * @author shiraishitoshio
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeErrorExportResult {

	/** ファイル名（例: analyze_error_20261003_101530.csv） */
	private String fileName;

	/** CSV 本文（UTF-8・BOM 付き） */
	private byte[] body;

	/** 条件に合う全件数 */
	private int total;

	/** 出力した件数（上限で打ち切ったときは total より小さい） */
	private int exported;
}
