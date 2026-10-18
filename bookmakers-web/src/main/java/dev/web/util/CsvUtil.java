package dev.web.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.opencsv.CSVReader;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.exceptions.CsvException;

/**
 * CSV ユーティリティ
 *
 * <h3>アップロード（{@link #readUploadedZip}）</h3>
 * <ul>
 *   <li>受け付けるのは zip だけ。zip の名前は決められた接頭辞（future_ / season_ / team_ / realtime_）で始まること。</li>
 *   <li>zip の中の CSV は 1 つだけで、名前は zip と同じ（例: season_20261018.zip の中に season_20261018.csv）。</li>
 *   <li>Mac で圧縮したときに入る __MACOSX/ や ._ で始まるファイル、フォルダは数えない。</li>
 *   <li>文字コードは UTF-8。先頭の BOM は取り除く。空行は読み飛ばす。</li>
 *   <li>決まりに合わないときは {@link CsvFileException}。読めたところまで（CSV の中身・表）を持たせるので、
 *       エラー内容を書き足した CSV を作れる。</li>
 * </ul>
 *
 * <h3>表 → Bean（{@link #toBeans}）</h3>
 * <ul>
 *   <li>Bean の @CsvBindByName の列名で値を入れる（フィールドは String）。</li>
 *   <li>Bean の列が見出しに無ければ {@link CsvFileException}。見出しにある余分な列（「エラー詳細」など）は無視する。</li>
 * </ul>
 *
 * <h3>書き出し</h3>
 * <ul>
 *   <li>{@link #writeCsv}: UTF-8（BOM 付き・改行 CRLF）。</li>
 *   <li>{@link #zipSingle}: ファイル 1 つの zip。</li>
 *   <li>{@link #createFormatZip}: 見出し行だけの CSV を zip にしたもの（フォーマットのダウンロード）。</li>
 * </ul>
 */
public class CsvUtil {

	/** BOM */
	private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	/** コンストラクタ生成禁止 */
	private CsvUtil() {}

	// =========================================================
	// 型
	// =========================================================

	/** ダウンロード用のファイル */
	public static class CsvFile {
		private final String fileName;
		private final byte[] bytes;

		public CsvFile(String fileName, byte[] bytes) {
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
	}

	/** 読み込んだ zip（名前の決まりを満たしたもの） */
	public static class UploadedZip {
		private final String zipName;
		private final String csvName;
		private final byte[] zipBytes;
		private final CsvTable table;

		public UploadedZip(String zipName, String csvName, byte[] zipBytes, CsvTable table) {
			this.zipName = zipName;
			this.csvName = csvName;
			this.zipBytes = zipBytes;
			this.table = table;
		}

		public String getZipName() {
			return zipName;
		}

		public String getCsvName() {
			return csvName;
		}

		public byte[] getZipBytes() {
			return zipBytes;
		}

		public CsvTable getTable() {
			return table;
		}
	}

	/**
	 * ファイルの決まりに合わない（ファイル名・zip の中身・CSV が読めない・列が足りない）
	 * <p>読めたところまでの CSV の表（table）を持つ。読めなかったときは null。</p>
	 */
	public static class CsvFileException extends Exception {
		private static final long serialVersionUID = 1L;
		private final transient CsvTable table;

		public CsvFileException(String message, CsvTable table) {
			super(message);
			this.table = table;
		}

		public CsvTable getTable() {
			return table;
		}
	}

	// =========================================================
	// アップロード
	// =========================================================

	/**
	 * アップロードされた zip を読み、名前の決まりをチェックして CSV の表を返す
	 * @param zipName アップロードされたファイル名
	 * @param bytes ファイルの中身
	 * @param prefix ファイル名の接頭辞（例: season_）
	 * @throws CsvFileException 決まりに合わないとき（メッセージは画面に出す想定）
	 */
	public static UploadedZip readUploadedZip(String zipName, byte[] bytes, String prefix) throws CsvFileException {
		String name = baseName(zipName);
		if (bytes == null || bytes.length == 0) {
			throw new CsvFileException("ファイルが空です。", null);
		}

		// zip でないとき（CSV をそのまま送った等）も、CSV として読めればエラー内容を書き足せるように表を持たせる
		if (!name.toLowerCase(Locale.ROOT).endsWith(".zip") || !isZip(bytes)) {
			throw new CsvFileException("zipファイルをアップロードしてください。（" + name + "）", tryReadTable(bytes));
		}

		String expectedCsvName = name.substring(0, name.length() - ".zip".length()) + ".csv";
		List<String> names = new ArrayList<>();
		Map<String, byte[]> contents = new LinkedHashMap<>();
		try (InputStream in = new ByteArrayInputStream(bytes);
				ZipInputStream zis = new ZipInputStream(in, StandardCharsets.UTF_8)) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if (isIgnorableEntry(entry)) {
					continue;
				}
				String n = baseName(entry.getName());
				names.add(n);
				contents.put(n, zis.readAllBytes());
			}
		} catch (IOException | IllegalArgumentException e) {
			throw new CsvFileException("zipファイルとして読み込めません。（" + name + "）", null);
		}

		if (names.isEmpty()) {
			throw new CsvFileException("zip内にCSVファイルがありません。（" + name + "）", null);
		}
		CsvTable firstTable = tryReadTable(contents.values().iterator().next());
		if (names.size() > 1) {
			throw new CsvFileException("zip内のファイルは 1 つにしてください。（" + String.join(", ", names) + "）", firstTable);
		}
		if (!name.startsWith(prefix)) {
			throw new CsvFileException("zipファイル名は「" + prefix + "」で始めてください。（" + name + "）", firstTable);
		}
		if (!names.get(0).equals(expectedCsvName)) {
			throw new CsvFileException(
					"zip内のCSVファイル名はzipと同じ「" + expectedCsvName + "」にしてください。（" + names.get(0) + "）", firstTable);
		}

		CsvTable table = readTable(contents.get(expectedCsvName));
		return new UploadedZip(name, expectedCsvName, bytes, table);
	}

	/**
	 * CSV のバイト列を表にする（BOM 除去・空行は除く）
	 * @throws CsvFileException CSV として読めない・見出しが無いとき
	 */
	public static CsvTable readTable(byte[] csvBytes) throws CsvFileException {
		byte[] b = stripBom(csvBytes);
		List<String[]> all;
		try (Reader r = new InputStreamReader(new ByteArrayInputStream(b), StandardCharsets.UTF_8);
				CSVReader reader = new CSVReader(r)) {
			all = reader.readAll();
		} catch (IOException | CsvException | RuntimeException e) {
			throw new CsvFileException("CSVを読み込めませんでした。（" + e.getClass().getSimpleName() + "）", null);
		}

		List<String[]> nonEmpty = new ArrayList<>();
		for (String[] line : all) {
			if (!isEmptyLine(line)) {
				nonEmpty.add(line);
			}
		}
		if (nonEmpty.isEmpty()) {
			throw new CsvFileException("CSVに見出し行がありません。", null);
		}
		List<String> header = new ArrayList<>();
		for (String h : nonEmpty.get(0)) {
			header.add(h == null ? "" : h.replace("﻿", "").strip());
		}
		return new CsvTable(header, new ArrayList<>(nonEmpty.subList(1, nonEmpty.size())));
	}

	/** 読めれば表、読めなければ null */
	private static CsvTable tryReadTable(byte[] bytes) {
		try {
			return readTable(bytes);
		} catch (CsvFileException e) {
			return null;
		}
	}

	// =========================================================
	// 表 → Bean
	// =========================================================

	/**
	 * 表を Bean のリストにする（@CsvBindByName の列名で String フィールドに入れる。行の順のまま）
	 * @throws CsvFileException Bean の列が見出しに無いとき
	 */
	public static <T> List<T> toBeans(CsvTable table, Class<T> type) throws CsvFileException {
		List<Field> fields = new ArrayList<>();
		List<Integer> indexes = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		for (Field f : type.getDeclaredFields()) {
			CsvBindByName ann = f.getAnnotation(CsvBindByName.class);
			if (ann == null || ann.column().isEmpty()) {
				continue;
			}
			int idx = table.getHeader().indexOf(ann.column());
			if (idx < 0) {
				missing.add(ann.column());
				continue;
			}
			f.setAccessible(true);
			fields.add(f);
			indexes.add(idx);
		}
		if (!missing.isEmpty()) {
			throw new CsvFileException("CSVの見出しに列が足りません。（" + String.join(", ", missing)
					+ "）フォーマットをダウンロードし直して、見出し行は変えずに使ってください。", table);
		}

		List<T> beans = new ArrayList<>();
		try {
			for (String[] row : table.getRows()) {
				T bean = type.getDeclaredConstructor().newInstance();
				for (int i = 0; i < fields.size(); i++) {
					int idx = indexes.get(i);
					fields.get(i).set(bean, idx < row.length ? row[idx] : null);
				}
				beans.add(bean);
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("CSV の Bean を作れません: " + type.getName(), e);
		}
		return beans;
	}

	// =========================================================
	// 書き出し
	// =========================================================

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

	/**
	 * ヘッダー行だけの CSV を zip にして返す（フォーマットのダウンロード）
	 * @param baseName zip・CSV 共通の名前（拡張子なし。例: season_20261018203000）
	 * @param type Bean クラス（@CsvBindByName の列名を宣言順に並べる）
	 */
	public static CsvFile createFormatZip(String baseName, Class<?> type) throws IOException {
		return new CsvFile(baseName + ".zip", zipSingle(baseName + ".csv", writeCsv(headerOf(type), new ArrayList<>())));
	}

	/** Bean の列名（宣言順） */
	public static List<String> headerOf(Class<?> type) {
		List<String> headers = new ArrayList<>();
		// getDeclaredFields は宣言順で返る（HotSpot）。列の並びは Bean の宣言順にそろえておく
		for (Field f : type.getDeclaredFields()) {
			CsvBindByName ann = f.getAnnotation(CsvBindByName.class);
			if (ann != null && !ann.column().isEmpty()) {
				headers.add(ann.column());
			}
		}
		return headers;
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

	/** CSV の値のエスケープ（, " 改行を含むときだけ "" で囲む） */
	private static String escape(String v) {
		if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
			return "\"" + v.replace("\"", "\"\"") + "\"";
		}
		return v;
	}

	private static boolean isEmptyLine(String[] line) {
		return line == null || Arrays.stream(line).allMatch(v -> v == null || v.isBlank());
	}

	private static byte[] stripBom(byte[] b) {
		if (b.length >= 3 && b[0] == UTF8_BOM[0] && b[1] == UTF8_BOM[1] && b[2] == UTF8_BOM[2]) {
			return Arrays.copyOfRange(b, 3, b.length);
		}
		return b;
	}

	/** zip 判定（ローカルファイルヘッダーの "PK"） */
	private static boolean isZip(byte[] bytes) {
		return bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K';
	}

	/** 数えないエントリ（フォルダ・Mac の付属ファイル） */
	private static boolean isIgnorableEntry(ZipEntry entry) {
		if (entry.isDirectory()) {
			return true;
		}
		String name = entry.getName();
		return name.startsWith("__MACOSX/") || baseName(name).startsWith("._") || baseName(name).equals(".DS_Store");
	}

	/** パスを除いたファイル名（Windows の \ にも対応） */
	public static String baseName(String path) {
		if (path == null) {
			return "";
		}
		String p = path.replace('\\', '/');
		int i = p.lastIndexOf('/');
		return i >= 0 ? p.substring(i + 1) : p;
	}
}