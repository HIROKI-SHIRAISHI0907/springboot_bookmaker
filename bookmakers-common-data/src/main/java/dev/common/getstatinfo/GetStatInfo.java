package dev.common.getstatinfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.BookDataEntity;
import dev.common.find.dto.FindBookInputDTO;
import dev.common.find.dto.FindBookOutputDTO;
import dev.common.findcsv.FindStat;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadStat;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.common.util.ExecuteMainUtil;

/**
 * 統計情報取得管理クラス
 * @author shiraishitoshio
 *
 */
@Component
public class GetStatInfo {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = GetStatInfo.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = GetStatInfo.class.getSimpleName();

	/**
	 * CSV原本パス
	 */
	private static final String PATH = "/Users/shiraishitoshio/bookmaker/csv/";

	/**
	 * 統計データCsv読み取りクラス
	 */
	@Autowired
	private FindStat findStatCsv;

	/**
	 * ファイル読み込みクラス
	 */
	@Autowired
	private ReadStat readStat;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 取得メソッド
	 */
	public Map<String, Map<String, List<BookDataEntity>>> getData(String csvNumber, String csvBackNumber) {
		final String METHOD_NAME = "getData";

		// 時間計測開始
		long startTime = System.nanoTime();

		//		DeleteFolderInFile deleteFolderInFile = new DeleteFolderInFile();
		//		deleteFolderInFile.delete(COPY_PATH);

		// 設定
		FindBookInputDTO findBookInputDTO = setBookInputDTO(csvNumber, csvBackNumber);

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
		// 結果構造：Map<"JPN-J1", Map<"HOME", List<BookDataEntity>>>
		Map<String, Map<String, List<BookDataEntity>>> resultMap = new HashMap<>();
		if (fileStatList.size() <= 0) {
			String messageCd = "データなし";
			String fillChar = "GetStatInfo";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar);
			return resultMap;
		}
		// スレッドプールを作成（例：同時に最大4スレッド）
		ExecutorService executor = Executors.newFixedThreadPool(fileStatList.size());
		// タスク送信
		List<Future<ReadFileOutputDTO>> futureList = new ArrayList<>();
		for (String file : fileStatList) {
			Future<ReadFileOutputDTO> future = executor.submit(() -> this.readStat.getFileBody(file));
			futureList.add(future);
		}

		for (Future<ReadFileOutputDTO> future : futureList) {
			try {
				ReadFileOutputDTO dto = future.get();
				List<BookDataEntity> entity = dto.getReadHoldDataList();
				if (entity == null) {
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, null);
					this.manageLoggerComponent.createBusinessException(
							PROJECT_NAME,
							CLASS_NAME,
							METHOD_NAME,
							"entity: nullエラー",
							null);
				}

				String[] data_key = ExecuteMainUtil.splitLeagueInfo(entity.get(0).getGameTeamCategory());
				String country = data_key[0];
				String league = data_key[1];
				String home = entity.get(0).getHomeTeamName();
				String away = entity.get(0).getAwayTeamName();

				if (country == null || league == null ||
						home == null || away == null) {
					// country/league/home/awayがnullのためスキップ
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, null);
					this.manageLoggerComponent.createBusinessException(
							PROJECT_NAME,
							CLASS_NAME,
							METHOD_NAME,
							"country/league/home/away: nullエラー, " +
									country + ", " + league + ", " + home +
									", " + away,
							null);
					continue;
				}

				String mapKey = country + "-" + league;
				String teamKey = home + "-" + away;
				// データをマップに追加
				resultMap
						.computeIfAbsent(mapKey, k -> new HashMap<>())
						.computeIfAbsent(teamKey, s -> new ArrayList<>())
						.addAll(entity);
			} catch (InterruptedException | ExecutionException e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, e);
				this.manageLoggerComponent.createBusinessException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						"InterruptedException|ExecutionException: エラー",
						e);
			}
		}
		executor.shutdown();

		// 時間計測終了
		long endTime = System.nanoTime();
		long durationMs = (endTime - startTime) / 1_000_000; // ミリ秒に変換

		System.out.println("時間: " + durationMs);

		return resultMap;
	}

	/**
	 * 読み取りinputDTOに設定する
	 * @param csvNumber CSV番号
	 * @param csvBackNumber CSV番号
	 * @return
	 */
	private FindBookInputDTO setBookInputDTO(String csvNumber, String csvBackNumber) {
		FindBookInputDTO findBookInputDTO = new FindBookInputDTO();
		findBookInputDTO.setDataPath(PATH);
		findBookInputDTO.setCopyFlg(false);
		findBookInputDTO.setGetBookFlg(false);
		findBookInputDTO.setCsvNumber(csvNumber);
		findBookInputDTO.setCsvBackNumber(csvBackNumber);
		String[] containsList = new String[6];
		containsList[0] = "breakfile";
		containsList[1] = "all.csv";
		containsList[3] = "conditiondata/";
		containsList[4] = "python_analytics/";
		containsList[5] = "average_stats/";
		findBookInputDTO.setContainsList(containsList);
		findBookInputDTO.setSuffixFile(BookMakersCommonConst.CSV);
		return findBookInputDTO;
	}

}
