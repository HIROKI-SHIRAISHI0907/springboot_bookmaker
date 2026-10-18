package dev.web.api.bm_a031;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import dev.web.util.CsvUtil;
import dev.web.util.CsvUtil.CsvTable;

/**
 * エラー内容を書き足した CSV と zip を作る（error フォルダに置くもの・画面からダウンロードするもの）
 *
 * <ul>
 *   <li>名前は元の名前の末尾に _error を付ける（season_20261018.zip → season_20261018_error.zip、
 *       中は season_20261018_error.csv）。</li>
 *   <li>CSV は元の見出し・行のまま、最後に「エラー詳細」列を足す。エラーの無い行は空欄。
 *       1 行に複数のエラーがあるときは「／」でつなぐ。</li>
 *   <li>ファイル全体のエラー（列が足りない等）は、全行の「エラー詳細」に書く。行が無いときは「エラー詳細」だけの行を 1 行作る。</li>
 *   <li>CSV が読めなかったとき（zip でない・壊れている等）は「エラー詳細」だけの CSV にする。</li>
 *   <li>見出しに既に「エラー詳細」がある（前回のエラーファイルを直して上げ直した）ときは、その列を書き直す（列を増やさない）。</li>
 * </ul>
 *
 * <p>バッチサービスの DB チェックで失敗したときも、同じ決まりで作る。</p>
 *
 * @author shiraishitoshio
 */
public final class CsvErrorFileBuilder {

	/** 足す列の見出し */
	public static final String ERROR_COLUMN = "エラー詳細";

	/** エラーファイルの名前の末尾 */
	public static final String ERROR_SUFFIX = "_error";

	private static final String JOIN = "／";

	private CsvErrorFileBuilder() {}

	/**
	 * エラーの名前（拡張子なし）
	 * <p>season_20261018.zip → season_20261018_error。拡張子が無い・zip でないときは最後の拡張子を外して付ける。</p>
	 */
	public static String errorBaseName(String uploadedFileName) {
		String n = CsvUtil.baseName(uploadedFileName);
		int dot = n.lastIndexOf('.');
		String base = dot > 0 ? n.substring(0, dot) : n;
		if (base.isEmpty()) {
			base = "upload";
		}
		return base.endsWith(ERROR_SUFFIX) ? base : base + ERROR_SUFFIX;
	}

	/**
	 * エラー内容を書き足した zip を作る
	 * @param uploadedFileName アップロードされたファイル名
	 * @param table 読めた CSV の表（読めなかったときは null）
	 * @param rowErrors 行番号（データ行の 0 始まり）→ エラー内容
	 * @param fileErrors ファイル全体のエラー
	 * @return {名前}_error.zip（中は {名前}_error.csv）
	 */
	public static CsvUtil.CsvFile buildErrorZip(String uploadedFileName, CsvTable table,
			Map<Integer, List<String>> rowErrors, List<String> fileErrors) throws IOException {
		String base = errorBaseName(uploadedFileName);
		byte[] csv = buildErrorCsv(table, rowErrors, fileErrors);
		return new CsvUtil.CsvFile(base + ".zip", CsvUtil.zipSingle(base + ".csv", csv));
	}

	/** エラー内容を書き足した CSV（UTF-8 BOM 付き・CRLF） */
	public static byte[] buildErrorCsv(CsvTable table, Map<Integer, List<String>> rowErrors, List<String> fileErrors) {
		String fileMessage = String.join(JOIN, fileErrors);

		if (table == null) {
			List<String[]> rows = new ArrayList<>();
			rows.add(new String[] { fileMessage });
			return CsvUtil.writeCsv(List.of(ERROR_COLUMN), rows);
		}

		List<String> header = new ArrayList<>(table.getHeader());
		int errorIdx = header.indexOf(ERROR_COLUMN);
		if (errorIdx < 0) {
			header.add(ERROR_COLUMN);
			errorIdx = header.size() - 1;
		}
		int width = header.size();

		List<String[]> out = new ArrayList<>();
		List<String[]> rows = table.getRows();
		for (int i = 0; i < rows.size(); i++) {
			String[] r = Arrays.copyOf(rows.get(i), width);
			for (int c = 0; c < width; c++) {
				if (r[c] == null) {
					r[c] = "";
				}
			}
			List<String> messages = new ArrayList<>();
			if (!fileMessage.isEmpty()) {
				messages.add(fileMessage);
			}
			List<String> re = rowErrors.get(i);
			if (re != null) {
				messages.addAll(re);
			}
			r[errorIdx] = String.join(JOIN, messages);
			out.add(r);
		}
		if (rows.isEmpty() && !fileMessage.isEmpty()) {
			String[] r = new String[width];
			Arrays.fill(r, "");
			r[errorIdx] = fileMessage;
			out.add(r);
		}
		return CsvUtil.writeCsv(header, out);
	}
}