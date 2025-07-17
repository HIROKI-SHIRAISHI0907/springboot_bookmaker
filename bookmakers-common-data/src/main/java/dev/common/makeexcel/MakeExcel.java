package dev.common.makeexcel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import dev.common.constant.BookMakersCommonConst;


public class MakeExcel {

	/**
	 * エクセルのフォント
	 */
	private static final String EXCEL_FONT = "Calibri";

	/**
	 * コンストラクタ作成禁止
	 */
	private MakeExcel() {

	}

	/**
	 * 新しいエクセルファイルを作成
	 * @param filePath 作成するエクセルファイルのパス
	 * @throws IOException ファイル作成時にエラーが発生した場合
	 */
	@SuppressWarnings("unused")
	public static void createExcelFile(String filePath) throws IOException {
		// 新しいエクセルファイルを作成
		Workbook workbook = new XSSFWorkbook(); // .xlsx形式のワークブック
		Sheet sheet = workbook.createSheet("Sheet");

		// エクセルファイルを指定したパスに保存
		try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
			workbook.write(fileOut);
		}

		// ワークブックを閉じる
		workbook.close();
	}

	/**
	 * ヘッダーを追加する
	 * @param filePath 存在するエクセルファイルのパス
	 * @param country 国
	 * @param league リーグ
	 * @param time 時間
	 * @param headers ヘッダーリスト
	 * @throws Exception
	 */
	public static void addHeader(String filePath, String country, String league, String time,
			String[][] headers) throws Exception {
		// エクセルファイルを読み込む
		FileInputStream file = new FileInputStream(new File(filePath));
		Workbook workbook = new XSSFWorkbook(file); // .xlsxフォーマットのワークブックを作成

		// シートを取得、なければ新規に作成
		Sheet sheet = workbook.getSheetAt(0);

		// 最初の行にヘッダーを追加
		Row headerRow = sheet.createRow(0); // 1行目にヘッダーを作成

		CellStyle style = setFont(workbook);

		// ヘッダーデータを1次元配列に連結
		int feature_size = headers.length;
		String[] connectHeaders = new String[feature_size * 2];
		int all_int = 0;
		for (int feature_int = 0; feature_int < feature_size; feature_int++) {
			all_int = feature_int * 2;
			connectHeaders[all_int] = headers[feature_int][0];
			all_int = feature_int * 2 + 1;
			connectHeaders[all_int] = headers[feature_int][1];
		}

		// ヘッダーをセルに追加
		Cell cell1 = headerRow.createCell(0); // ヘッダーの列にセルを作成
		Cell cell2 = headerRow.createCell(1); // ヘッダーの列にセルを作成
		Cell cell3 = headerRow.createCell(2); // ヘッダーの列にセルを作成
		cell1.setCellValue(country);
		cell1.setCellStyle(style); // セルスタイルを設定（フォントを適用）
		cell2.setCellValue(league);
		cell2.setCellStyle(style); // セルスタイルを設定（フォントを適用）
		cell3.setCellValue(time);
		cell3.setCellStyle(style); // セルスタイルを設定（フォントを適用）
		for (int i = 3; i < connectHeaders.length + 3; i++) {
			Cell cell = headerRow.createCell(i); // ヘッダーの列にセルを作成
			int dataind = i - 3;
			cell.setCellValue(connectHeaders[dataind]); // ヘッダーの値を設定
			cell.setCellStyle(style); // セルスタイルを設定（フォントを適用）
		}

		// ファイルに変更を保存
		FileOutputStream outFile = new FileOutputStream(filePath);
		workbook.write(outFile);

		// ワークブックとファイルを閉じる
		outFile.close();
		workbook.close();
		file.close();
	}

	/**
	 * エクセルファイルの最後の行にデータを追加するメソッド
	 *
	 * @param filePath エクセルファイルのパス
	 * @param country 国
	 * @param league リーグ
	 * @param time 時間
	 * @param newRecord 追加するデータ（配列）
	 * @throws IOException ファイル作成時にエラーが発生した場合
	 */
	public static void addRecord(String filePath, String country, String league, String time,
			String[][] newRecord) throws IOException {
		// エクセルファイルを読み込む
		FileInputStream file = new FileInputStream(new File(filePath));
		Workbook workbook = new XSSFWorkbook(file); // .xlsx形式のワークブックを作成

		// シートを取得、なければ新規に作成
		Sheet sheet = workbook.getSheetAt(0);
		if (sheet == null) {

		}

		CellStyle style = setFont(workbook);

		// 最後の行番号を取得
		int lastRowNum = sheet.getLastRowNum();

		// 新しい行を作成（最後の行の次）
		Row newRow = sheet.createRow(lastRowNum + 1);

		// 試合時間の分単位修正
		double convert_times = convertTime(time);

		// ヘッダーデータを1次元配列に連結
		int feature_size = newRecord.length;
		String[] connectRecords = new String[feature_size * 2];
		int all_int = 0;
		for (int feature_int = 0; feature_int < feature_size; feature_int++) {
			all_int = feature_int * 2;
			connectRecords[all_int] = newRecord[feature_int][0];
			all_int = feature_int * 2 + 1;
			connectRecords[all_int] = newRecord[feature_int][1];
		}

		// 新しい行にデータを挿入
		Cell cell1 = newRow.createCell(0); // ヘッダーの列にセルを作成
		Cell cell2 = newRow.createCell(1); // ヘッダーの列にセルを作成
		Cell cell3 = newRow.createCell(2); // ヘッダーの列にセルを作成
		cell1.setCellValue(country);
		cell1.setCellStyle(style); // セルスタイルを設定（フォントを適用）
		cell2.setCellValue(league);
		cell2.setCellStyle(style); // セルスタイルを設定（フォントを適用）
		cell3.setCellValue(convert_times);
		cell3.setCellStyle(style); // セルスタイルを設定（フォントを適用）
		for (int i = 3; i < connectRecords.length + 3; i++) {
			Cell cell = newRow.createCell(i); // ヘッダーの列にセルを作成
			int dataind = i - 3;
			cell.setCellValue(connectRecords[dataind]); // ヘッダーの値を設定
			cell.setCellStyle(style); // セルスタイルを設定（フォントを適用）
		}

		// 新しい行を作成（最後の行の次）
		Row newRow2 = sheet.createRow(lastRowNum + 2);

		// その新しい行に0データを挿入
		for (int i = 2; i < connectRecords.length + 3; i++) {
			Cell cell = newRow2.createCell(i); // ヘッダーの列にセルを作成
			cell.setCellValue(1); // ヘッダーの値を設定
			cell.setCellStyle(style); // セルスタイルを設定（フォントを適用）
		}

		// ファイルに変更を保存
		try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
			workbook.write(fileOut); // ファイルに書き込み
		}

		// ワークブックを閉じる
		workbook.close();
		file.close();
	}

	/**
	 * エクセルファイル内の特定の行を更新するメソッド
	 *
	 * @param filePath エクセルファイルのパス
	 * @param country 国
	 * @param league リーグ
	 * @param time 時間
	 * @param updatedRecord 更新するデータ（配列）
	 * @param rowIndex 更新する行番号（0-based index）
	 * @throws IOException ファイル作成時にエラーが発生した場合
	 */
	public static void updateRecord(String filePath, String country, String league, String time,
			String[][] updateRecord, int rowIndex) throws IOException {
		// エクセルファイルを読み込む
		FileInputStream file = new FileInputStream(new File(filePath));
		Workbook workbook = new XSSFWorkbook(file); // .xlsx形式のワークブックを作成

		// シートを取得（最初のシート）
		Sheet sheet = workbook.getSheetAt(0);
		if (sheet == null) {

		}

		CellStyle style = setFont(workbook);

		// 指定された行を取得
		Row row = sheet.getRow(rowIndex);
		if (row == null) {

		}

		// 指定された行を取得
		Row row_counter = sheet.getRow(rowIndex + 1);
		if (row_counter == null) {

		}

		// 更新元データを1次元配列に連結
		int feature_size = updateRecord.length;
		float[] exRecords = new float[feature_size * 2 + 1];
		String[] percentChkRecords = new String[feature_size * 2 + 1];
		int ex_feature_int = 0;
		for (Cell cell : row) {
			if (ex_feature_int == 0 || ex_feature_int == 1) {
				ex_feature_int++;
				continue;
			}
			int ex_feature_int_aft = ex_feature_int - 2;
			if (ex_feature_int_aft != 0) {
				if (cell.getStringCellValue().contains("%")) {
					percentChkRecords[ex_feature_int_aft] = "%";
				} else if (cell.getStringCellValue().endsWith(".")) {
					percentChkRecords[ex_feature_int_aft] = ".";
				} else {
					percentChkRecords[ex_feature_int_aft] = "";
				}
			} else {
				percentChkRecords[ex_feature_int_aft] = "";
			}
			switch (cell.getCellType()) {
			case STRING:
				float get_value1 = convertFloatValue(ex_feature_int, cell.getStringCellValue());
				exRecords[ex_feature_int_aft] = get_value1;
				break;
			case NUMERIC:
				float get_value2 = convertFloatValue(ex_feature_int,
						String.valueOf(cell.getNumericCellValue()));
				exRecords[ex_feature_int_aft] = get_value2;
				break;
			default:
				break;
			}
			ex_feature_int++;
		}

		// 有効件数を1次元配列に連結
		double[] exCounterRecords = new double[feature_size * 2 + 1];
		int ex_feature_counter_int = 0;
		for (Cell cell : row_counter) {
			switch (cell.getCellType()) {
			case NUMERIC:
				double get_value = cell.getNumericCellValue();
				exCounterRecords[ex_feature_counter_int] = get_value;
				break;
			default:
				exCounterRecords[ex_feature_counter_int] = 0;
				break;
			}
			ex_feature_counter_int++;
		}

		// 追加更新データを格納
		float[] connectRecords = new float[feature_size * 2 + 1];
		connectRecords[0] = convertTime(time).floatValue();
		int all_int = 0;
		for (int feature_int = 0; feature_int < feature_size; feature_int++) {
			all_int = feature_int * 2 + 1;
			connectRecords[all_int] = convertFloatValue(feature_int, updateRecord[feature_int][0]);
			all_int = feature_int * 2 + 2;
			connectRecords[all_int] = convertFloatValue(feature_int, updateRecord[feature_int][1]);
		}

		// 累積平均を導出
		int[] exCounterNextRecords = new int[feature_size * 2 + 1];
		String[] accumulateRecords = new String[feature_size * 2 + 1];
		for (int accumulate_int = 0; accumulate_int < exRecords.length; accumulate_int++) {
			// 増分(3つ分ずれる)
			exCounterNextRecords[accumulate_int] = incrementChk(exCounterRecords[accumulate_int],
					exRecords[accumulate_int]);
			// 累積平均
			accumulateRecords[accumulate_int] = accumulator(
					exRecords[accumulate_int],
					exCounterNextRecords[accumulate_int],
					connectRecords[accumulate_int],
					percentChkRecords[accumulate_int]);
		}

		// 更新するセルに新しいデータを設定
		for (int i = 2; i < accumulateRecords.length + 2; i++) {
			Cell cell = row.createCell(i); // 既存のセルを作成（または更新）
			int dataind = i - 2;
			cell.setCellValue(accumulateRecords[dataind]); // ヘッダーの値を設定
			cell.setCellStyle(style); // セルスタイルを設定（フォントを適用）
		}

		for (int i = 2; i < connectRecords.length + 2; i++) {
			Cell cell = row_counter.createCell(i); // 既存のセルを作成（または更新）
			int dataind = i - 2;
			cell.setCellValue(exCounterNextRecords[dataind]); // ヘッダーの値を設定
			cell.setCellStyle(style); // セルスタイルを設定（フォントを適用）
		}

		// ファイルに変更を保存
		try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
			workbook.write(fileOut); // ファイルに書き込み
		}

		// ワークブックを閉じる
		workbook.close();
		file.close();
	}

	/**
	 * フォントを指定する
	 * @param workbook
	 */
	private static CellStyle setFont(Workbook workbook) {
		// フォントを作成
		Font font = workbook.createFont();
		font.setFontHeightInPoints((short) 12); // フォントサイズを12に設定
		font.setFontName(EXCEL_FONT); // フォントをCalibriに設定

		// セルスタイルを作成
		CellStyle style = workbook.createCellStyle();
		style.setFont(font); // 作成したフォントをセルスタイルに設定
		return style;
	}

	/**
	 * エクセルから取得した値をfloatに変換する
	 * @param ex_feature_int 特徴index
	 * @param ex_conv_value 変換元値
	 * @return return_value 変換値
	 */
	private static float convertFloatValue(Integer ex_feature_int, String ex_conv_value) {
		//System.out.println("convertFloatValue: " + ex_feature_int + "番目,"+ ex_conv_value);
		// 順位表
		float return_value = 0;
		String conv_value = "";
		if (ex_conv_value != null && ex_conv_value.endsWith(".")) {
			conv_value = ex_conv_value.replace(".", "");
			return_value = Float.parseFloat(conv_value);
		} else if (ex_conv_value != null && ex_conv_value.endsWith("%")) {
			String tmp1 = ex_conv_value.replace("%", "");
			String tmp2 = tmp1.split("\\.")[0]; //小数点以下を削除
			int data = Integer.parseInt(tmp2);
			float flo = (float) data / 100;
			return_value = flo;
		} else if (ex_conv_value != null && ex_conv_value.contains(".")) {
			return_value = Float.parseFloat(ex_conv_value);
		} else if (ex_conv_value == null || ex_conv_value == "" || ex_conv_value == "0") {
			return_value = 0;
		} else if (ex_conv_value != null && ex_conv_value != "" && ex_conv_value != "0") {
			return_value = Float.parseFloat(ex_conv_value);
		} else {
			//System.out.println(ex_feature_int + "番目," + ex_conv_value);
			return_value = 0;
		}
		//System.out.println(ex_feature_int + "番目," + return_value);
		return return_value;
	}

	/**
	 * 累積平均を導出する(割合の場合は%表記に変更し直す)
	 * @param ex_calc_value 更新元データ
	 * @param valid_counter 有効件数
	 * @param sum_calc_value 加算データ
	 * @param placeHolder 埋め文字列(「%」,「.」など)
	 * @return
	 */
	private static String accumulator(float ex_calc_value, int valid_counter,
			float sum_calc_value, String placeHolder) {
		double calc = (ex_calc_value * (valid_counter - 1) + sum_calc_value) / valid_counter;
		if ("%".equals(placeHolder)) {
			calc *= 100;
		}
		String accumlate = String.format("%.2f", calc);
		return accumlate + placeHolder;
	}

	/**
	 * 有効件数を増分する
	 * @param increment_counter 有効件数
	 * @param increment_chk_string 有効件数を増分させるか判断するfloat（0,""など）
	 * @return
	 */
	private static Integer incrementChk(Double increment_counter, float increment_chk_string) {
		int intValue = (int) Math.floor(increment_chk_string);
		if (intValue == 0) {
			return (int) Math.floor(increment_counter);
		}
		return (int) Math.floor(increment_counter) + 1;
	}

	/**
	 * 試合時間を分表記に修正する
	 * @param matchTime 試合時間
	 * @return
	 */
	private static Double convertTime(String matchTime) {
		System.out.println("convertTime: " + matchTime);
		if (BookMakersCommonConst.HALF_TIME.equals(matchTime) ||
				BookMakersCommonConst.FIRST_HALF_TIME.equals(matchTime)) {
			return 45.0;
		}

		// "."が含まれている場合
		if (matchTime.contains(".")) {
			String[] parts = matchTime.split(".");
			return Double.parseDouble(parts[0]);
		}

		// ":"が含まれている場合（34:56形式）
		else if (matchTime.contains(":")) {
			String[] parts = matchTime.split(":");
			int minutes = Integer.parseInt(parts[0]);
			int seconds = Integer.parseInt(parts[1]);
			return minutes + (seconds / 60.0);
		}

		// "+"が含まれている場合（45+8'形式）
		else if (matchTime.contains("+")) {
			String[] parts = matchTime.split("\\+");
			int minutes = Integer.parseInt(parts[0].replace("'", ""));
			int seconds = Integer.parseInt(parts[1].replace("'", ""));
			return minutes + (seconds / 60.0);
		}

		// 単独の分（45'形式）
		else if (matchTime.endsWith("'")) {
			double minutes = Double.parseDouble(matchTime.replace("'", ""));
			return minutes;
		}

		// その他（不正なフォーマットが渡された場合は例外）
		throw new IllegalArgumentException("Invalid match time format: " + matchTime);
	}

}
