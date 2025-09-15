package dev.common.readfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.common.server.TimeConfig;
import dev.common.util.DateStatHelper;

/**
 * ファイル読み込みクラス
 * @author shiraishitoshio
 *
 */
@Component
public class ReadSeason {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ReadSeason.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ReadSeason.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "READ_SEASON";

	/** TimeConfigクラス */
	@Autowired
	private TimeConfig timeConfig;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 統計データファイルの中身を取得する
	 * @param fileFullPath ファイル名（フルパス）
	 * @return readFileOutputDTO
	 */
	public ReadFileOutputDTO getFileBody(String fileFullPath) {
		final String METHOD_NAME = "getFileBody";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, fileFullPath);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		ReadFileOutputDTO readFileOutputDTO = new ReadFileOutputDTO();
		List<CountryLeagueSeasonMasterEntity> entiryList = new ArrayList<CountryLeagueSeasonMasterEntity>();
		try (InputStream is = new FileInputStream(new File(fileFullPath));
				Workbook wb = WorkbookFactory.create(is)) {
			if (wb.getNumberOfSheets() == 0) {
				readFileOutputDTO.setResultCd(BookMakersCommonConst.ERR_CD_NO_SHEET_EXISTS);
				readFileOutputDTO.setCountryLeagueSeasonList(entiryList);
				return readFileOutputDTO; // シートなし
			}
			Sheet sheet = wb.getSheetAt(0);
			DataFormatter formatter = new DataFormatter(); // 表示通りの文字列に整形
			int firstRow = sheet.getFirstRowNum();
			int lastRow = sheet.getLastRowNum();

			for (int r = firstRow + 1; r <= lastRow; r++) { // 1行目をヘッダーとしてスキップ
				Row row = sheet.getRow(r);
				if (row == null)
					continue;

				String country = cellString(row.getCell(0), formatter);
				String league = cellString(row.getCell(1), formatter);
				String start = cellString(row.getCell(2), formatter);
				String end = cellString(row.getCell(3), formatter);
				String round = cellString(row.getCell(4), formatter);
				String path = cellString(row.getCell(5), formatter);
				String icon = cellString(row.getCell(6), formatter);

				// 全部国,リーグが空ならスキップ
				if (country.isEmpty() && league.isEmpty())
					continue;

				// 日付変換
				DateStatHelper.SeasonIso season =
	                    DateStatHelper.toSeasonIso(start, end, this.timeConfig.clock());
	            String startConv = season.startIso;
	            String endConv   = season.endIso;

				CountryLeagueSeasonMasterEntity e = new CountryLeagueSeasonMasterEntity();
				e.setCountry(country);
				e.setLeague(league);
				e.setStartSeasonDate(startConv);
				e.setEndSeasonDate(endConv);
				e.setRound(round);
				e.setPath(path);
				e.setIcon(icon);
				entiryList.add(e);
			}
			readFileOutputDTO.setResultCd(BookMakersCommonConst.NORMAL_CD);
			readFileOutputDTO.setCountryLeagueSeasonList(entiryList);
		} catch (Exception e) {
			readFileOutputDTO.setExceptionProject(PROJECT_NAME);
			readFileOutputDTO.setExceptionClass(CLASS_NAME);
			readFileOutputDTO.setExceptionMethod(METHOD_NAME);
			readFileOutputDTO.setResultCd(BookMakersCommonConst.ERR_CD_ERR_FILE_READS);
			readFileOutputDTO.setErrMessage(BookMakersCommonConst.ERR_MESSAGE_ERR_FILE_READS);
			readFileOutputDTO.setThrowAble(e);
			return readFileOutputDTO;
		}

		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();

		return readFileOutputDTO;
	}

	/**
	 * セルを文字列化（DataFormatterで型を意識せず取得）
	 * @param cell
	 * @param formatter
	 * @return
	 */
    private static String cellString(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        // 数値・日付・文字列などをセルの見た目どおりに
        String s = formatter.formatCellValue(cell);
        return s != null ? s.trim() : "";
    }

}
