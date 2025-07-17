package dev.common.convcsv;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;


/**
 * CSVに変換するクラス
 * @author shiraishitoshio
 */
public class ConvertCSV {

    /** シングルトン */
    private ConvertCSV() {}

    /**
     * CSVに変換するロジック
     * @param convertBeforePath 変換前のファイルパス
     * @param csvNamePath 変換後のCSVの名前付きのパス
     */
    public static void convertExecute(String convertBeforePath, String csvNamePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(convertBeforePath));
             Writer writer = new OutputStreamWriter(new FileOutputStream(csvNamePath), StandardCharsets.UTF_8)) {
            Sheet sheet = workbook.getSheetAt(0); // 最初のシートを取得

            int maxColumns = getMaxColumns(sheet); // 最大列数

            int rowCount = 0;
            for (Row row : sheet) {
                StringBuilder rowStringBuilder = new StringBuilder();
                for (int colIndex = 0; colIndex < maxColumns; colIndex++) {
                    if (rowStringBuilder.length() > 0) {
                        rowStringBuilder.append(",");
                    }
                    Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    // セルの型に応じた値を取得
                    switch (cell.getCellType()) {
                        case STRING:
                        	rowStringBuilder.append(escapeCSV(cell.getStringCellValue()));
                            break;
                        case NUMERIC:
                        	if (rowCount > 0 & colIndex == 44) {
                        		rowStringBuilder.append(convertExcelDateToDateTimeUpper(cell.getNumericCellValue()));
                        	} else {
                        		rowStringBuilder.append(cell.getNumericCellValue());
                        	}
                            break;
                        case BOOLEAN:
                            rowStringBuilder.append(cell.getBooleanCellValue());
                            break;
                        case FORMULA:
                            rowStringBuilder.append(escapeCSV(cell.getCellFormula()));
                            break;
                        case BLANK:
                            rowStringBuilder.append(""); // 空白セル
                            break;
                        default:
                            rowStringBuilder.append("");
                            break;
                    }
                }
                writer.write(rowStringBuilder.toString());
                writer.write("\n");
                rowCount++;
            }
        } catch (Exception e) {
        	throw e;
		}
    }

    /**
     * CSVエスケープ処理
     * @param value 値
     * @return エスケープ後の文字列
     */
    private static String escapeCSV(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            value = value.replace("\"", "\"\""); // ダブルクォートをエスケープ
            return "\"" + value + "\"";
        }
        return value;
    }

    /**
     * 最大列数を取得する
     * @param sheet シート
     * @return 最大列数
     */
    private static int getMaxColumns(Sheet sheet) {
    	int maxColumns = 0;
    	for (Row row : sheet) {
    		maxColumns = Math.max(maxColumns, row.getLastCellNum());
    	}
    	return maxColumns;
    }

    /**
     * Excelのシリアル値をLocalDateTimeに変換する
     * @param excelDate Excelの日付値（例: 43568.865432）
     * @return 変換されたLocalDateTime
     */
    public static LocalDateTime convertExcelDateToDateTimeUpper(double excelDate) {
        // Excelの日付の基準日（1900年1月1日）
        LocalDate epoch = LocalDate.of(1900, 1, 1);

        // Excelのバグ（1900年2月29日を誤って認識している）を補正
        int days = (int) Math.floor(excelDate);
        if (days > 59) {
            days -= 1; // 1900年2月29日分を補正
        }

        // 基準日からの経過日数を追加
        LocalDate datePart = epoch.plusDays(days - 1); // Excelは1から始まるため、-1

        // 小数部分を時刻に変換
        double fractionalDay = excelDate - Math.floor(excelDate);
        int hours = (int) (fractionalDay * 24);
        int minutes = (int) ((fractionalDay * 24 - hours) * 60);
        int seconds = (int) (((fractionalDay * 24 - hours) * 60 - minutes) * 60);

        // 日付と時刻を統合
        return datePart.atTime(hours, minutes, seconds);
    }
}