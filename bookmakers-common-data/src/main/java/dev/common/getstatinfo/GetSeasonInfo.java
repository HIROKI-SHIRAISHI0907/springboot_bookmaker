package dev.common.getstatinfo;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.find.dto.FindBookInputDTO;
import dev.common.find.dto.FindBookOutputDTO;
import dev.common.findcsv.FindStat;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadSeason;
import dev.common.readfile.dto.ReadFileOutputDTO;

/**
 * シーズン情報取得管理クラス
 * @author shiraishitoshio
 *
 */
@Component
public class GetSeasonInfo {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = GetSeasonInfo.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = GetSeasonInfo.class.getSimpleName();

	/** Configクラス */
	@Autowired
	private PathConfig config;

	/**
	 * パス(/Users/shiraishitoshio/bookmaker/の予定)
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
	private ReadSeason readSeason;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 取得メソッド
	 */
	public List<CountryLeagueSeasonMasterEntity> getData() {
		final String METHOD_NAME = "getData";
		// パス
		PATH = config.getOutputCsvFolder();

		// 時間計測開始
		long startTime = System.nanoTime();

		// 設定
		FindBookInputDTO findBookInputDTO = setBookInputDTO();

		// 統計データXlsx読み取りクラス
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
		String fileStatList = findBookOutputDTO.getBookList().get(0);
		// 結果構造：Map<"JPN-J1", Map<"HOME", List<BookDataEntity>>>
		if (fileStatList == null) {
			String messageCd = "データなし";
			String fillChar = "GetSeasonInfo";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar);
			return null;
		}

		ReadFileOutputDTO readFileOutputDTO = this.readSeason.getFileBody(fileStatList);
		List<CountryLeagueSeasonMasterEntity> entity = null;
		try {
			entity = readFileOutputDTO.getCountryLeagueSeasonList();
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, e);
			this.manageLoggerComponent.createBusinessException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					"InterruptedException|ExecutionException: エラー",
					e);
		}
		//executor.shutdown();

		// 時間計測終了
		long endTime = System.nanoTime();
		long durationMs = (endTime - startTime) / 1_000_000; // ミリ秒に変換

		System.out.println("時間: " + durationMs);
		return entity;
	}

	/**
	 * 読み取りinputDTOに設定する
	 * @return
	 */
	private FindBookInputDTO setBookInputDTO() {
		FindBookInputDTO findBookInputDTO = new FindBookInputDTO();
		findBookInputDTO.setDataPath(PATH);
		findBookInputDTO.setTargetFile(BookMakersCommonConst.SEASON_CSV);
		return findBookInputDTO;
	}

}
