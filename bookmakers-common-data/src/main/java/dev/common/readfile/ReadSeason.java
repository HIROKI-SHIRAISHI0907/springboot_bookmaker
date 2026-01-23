package dev.common.readfile;

import java.io.BufferedReader;
import java.io.FileInputStream;
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
	private static final String CLASS_NAME = ReadSeason.class.getSimpleName();

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
	public ReadFileOutputDTO getFileBody(String fileFullPath) {
		final String METHOD_NAME = "getFileBody";

		this.manageLoggerComponent.init(EXEC_MODE, fileFullPath);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		ReadFileOutputDTO readFileOutputDTO = new ReadFileOutputDTO();
		List<CountryLeagueSeasonMasterEntity> entiryList = new ArrayList<>();

		try {
			// =========================
			// CSV 処理
			// =========================
			if (fileFullPath != null && fileFullPath.toLowerCase().endsWith(".csv")) {

				try (BufferedReader br = new BufferedReader(
						new InputStreamReader(new FileInputStream(fileFullPath), StandardCharsets.UTF_8))) {

					String line;
					boolean headerSkipped = false;

					while ((line = br.readLine()) != null) {

						// BOM除去（UTF-8-SIG対策）: 1行目の先頭だけに付く可能性がある
						if (!headerSkipped && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
							line = line.substring(1);
						}

						// 行自体が完全に空ならスキップ
						if (line.isEmpty()) {
							continue;
						}

						// CSV簡易パース（ダブルクォート対応）
						List<String> cols = parseCsvLine(line);

						// ✅ 空欄があっても「列」としてカウントするため、期待列数まで空文字で埋める
						while (cols.size() < EXPECT_COLS) {
							cols.add("");
						}

						// 1行目はヘッダーとしてスキップ
						if (!headerSkipped) {
							headerSkipped = true;
							continue;
						}

						// ✅ 「空欄があっても文字列としてカウント」したいので、
						//    ここでは空欄チェックで弾かない。
						//    ただし、完全に空行（全列が空）だけは除外する。
						if (isAllEmpty(cols)) {
							continue;
						}

						// 取り出し（空欄でも "" が入る）
						String country = safeGet(cols, 0);
						String league  = safeGet(cols, 1);
						String year    = safeGet(cols, 2);
						String start   = safeGet(cols, 3);
						String end     = safeGet(cols, 4);
						String round   = safeGet(cols, 5);
						String path    = safeGet(cols, 6);
						String icon    = safeGet(cols, 7);

						CountryLeagueSeasonMasterEntity e = new CountryLeagueSeasonMasterEntity();
						e.setCountry(country);
						e.setLeague(league);
						e.setSeasonYear(year);
						e.setStartSeasonDate(start);
						e.setEndSeasonDate(end);
						e.setRound(round);
						e.setPath(path);
						e.setIcon(icon);

						entiryList.add(e);
					}
				}

				readFileOutputDTO.setResultCd(BookMakersCommonConst.NORMAL_CD);
				readFileOutputDTO.setCountryLeagueSeasonList(entiryList);
				return readFileOutputDTO;
			}

		} catch (Exception e) {
			readFileOutputDTO.setExceptionProject(PROJECT_NAME);
			readFileOutputDTO.setExceptionClass(CLASS_NAME);
			readFileOutputDTO.setExceptionMethod(METHOD_NAME);
			readFileOutputDTO.setResultCd(BookMakersCommonConst.ERR_CD_ERR_FILE_READS);
			readFileOutputDTO.setErrMessage(BookMakersCommonConst.ERR_MESSAGE_ERR_FILE_READS);
			readFileOutputDTO.setThrowAble(e);
			return readFileOutputDTO;

		} finally {
			// ✅ CSVでreturnした場合も含め、必ず終端ログ/クリア
			this.manageLoggerComponent.debugEndInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}

		return readFileOutputDTO;
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
