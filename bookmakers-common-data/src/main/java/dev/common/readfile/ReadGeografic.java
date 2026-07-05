package dev.common.readfile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.TeamLocationEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.dto.ReadFileOutputDTO;

/**
 * geografic CSV 読み込みクラス
 * @author shiraishitoshio
 */
@Component
public class ReadGeografic implements ReadFileBodyIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ReadGeografic.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ReadGeografic.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "READ_GEOGRAFIC";

	/**
	 * 期待するCSV列数
	 * id,teamId,teamName,teamNameTranslate,country,countryTranslate,
	 * homeCity,homeCityTranslate,stadiumName,stadiumNameTranslate,address,latitude,longitude,placeId,
	 * displayNameEn,addressEn,latitudeEn,longitudeEn,
	 * displayNameLocal,addressLocal,latitudeLocal,longitudeLocal,
	 * localLanguageCode,geocodeSource,validFrom,validTo
	 */
	private static final int EXPECT_COLS = 26;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * geografic CSVファイルの中身を取得する
	 * @param is InputStream
	 * @param key ログ用キー
	 * @return ReadFileOutputDTO
	 */
	@Override
	public ReadFileOutputDTO getFileBodyFromStream(InputStream is, String key) {
		final String METHOD_NAME = "getFileBodyFromStream";

		this.manageLoggerComponent.init(EXEC_MODE, key);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		ReadFileOutputDTO dto = new ReadFileOutputDTO();
		List<TeamLocationEntity> entityList = new ArrayList<>();

		try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			String line;
			boolean headerSkipped = false;

			while ((line = br.readLine()) != null) {
				if (!headerSkipped && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
					line = line.substring(1);
				}

				if (line.isEmpty()) {
					continue;
				}

				List<String> cols = parseCsvLine(line);
				while (cols.size() < EXPECT_COLS) {
					cols.add("");
				}

				// ヘッダ行スキップ
				if (!headerSkipped) {
					headerSkipped = true;
					continue;
				}

				// 全列空ならスキップ
				if (isAllEmpty(cols)) {
					continue;
				}

				TeamLocationEntity e = new TeamLocationEntity();

				e.setId(parseInteger(safeGet(cols, 0)));

				e.setTeamName(safeGet(cols, 2));
				e.setTeamNameTranslate(safeGet(cols, 3));

				e.setCountry(safeGet(cols, 4));
				e.setCountryTranslate(safeGet(cols, 5));

				e.setHomeCity(safeGet(cols, 6));
				e.setHomeCityTranslate(safeGet(cols, 7));

				e.setStadiumName(safeGet(cols, 8));
				e.setStadiumNameTranslate(safeGet(cols, 9));

				// 既存互換列
				e.setAddress(safeGet(cols, 10));
				e.setLatitude(parseBigDecimal(safeGet(cols, 11)));
				e.setLongitude(parseBigDecimal(safeGet(cols, 12)));

				// 追加列
				e.setPlaceId(safeGet(cols, 13));

				e.setDisplayNameEn(safeGet(cols, 14));
				e.setAddressEn(safeGet(cols, 15));
				e.setLatitudeEn(parseBigDecimal(safeGet(cols, 16)));
				e.setLongitudeEn(parseBigDecimal(safeGet(cols, 17)));

				e.setDisplayNameLocal(safeGet(cols, 18));
				e.setAddressLocal(safeGet(cols, 19));
				e.setLatitudeLocal(parseBigDecimal(safeGet(cols, 20)));
				e.setLongitudeLocal(parseBigDecimal(safeGet(cols, 21)));

				e.setLocalLanguageCode(safeGet(cols, 22));
				e.setGeocodeSource(safeGet(cols, 23));

				entityList.add(e);
			}

			dto.setResultCd(BookMakersCommonConst.NORMAL_CD);

			dto.setTeamLocationList(entityList);

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
	 * ※ 空欄も "" として返る
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
				cols.add(sb.toString());
				sb.setLength(0);
				continue;
			}

			sb.append(c);
		}

		cols.add(sb.toString());
		return cols;
	}

	/**
	 * 全列が "" のときだけ true
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

	private Integer parseInteger(String value) {
		if (value == null || value.isEmpty()) {
			return null;
		}
		return Integer.valueOf(value);
	}

	private BigDecimal parseBigDecimal(String value) {
		if (value == null || value.isEmpty()) {
			return null;
		}
		return new BigDecimal(value);
	}
}
