package dev.common.getinfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.find.dto.FindBookInputDTO;
import dev.common.find.dto.FindBookOutputDTO;
import dev.common.findcsv.FindStat;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadTeam;
import dev.common.readfile.dto.ReadFileOutputDTO;

/**
 * チームマスタ情報取得管理クラス
 * @author shiraishitoshio
 *
 */
@Component
public class GetTeamMasterInfo {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = GetTeamMasterInfo.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = GetTeamMasterInfo.class.getSimpleName();

	/** Configクラス */
	@Autowired
	private PathConfig config;

	/**
	 * パス(/Users/shiraishitoshio/bookmaker/teams_by_leagueの予定)
	 */
	private String PATH;

	/**
	 * 統計データCsv読み取りクラス
	 */
	@Autowired
	private FindStat findStatCsv;

	/**
	 * ファイル読み込みクラス
	 */
	@Autowired
	private ReadTeam readTeam;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 取得メソッド
	 */
	public Map<String, List<CountryLeagueMasterEntity>> getData() {
		final String METHOD_NAME = "getData";
		// パス
		PATH = config.getTeamCsvFolder();

		// 時間計測開始
		long startTime = System.nanoTime();

		// 設定
		FindBookInputDTO findBookInputDTO = setBookInputDTO();

		// 統計データCsv読み取りクラス
		FindBookOutputDTO findBookOutputDTO = this.findStatCsv.execute(findBookInputDTO);

		// エラーの場合,戻り値の例外を業務例外に集約してスロー
		if (!BookMakersCommonConst.NORMAL_CD.equals(findBookOutputDTO.getResultCd())) {
			this.manageLoggerComponent.createBusinessException(
					findBookOutputDTO.getExceptionProject(),
					findBookOutputDTO.getExceptionClass(),
					findBookOutputDTO.getExceptionMethod(),
					findBookOutputDTO.getErrMessage(),
					findBookOutputDTO.getThrowAble());
		}

		// 読み込んだパスからデータ取得
		List<String> fileStatList = findBookOutputDTO.getBookList();
		if (fileStatList == null || fileStatList.isEmpty()) {
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, "データなし", "GetTeamMasterInfo");
			return null;
		}

		// ★ List<List<...>> → Map<String, List<...>>
		// 順序を維持したいので LinkedHashMap
		Map<String, List<CountryLeagueMasterEntity>> entityMap = new LinkedHashMap<>();

		for (String path : fileStatList) {
			try {
				ReadFileOutputDTO readFileOutputDTO = this.readTeam.getFileBody(path);
				List<CountryLeagueMasterEntity> entity = readFileOutputDTO.getCountryLeagueMasterList();
				// キー：ファイルパス、値：そのファイルから読んだリスト
				entityMap.put(path, entity);
			} catch (Exception e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, "ファイル読み込み失敗", e, path);
				this.manageLoggerComponent.createBusinessException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						"ファイル読み込みエラー: " + path,
						e);
			}
		}

		// 時間計測終了
		long endTime = System.nanoTime();
		long durationMs = (endTime - startTime) / 1_000_000; // ミリ秒に変換
		System.out.println("時間: " + durationMs);

		return entityMap;
	}

	/**
	 * 読み取りinputDTOに設定する
	 * @return
	 */
	private FindBookInputDTO setBookInputDTO() {
		FindBookInputDTO findBookInputDTO = new FindBookInputDTO();
		findBookInputDTO.setDataPath(PATH);
		findBookInputDTO.setPrefixFile(BookMakersCommonConst.TEAM_DATA_);
		findBookInputDTO.setSuffixFile(BookMakersCommonConst.CSV);
		findBookInputDTO.setContainsList(new String[0]);
		findBookInputDTO.setTargetFile(null);              // targetFile は使わない
		findBookInputDTO.setGetBookFlg(true);              // getFiles を使う
		findBookInputDTO.setCsvNumber(null);
		findBookInputDTO.setCsvBackNumber(null);
		return findBookInputDTO;
	}

}
