package dev.batch.bm_b015;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * アップロードされた zip（中に CSV 1 つ）の読み込みと、エラー内容を書き足した zip の作成
 *
 * <ul>
 *   <li>ファイル名・中身の決まり（接頭辞・zip と同じ名前など）は web で確かめ済み。ここでは中の CSV を読むだけ。</li>
 *   <li>Mac の付属ファイル（__MACOSX/・._〇〇・.DS_Store）とフォルダは数えない。</li>
 *   <li>文字コードは UTF-8。先頭の BOM は除く。空行は読み飛ばす。値の前後の空白はそのまま（使う側で除く）。</li>
 *   <li>エラーファイルは web（CsvErrorFileBuilder）と同じ決まり:
 *       名前は 〇〇_error.zip（中は 〇〇_error.csv）、元の見出し・行のまま最後に「エラー詳細」列を足す
 *       （既にあれば書き直す）。UTF-8 BOM 付き・CRLF。</li>
 * </ul>
 * <p>opencsv はバッチの依存に無いため、CSV は自前で読む（"" で囲んだ値・値の中の , " 改行に対応）。</p>
 * @author shiraishitoshio
 */
public final class UploadCsvUtil {

	private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	private UploadCsvUtil() {
	}

	/** CSV の表（見出し + データ行。空行は除いてある） */
	public static class CsvTable {
		private final List<String> header;
		private final List<String[]> rows;

		public CsvTable(List<String> header, List<String[]> rows) {
			this.header = header;
			this.rows = rows;
		}

		public List<String> getHeader() {
			return header;
		}

		public List<String[]> getRows() {
			return rows;
		}

		/** 列名の位置（無ければ -1） */
		public int indexOf(String column) {
			return header.indexOf(column);
		}

		/** i 行目（0 始まり）の列の値（列が無い・値が無いときは空文字） */
		public String value(int i, String column) {
			int idx = indexOf(column);
			if (idx < 0) {
				return "";
			}
			String[] r = rows.get(i);
			return idx < r.length && r[idx] != null ? r[idx] : "";
		}
	}

	/** 読めないとき（メッセージはエラーファイル・状況管理に出す） */
	public static class CsvReadException extends Exception {
		private static final long serialVersionUID = 1L;

		public CsvReadException(String message) {
			super(message);
		}
	}

	/** 作ったファイル */
	public static class OutFile {
		private final String fileName;
		private final byte[] bytes;

		public OutFile(String fileName, byte[] bytes) {
			this.fileName = fileName;
			this.bytes = bytes;
		}

		public String getFileName() {
			return fileName;
		}

		public byte[] getBytes() {
			return bytes;
		}
	}

	// =========================================================
	// 読み込み
	// =========================================================

	/**
	 * zip の中の CSV を表にする
	 * @throws CsvReadException zip として読めない・CSV が無い・見出しが無い
	 */
	public static CsvTable readZip(byte[] zipBytes) throws CsvReadException {
		if (zipBytes == null || zipBytes.length == 0) {
			throw new CsvReadException("ファイルが空です。");
		}
		byte[] csv = null;
		try (InputStream in = new ByteArrayInputStream(zipBytes);
				ZipInputStream zis = new ZipInputStream(in, StandardCharsets.UTF_8)) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if (isIgnorableEntry(entry)) {
					continue;
				}
				csv = zis.readAllBytes();
				break;
			}
		} catch (IOException | IllegalArgumentException e) {
			throw new CsvReadException("zipファイルとして読み込めません。（" + e.getClass().getSimpleName() + "）");
		}
		if (csv == null) {
			throw new CsvReadException("zip内にCSVファイルがありません。");
		}
		return readCsv(csv);
	}

	/** CSV のバイト列を表にする */
	public static CsvTable readCsv(byte[] csvBytes) throws CsvReadException {
		String text = new String(stripBom(csvBytes), StandardCharsets.UTF_8);
		List<String[]> all = parse(text);
		List<String[]> nonEmpty = new ArrayList<>();
		for (String[] line : all) {
			if (!isEmptyLine(line)) {
				nonEmpty.add(line);
			}
		}
		if (nonEmpty.isEmpty()) {
			throw new CsvReadException("CSVに見出し行がありません。");
		}
		List<String> header = new ArrayList<>();
		for (String h : nonEmpty.get(0)) {
			header.add(h == null ? "" : h.replace("﻿", "").strip());
		}
		return new CsvTable(header, new ArrayList<>(nonEmpty.subList(1, nonEmpty.size())));
	}

	/** CSV の文字列を行・値に分ける（RFC 4180 相当。"" で囲んだ値の中の , " 改行に対応） */
	static List<String[]> parse(String text) throws CsvReadException {
		List<String[]> lines = new ArrayList<>();
		List<String> cur = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		boolean quoted = false;
		boolean fieldStarted = false;
		int n = text.length();
		for (int i = 0; i < n; i++) {
			char c = text.charAt(i);
			if (quoted) {
				if (c == '"') {
					if (i + 1 < n && text.charAt(i + 1) == '"') {
						sb.append('"');
						i++;
					} else {
						quoted = false;
					}
				} else {
					sb.append(c);
				}
				continue;
			}
			if (c == '"' && !fieldStarted) {
				quoted = true;
				fieldStarted = true;
			} else if (c == ',') {
				cur.add(sb.toString());
				sb.setLength(0);
				fieldStarted = false;
			} else if (c == '\r' || c == '\n') {
				cur.add(sb.toString());
				sb.setLength(0);
				fieldStarted = false;
				lines.add(cur.toArray(new String[0]));
				cur = new ArrayList<>();
				if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') {
					i++;
				}
			} else {
				sb.append(c);
				fieldStarted = true;
			}
		}
		if (quoted) {
			throw new CsvReadException("CSVを読み込めませんでした。（\" が閉じられていません）");
		}
		if (sb.length() > 0 || !cur.isEmpty()) {
			cur.add(sb.toString());
			lines.add(cur.toArray(new String[0]));
		}
		return lines;
	}

	// =========================================================
	// エラーファイル
	// =========================================================

	/**
	 * エラーの名前（拡張子なし）。web の CsvErrorFileBuilder.errorBaseName と同じ。
	 * <p>season_20261018.zip → season_20261018_error（既に _error で終わるならそのまま）</p>
	 */
	public static String errorBaseName(String uploadedFileName) {
		String n = baseName(uploadedFileName);
		int dot = n.lastIndexOf('.');
		String base = dot > 0 ? n.substring(0, dot) : n;
		if (base.isEmpty()) {
			base = "upload";
		}
		return base.endsWith(FileUploadBatchConst.ERROR_SUFFIX) ? base : base + FileUploadBatchConst.ERROR_SUFFIX;
	}

	/**
	 * エラー内容を書き足した zip を作る
	 * @param uploadedFileName アップロードされた zip の名前
	 * @param table 読めた表（読めなかったときは null）
	 * @param rowErrors データ行の番号（0 始まり）→ エラー内容
	 * @param fileErrors ファイル全体のエラー（全行の「エラー詳細」に書く）
	 */
	public static OutFile buildErrorZip(String uploadedFileName, CsvTable table,
			Map<Integer, List<String>> rowErrors, List<String> fileErrors) throws IOException {
		String base = errorBaseName(uploadedFileName);
		byte[] csv = buildErrorCsv(table, rowErrors, fileErrors);
		return new OutFile(base + ".zip", zipSingle(base + ".csv", csv));
	}

	/** エラー内容を書き足した CSV */
	public static byte[] buildErrorCsv(CsvTable table, Map<Integer, List<String>> rowErrors, List<String> fileErrors) {
		String fileMessage = String.join(FileUploadBatchConst.ERROR_DELIMITER, fileErrors);
		if (table == null) {
			List<String[]> rows = new ArrayList<>();
			rows.add(new String[] { fileMessage });
			return writeCsv(Arrays.asList(FileUploadBatchConst.ERROR_COLUMN), rows);
		}
		List<String> header = new ArrayList<>(table.getHeader());
		int errorIdx = header.indexOf(FileUploadBatchConst.ERROR_COLUMN);
		if (errorIdx < 0) {
			header.add(FileUploadBatchConst.ERROR_COLUMN);
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
			r[errorIdx] = String.join(FileUploadBatchConst.ERROR_DELIMITER, messages);
			out.add(r);
		}
		if (rows.isEmpty() && !fileMessage.isEmpty()) {
			String[] r = new String[width];
			Arrays.fill(r, "");
			r[errorIdx] = fileMessage;
			out.add(r);
		}
		return writeCsv(header, out);
	}

	/** CSV（UTF-8 BOM 付き・CRLF） */
	public static byte[] writeCsv(List<String> header, List<String[]> rows) {
		StringBuilder sb = new StringBuilder();
		appendLine(sb, header.toArray(new String[0]));
		for (String[] r : rows) {
			appendLine(sb, r);
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos.write(UTF8_BOM, 0, UTF8_BOM.length);
		byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
		baos.write(body, 0, body.length);
		return baos.toByteArray();
	}

	/** ファイル 1 つの zip */
	public static byte[] zipSingle(String entryName, byte[] content) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
			zos.putNextEntry(new ZipEntry(entryName));
			zos.write(content);
			zos.closeEntry();
		}
		return baos.toByteArray();
	}

	/** パスを除いたファイル名 */
	public static String baseName(String path) {
		if (path == null) {
			return "";
		}
		String p = path.replace('\\', '/');
		int i = p.lastIndexOf('/');
		return i >= 0 ? p.substring(i + 1) : p;
	}

	// =========================================================
	// 部品
	// =========================================================

	private static void appendLine(StringBuilder sb, String[] values) {
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(escape(values[i] == null ? "" : values[i]));
		}
		sb.append("\r\n");
	}

	private static String escape(String v) {
		if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
			return "\"" + v.replace("\"", "\"\"") + "\"";
		}
		return v;
	}

	private static boolean isEmptyLine(String[] line) {
		if (line == null) {
			return true;
		}
		for (String v : line) {
			if (v != null && !v.isBlank()) {
				return false;
			}
		}
		return true;
	}

	private static byte[] stripBom(byte[] b) {
		if (b.length >= 3 && b[0] == UTF8_BOM[0] && b[1] == UTF8_BOM[1] && b[2] == UTF8_BOM[2]) {
			return Arrays.copyOfRange(b, 3, b.length);
		}
		return b;
	}

	private static boolean isIgnorableEntry(ZipEntry entry) {
		if (entry.isDirectory()) {
			return true;
		}
		String name = entry.getName();
		String base = baseName(name);
		return name.startsWith("__MACOSX/") || base.startsWith("._") || base.equals(".DS_Store");
	}
}
