package dev.application.analyze.bm_m007_bm_m016;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.application.domain.repository.TimeRangeFeatureAllLeagueRepository;
import dev.application.domain.repository.TimeRangeFeatureRepository;
import dev.application.domain.repository.TimeRangeFeatureScoredRepository;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;
import jakarta.annotation.PreDestroy;

/**
 * 非同期キュー
 * @author shiraishitoshio
 *
 */
@Component
public class TimeRangeFeatureQueueService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TimeRangeFeatureStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TimeRangeFeatureStat.class.getSimpleName();

	/** executorService */
	private final ThreadPoolExecutor executorService = new ThreadPoolExecutor(
			10, // core
			20, // max
			60L, TimeUnit.SECONDS, // idle timeout
			new LinkedBlockingQueue<>(1000), // task queue
			Executors.defaultThreadFactory(),
			new ThreadPoolExecutor.CallerRunsPolicy()); // fallback

	/** TimeRangeFeatureRepositoryレポジトリクラス */
	@Autowired
	private TimeRangeFeatureRepository timeRangeFeatureRepository;

	/** TimeRangeFeatureScoredRepositoryレポジトリクラス */
	@Autowired
	private TimeRangeFeatureScoredRepository timeRangeFeatureScoredRepository;

	/** TimeRangeFeatureAllLeagueRepositoryレポジトリクラス */
	@Autowired
	private TimeRangeFeatureAllLeagueRepository timeRangeFeatureAllLeagueRepository;

	/** BookDataToTimeRangeFeatureMapperクラス */
	@Autowired
	private BookDataToTimeRangeFeatureMapper bookDataToTimeRangeFeatureMapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/** スレッドのシャットダウン */
	@PreDestroy
	public void shutdownExecutor() {
		try {
			executorService.shutdown();
			if (!executorService.awaitTermination(5, TimeUnit.MINUTES)) {
				executorService.shutdownNow();
			}
		} catch (InterruptedException ex) {
			executorService.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Mainテーブル登録キュー
	 * @param mapKey
	 * @param entity
	 */
	public void enqueueMainInsert(String mapKey, BookDataEntity entity) {
		executorService.submit(() -> {
			final String METHOD_NAME = "enqueueMainInsert";
			try {
				TimeRangeFeatureOutputDTO dto = splitTeamKey(mapKey);
				String country = dto.getCountry();
				String league = dto.getLeague();
				String home = dto.getHome();
				String away = dto.getAway();
				String seq1 = dto.getSeq1(); // 連番ID
				// ログ設定
				String tableId = "登録テーブルID: (" + TimeRangeFeatureTableMapUtil.getTableMap(Integer.parseInt(seq1)) + ")";
				String loggers = setLogger(country, league, home, away) + ", " + tableId;

				TimeRangeFeatureEntity timeRangeFeatureEntity = this.bookDataToTimeRangeFeatureMapper.mapStruct(entity,
						seq1);
				int result = this.timeRangeFeatureRepository.insert(timeRangeFeatureEntity);
				if (result != 1) {
					String messageCd = "新規登録エラー";
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, loggers);
					this.manageLoggerComponent.createSystemException(
							PROJECT_NAME,
							CLASS_NAME,
							METHOD_NAME,
							messageCd,
							null);
				}
			} catch (Exception ex) {
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME,
						CLASS_NAME, METHOD_NAME, "非同期登録失敗", ex, mapKey);
			}
		});
	}

	/**
	 * MainSubテーブル登録キュー
	 * @param mapKey
	 * @param entity
	 */
	public void enqueueCommonInsert(String mapKey, BookDataEntity entity, String tableName) {
		executorService.submit(() -> {
			final String METHOD_NAME = "enqueueCommonInsert";
			try {
				TimeRangeFeatureOutputDTO dto = splitTeamKey(mapKey);
				String country = dto.getCountry();
				String league = dto.getLeague();
				String home = dto.getHome();
				String away = dto.getAway();
				String seq1 = dto.getSeq1();
				String time = entity.getTime();

				String tableId = "登録テーブルID: (" + TimeRangeFeatureTableMapUtil.getTableMap(Integer.parseInt(seq1)) + ")";
				String loggers = setLogger(country, league, home, away) + ", " + tableId;

				// フィールド取得 → パラレル処理でマップ構築
				Field[] fields = entity.getClass().getDeclaredFields();
				Map<String, String> newFeatureMap = Arrays.stream(fields).parallel()
						.filter(f -> !ExecuteMainUtil.chkExclusive(f.getName()))
						.collect(Collectors.toMap(
								Field::getName,
								f -> {
									try {
										String value = (String) f.get(entity);
										if (ExecuteMainUtil.chkRonriSplit(f.getName())) {
											List<String> split = ExecuteMainUtil.splitGroup(value);
											return "[SPLIT]" + split.get(1) + "|" + split.get(2); // 一時的に文字列で結合
										} else {
											return value;
										}
									} catch (Exception e) {
										String messageCd = "entityエラー";
										manageLoggerComponent.debugErrorLog(
												PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, loggers);
										return null;
									}
								}));

				// 登録処理：並列処理（I/Oがボトルネックの場合有効） ※同時書き込みに注意
				newFeatureMap.entrySet().parallelStream().forEach(entry -> {
					String feature = entry.getKey();
					String thresHoldRaw = entry.getValue();
					if (thresHoldRaw == null)
						return;

					// indexごとに処理分割
					if ("3".equals(seq1) || "5".equals(seq1) || "6".equals(seq1) ||
							"8".equals(seq1) || "10".equals(seq1)) {
						// DB取得
						TimeRangeFeatureOutputDTO dtos = getScoredData(
								time, feature, thresHoldRaw, country, league, tableName);
						boolean updFlg = dtos.isUpdFlg();
						String id = dtos.getId();
						String target = dtos.getTarget();
						String search = dtos.getSearch();

						if (thresHoldRaw.startsWith("[SPLIT]")) {
							String[] split = thresHoldRaw.replace("[SPLIT]", "").split("\\|");
							// 閾値設定
							saveScoredData(feature + "_success", split[0], loggers, country, league, METHOD_NAME,
									tableName, target, search, updFlg, id);
							// 閾値設定
							saveScoredData(feature + "_try", split[1], loggers, country, league,
									METHOD_NAME, tableName, target, search, updFlg, id);
						} else {
							// 閾値設定
							saveScoredData(feature, thresHoldRaw, loggers, country, league, METHOD_NAME,
									tableName, target, search, updFlg, id);
						}
					} else if ("2".equals(seq1) || "4".equals(seq1) || "7".equals(seq1) ||
							"9".equals(seq1)) {
						// DB取得
						TimeRangeFeatureOutputDTO dtos = getAllLeagueData(
								time, feature, thresHoldRaw, tableName);
						boolean updFlg = dtos.isUpdFlg();
						String id = dtos.getId();
						String target = dtos.getTarget();
						String search = dtos.getSearch();

						if (thresHoldRaw.startsWith("[SPLIT]")) {
							String[] split = thresHoldRaw.replace("[SPLIT]", "").split("\\|");
							// 閾値設定
							saveAllLeagueData(feature + "_success", split[0], loggers, METHOD_NAME,
									tableName, target, search, updFlg, id);
							// 閾値設定
							saveAllLeagueData(feature + "_try", split[1], loggers,
									METHOD_NAME, tableName, target, search, updFlg, id);
						} else {
							// 閾値設定
							saveAllLeagueData(feature, thresHoldRaw, loggers,
									METHOD_NAME, tableName, target, search, updFlg, id);
						}
					}
				});
			} catch (Exception ex) {
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME,
						CLASS_NAME, METHOD_NAME, "非同期登録失敗", ex, mapKey);
			}
		});
	}

	/**
	 * 取得データ
	 * @param timeRange 時間範囲
	 * @param feature 特徴量
	 * @param thresHold 閾値
	 * @param country 国
	 * @param league リーグ
	 * @param tableName テーブル名
	 * @return
	 */
	private TimeRangeFeatureOutputDTO getScoredData(String timeRange, String feature, String thresHold,
			String country, String league, String tableName) {
		TimeRangeFeatureOutputDTO dto = new TimeRangeFeatureOutputDTO();
		List<TimeRangeFeatureScoredEntity> dataEntities = this.timeRangeFeatureScoredRepository.findData(
				country, league, timeRange, feature, tableName);
		dto.setUpdFlg(false);
		if (!dataEntities.isEmpty()) {
			dto.setId(dataEntities.get(0).getId());
			dto.setTarget(dataEntities.get(0).getTarget());
			dto.setSearch(dataEntities.get(0).getSearch());
			dto.setUpdFlg(true);
		}
		return dto;
	}

	/**
	 * 取得データ
	 * @param timeRange 時間範囲
	 * @param feature 特徴量
	 * @param thresHold 閾値
	 * @param tableName テーブル名
	 * @return
	 */
	private TimeRangeFeatureOutputDTO getAllLeagueData(String timeRange, String feature, String thresHold,
			String tableName) {
		TimeRangeFeatureOutputDTO dto = new TimeRangeFeatureOutputDTO();
		List<TimeRangeFeatureAllLeagueEntity> dataEntities = this.timeRangeFeatureAllLeagueRepository
				.findData(timeRange, feature, tableName);
		dto.setUpdFlg(false);
		if (!dataEntities.isEmpty()) {
			dto.setId(dataEntities.get(0).getId());
			dto.setTarget(dataEntities.get(0).getTarget());
			dto.setSearch(dataEntities.get(0).getSearch());
		}
		return dto;
	}

	/**
	 * scored登録メソッド
	 * @param feature 特徴量
	 * @param thresHold 閾値
	 * @param loggers ログ
	 * @param country 国
	 * @param league リーグ
	 * @param methodName メソッド名
	 * @param tableName テーブル名
	 * @param updFlg 更新フラグ
	 * @param id ID
	 */
	private synchronized void saveScoredData(String feature, String thresHold, String loggers,
			String country, String league, String methodName, String tableName,
			String target, String search, boolean updFlg, String id) {
		if (updFlg) {
			if (this.timeRangeFeatureScoredRepository.update(id, tableName, target, search) != 1) {
				logAndThrow("更新エラー", methodName, loggers);
			}
		} else {
			TimeRangeFeatureScoredEntity entityA = new TimeRangeFeatureScoredEntity();
			entityA.setTableName(tableName);
			entityA.setCountry(country);
			entityA.setLeague(league);
			entityA.setTimeRange(loggers);
			entityA.setFeature(feature);
			entityA.setThresHold(thresHold);
			entityA.setTarget(loggers);
			entityA.setSearch(loggers);
			if (this.timeRangeFeatureScoredRepository.insert(entityA) != 1) {
				logAndThrow("新規登録エラー", methodName, loggers);
			}
		}
	}

	/**
	 * allleague登録メソッド
	 * @param feature 特徴量
	 * @param thresHold 閾値
	 * @param loggers ログ
	 * @param methodName メソッド名
	 * @param tableName テーブル名
	 * @param updFlg 更新フラグ
	 * @param id ID
	 */
	private synchronized void saveAllLeagueData(String feature, String thresHold, String loggers,
			String methodName, String tableName, String target, String search,
			boolean updFlg, String id) {
		if (updFlg) {
			if (this.timeRangeFeatureAllLeagueRepository.update(id,
					tableName, target, search) != 1) {
				logAndThrow("更新エラー", methodName, loggers);
			}
		} else {
			TimeRangeFeatureAllLeagueEntity entityB = new TimeRangeFeatureAllLeagueEntity();
			entityB.setTableName(tableName);
			entityB.setTimeRange(loggers);
			entityB.setFeature(feature);
			entityB.setThresHold(thresHold);
			entityB.setTarget(loggers);
			entityB.setSearch(loggers);
			if (this.timeRangeFeatureAllLeagueRepository.insert(entityB) != 1) {
				logAndThrow("新規登録エラー", methodName, loggers);
			}
		}
	}

	/**
	 * 例外共通メソッド
	 * @param messageCd メッセージコード
	 * @param methodName メソッド名
	 * @param loggers ログ
	 */
	private void logAndThrow(String messageCd, String methodName, String loggers) {
		this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, methodName, messageCd, null, loggers);
		this.manageLoggerComponent.createSystemException(PROJECT_NAME, CLASS_NAME, methodName, messageCd, null);
	}

	/**
	 * ログ設定
	 * @param country 国
	 * @param league リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @return
	 */
	private String setLogger(String country, String league, String home, String away) {
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append("country: " + country + ", ");
		sBuilder.append("league: " + league + ", ");
		sBuilder.append("home: " + home + ", ");
		sBuilder.append("away: " + away + ", ");
		return sBuilder.toString();
	}

	/**
	 * テーブル情報マップを取得
	 * @param key キー(国-リーグ-ホーム-アウェー-連番)
	 * @return テーブル情報のマップ
	 */
	private TimeRangeFeatureOutputDTO splitTeamKey(String mapKey) {
		final String METHOD_NAME = "splitTeamKey";

		TimeRangeFeatureOutputDTO timeRangeFeatureOutputDTO = new TimeRangeFeatureOutputDTO();
		String[] key = mapKey.split("-");
		if (key.length < 4) {
			String messageCd = "";
			this.manageLoggerComponent.createSystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null);
		}
		timeRangeFeatureOutputDTO.setCountry(key[0]);
		timeRangeFeatureOutputDTO.setLeague(key[1]);
		timeRangeFeatureOutputDTO.setHome(key[2]);
		timeRangeFeatureOutputDTO.setAway(key[3]);
		if (key.length > 4) {
			timeRangeFeatureOutputDTO.setSeq1(key[4]);
		}
		return timeRangeFeatureOutputDTO;
	}

	/**
	 * 定期的にキューの状態を監視してログ出力する
	 */
	@Scheduled(fixedDelay = 3000)
	public void monitorQueueStatus() {
		final String METHOD_NAME = "monitorQueueStatus";
		int queueSize = getQueueSize();
		int active = getActiveTaskCount();
		long completed = getCompletedTaskCount();
		long total = getTotalTaskCount();

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				String.format("QueueSize=%d, ActiveThreads=%d, Completed=%d, Total=%d",
						queueSize, active, completed, total));
	}

	/**
	 * 現在のキューサイズ（待機中のタスク数）を返す
	 */
	public int getQueueSize() {
		return executorService.getQueue().size();
	}

	/**
	 * 現在実行中のタスク数（アクティブスレッド数）を返す
	 */
	public int getActiveTaskCount() {
		return executorService.getActiveCount();
	}

	/**
	 * 現在のスレッドプールサイズを返す（実際に存在しているスレッドの数）
	 */
	public int getPoolSize() {
		return executorService.getPoolSize();
	}

	/**
	 * 送信済みの累計タスク数を返す（成功/失敗問わず）
	 */
	public long getTotalTaskCount() {
		return executorService.getTaskCount();
	}

	/**
	 * 完了済みの累計タスク数を返す
	 */
	public long getCompletedTaskCount() {
		return executorService.getCompletedTaskCount();
	}
}
