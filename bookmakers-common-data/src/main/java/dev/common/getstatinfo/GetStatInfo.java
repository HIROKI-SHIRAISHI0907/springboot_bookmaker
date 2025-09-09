package dev.common.getstatinfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
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

	/** Configクラス */
	@Autowired
	private PathConfig config;

	/**
	 * パス
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
	private ReadStat readStat;

	/** CSV用のスレッドプール（CsvQueueConfigで定義） */
	@Autowired
	private ThreadPoolTaskExecutor csvTaskExecutor;

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
		// パス
		PATH = config.getCsvFolder();

		// 時間計測開始
		long startTime = System.nanoTime();

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
		// 結果構造：Map<"JPN-J1", Map<"HOME"-"AWAY", List<BookDataEntity>>>
		Map<String, Map<String, List<BookDataEntity>>> resultMap = new HashMap<>();
		if (fileStatList.size() <= 0) {
			String messageCd = "データなし";
			String fillChar = "GetStatInfo";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar);
			return resultMap;
		}
		// ★ 同時実行をハード制限（CsvQueueConfigの並列数に合わせる）
		final int concurrency = 8; // または csvTaskExecutor.getMaxPoolSize()
		final Semaphore gate = new Semaphore(concurrency);

		List<CompletableFuture<ReadFileOutputDTO>> futures = new ArrayList<>(fileStatList.size());
		for (String file : fileStatList) {
			try {
				// キューを溢れさせない（ここでブロックしてin-flight最大数を制限）
				gate.acquire();
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				this.manageLoggerComponent.createBusinessException(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, "Semaphore acquire 中断", ie);
				break;
			}
			CompletableFuture<ReadFileOutputDTO> cf = CompletableFuture
					.supplyAsync(() -> this.readStat.getFileBody(file), csvTaskExecutor)
					.whenComplete((r, t) -> gate.release());
			futures.add(cf);
		}

		for (CompletableFuture<ReadFileOutputDTO> cf : futures) {
			try {
				ReadFileOutputDTO dto = cf.join(); // get()と違いchecked例外なし
				if (dto == null) {
					this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, null);
					this.manageLoggerComponent.createBusinessException(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, "dto: nullエラー", null);
					continue;
				}
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

				String mapKey = entity.get(0).getGameTeamCategory();
				String teamKey = home + "-" + away;
				// データをマップに追加
				resultMap
						.computeIfAbsent(mapKey, k -> new HashMap<>())
						.computeIfAbsent(teamKey, s -> new ArrayList<>())
						.addAll(entity);
			} catch (RuntimeException e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, e);
				this.manageLoggerComponent.createBusinessException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						"非同期処理中にエラー",
						e);
			}
		}
		// Spring管理のExecutorはアプリ終了時に停止するためshutdown不要

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
