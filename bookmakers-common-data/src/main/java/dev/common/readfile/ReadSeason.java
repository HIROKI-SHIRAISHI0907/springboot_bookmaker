package dev.common.readfile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.dto.ReadFileOutputDTO;

/**
 * ファイル読み込みクラス
 * @author shiraishitoshio
 */
@Component
public class ReadSeason implements ReadFileBodyIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ReadSeason.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ReadSeason.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "READ_SEASON";

	/** 期待するCSV列数（country, league, season_year, start, end, round, path, icon） */
	private static final int EXPECT_COLS = 8;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 統計データファイルの中身を取得する
	 * @param fileFullPath ファイル名（フルパス）
	 * @return readFileOutputDTO
	 */
	@Override
	public ReadFileOutputDTO getFileBodyFromStream(InputStream is, String key) {
	    final String METHOD_NAME = "getFileBodyFromStream";

	    this.manageLoggerComponent.init(EXEC_MODE, key);
	    this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

	    ReadFileOutputDTO dto = new ReadFileOutputDTO();
	    List<CountryLeagueSeasonMasterEntity> entiryList = new ArrayList<>();

	    try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
	        String line;
	        boolean headerSkipped = false;
	        while ((line = br.readLine()) != null) {
	            if (!headerSkipped && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
	                line = line.substring(1);
	            }
	            if (line.isEmpty()) continue;
	            List<String> cols = parseCsvLine(line);
	            while (cols.size() < EXPECT_COLS) cols.add("");
	            if (!headerSkipped) {
	                headerSkipped = true;
	                continue;
	            }
	            if (isAllEmpty(cols)) continue;
	            CountryLeagueSeasonMasterEntity e = new CountryLeagueSeasonMasterEntity();
	            e.setCountry(safeGet(cols, 0));
	            e.setLeague(safeGet(cols, 1));
	            e.setSeasonYear(safeGet(cols, 2));
	            e.setStartSeasonDate(safeGet(cols, 3));
	            e.setEndSeasonDate(safeGet(cols, 4));
	            e.setRound(safeGet(cols, 5));
	            e.setPath(safeGet(cols, 6));
	            e.setIcon(safeGet(cols, 7));
	            entiryList.add(e);
	        }
	        dto.setResultCd(BookMakersCommonConst.NORMAL_CD);
	        dto.setCountryLeagueSeasonList(entiryList);
	        return dto;

	    } catch (Exception e) {
	        dto.setExceptionProject(PROJECT_NAME);
	        dto.setExceptionClass(CLASS_NAME);
	        dto.setExceptionMethod(METHOD_NAME);
	        dto.setResultCd(BookMakersCommonConst.ERR_CD_ERR_FILE_READS);
	        dto.setErrMessage(BookMakersCommonConst.ERR_MESSAGE_ERR_FILE_READS);
	        dto.setThrowAble(e);
	        return dto;

	    } finally {
	        this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	        this.manageLoggerComponent.clear();
	    }
	}

	/**
	 * CSV 1行を簡易パース（ダブルクォート対応、"" は " に展開）
	 * ※ 空欄も "" として返る（例: "a,,b" -> ["a","","b"]）
	 */
	private List<String> parseCsvLine(String line) {
		List<String> cols = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		boolean inQuotes = false;

		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);

			if (c == '"') {
				// "" -> "
				if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					sb.append('"');
					i++;
				} else {
					inQuotes = !inQuotes;
				}
				continue;
			}

			if (c == ',' && !inQuotes) {
				cols.add(sb.toString()); // ✅ trimしない：空欄やスペースも「文字列」として保持
				sb.setLength(0);
				continue;
			}

			sb.append(c);
		}

		// 最後の列
		cols.add(sb.toString());
		return cols;
	}

	/**
	 * 全列が "" のときだけ true（空白スペースのみは「文字列」として扱いたいので empty 判定にしない）
	 */
	private boolean isAllEmpty(List<String> cols) {
		for (String v : cols) {
			if (v != null && !v.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private String safeGet(List<String> cols, int idx) {
		if (cols == null || idx < 0 || idx >= cols.size()) {
			return "";
		}
		String v = cols.get(idx);
		return v == null ? "" : v;
	}
}
