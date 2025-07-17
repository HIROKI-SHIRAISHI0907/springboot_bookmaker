package dev.common.readexcel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import dev.common.exception.BusinessException;


public class ReadExcel {

	/**
	 * エクセルファイルを読み込み、特定の文字列を検索
	 *
	 * @param filePath エクセルファイルのパス
	 * @param searchTerm 検索する文字列
	 * @return 存在する行番号
	 * @throws IOException
	 * @throws InvalidFormatException
	 */
	public Integer searchInExcel(String filePath, String searchCountry, String searchLeague) throws IOException, InvalidFormatException {
		// エクセルファイルを読み込む
		Workbook workbook = null;
		FileInputStream file = null;
		try {
			file = new FileInputStream(new File(filePath));
			workbook = WorkbookFactory.create(file); // ワークブックの作成
			Sheet sheet = workbook.getSheetAt(0); // 最初のシートを取得

			boolean countryFound = false;
			boolean leagueFound = false;
			// シートの全行をループ
			for (Row row : sheet) {
				// 各行のセルをループ
				for (Cell cell : row) {
					// セルの内容が検索する文字列と一致するか確認
					if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().equals(searchCountry)) {
						// 一致する場合、その行と列を表示
						countryFound = true;
					}

					if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().equals(searchLeague)) {
						// 一致する場合、その行と列を表示
						leagueFound = true;
					}

					if (countryFound && leagueFound) {
						//System.out.println(searchCountry + "," + searchLeague +
						//		"Found at Row: " + row.getRowNum());
						return row.getRowNum();
					}
				}
			}

			//System.out.println("指定された文字列は見つかりませんでした。" + searchCountry + "," + searchLeague);
			return -1;
		} catch (Exception e) {
			throw new BusinessException("", "", "",
					"読み込み時にエラーが発生しました。: " + e);
		} finally {
			// ファイルを閉じる
			workbook.close();
			file.close();
		}

	}
}
