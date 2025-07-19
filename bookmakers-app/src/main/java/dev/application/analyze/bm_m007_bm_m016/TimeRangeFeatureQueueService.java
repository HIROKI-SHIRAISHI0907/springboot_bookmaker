package dev.application.analyze.bm_m007_bm_m016;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.application.domain.repository.TimeRangeFeatureAllLeagueRepository;
import dev.application.domain.repository.TimeRangeFeatureScoredRepository;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;
import jakarta.annotation.PreDestroy;

/**
 * 非同期キュークラス
 * @author shiraishitoshio
 *
 */
@Component
public class TimeRangeFeatureQueueService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TimeRangeFeatureQueueService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TimeRangeFeatureQueueService.class.getSimpleName();

	/** executorService */
	private final ThreadPoolExecutor executorService = new ThreadPoolExecutor(
			10, // core
			20, // max
			60L, TimeUnit.SECONDS, // idle timeout
			new LinkedBlockingQueue<>(1000), // task queue
			Executors.defaultThreadFactory(),
			new ThreadPoolExecutor.CallerRunsPolicy()); // fallback

	/** TimeRangeFeatureScoredRepositoryレポジトリクラス */
	@Autowired
	private TimeRangeFeatureScoredRepository timeRangeFeatureScoredRepository;

	/** TimeRangeFeatureAllLeagueRepositoryレポジトリクラス */
	@Autowired
	private TimeRangeFeatureAllLeagueRepository timeRangeFeatureAllLeagueRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * シャットダウン
	 */
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
	 * enqueueCommonInsert で更新と登録に振り分け、結果を Map で返却する
	 * @param mapKey マップキー
	 * @param entity エンティティ
	 * @param tableName テーブル名
	 * @return
	 */
	public ConcurrentMap<String, Object> enqueueCommonReturn(String mapKey, BookDataEntity entity, String tableName) {
		ConcurrentMap<String, Object> result = new ConcurrentHashMap<>();
		try {
			Map<String, TeamRangeUpdateData> updateMap = new ConcurrentHashMap<>();
			Map<String, TeamRangeRegisterData> registerMap = new ConcurrentHashMap<>();
			TimeRangeFeatureOutputDTO dto = TimeRangeFeatureCommonUtil.splitTeamKey(mapKey);
			String country = dto.getCountry();
			String league = dto.getLeague();
			String time = entity.getTime();

			Field[] fields = entity.getClass().getDeclaredFields();
			Map<String, String> featureMap = Arrays.stream(fields).parallel()
					.filter(f -> !ExecuteMainUtil.chkExclusive(f.getName()))
					.collect(Collectors.toMap(
							Field::getName,
							f -> {
								try {
									String value = (String) f.get(entity);
									if (ExecuteMainUtil.chkRonriSplit(f.getName())) {
										List<String> split = ExecuteMainUtil.splitGroup(value);
										return "[SPLIT]" + split.get(1) + "|" + split.get(2);
									} else {
										return value;
									}
								} catch (Exception e) {
									return null;
								}
							}));

			featureMap.entrySet().parallelStream().forEach(entry -> {
				String feature = entry.getKey();
				String value = entry.getValue();
				if (value == null)
					return;

				TimeRangeFeatureOutputDTO checkDto = tableName.endsWith("scored")
						? getScoredData(time, feature, value, country, league, tableName)
						: getAllLeagueData(time, feature, value, tableName);

				if (checkDto.isUpdFlg()) {
					String target = String.valueOf(Integer.parseInt(checkDto.getTarget()) + 1);
					String search = String.valueOf(Integer.parseInt(checkDto.getSearch()) + 1);
					updateMap.put(feature, new TeamRangeUpdateData(
							checkDto.getId(), target, search, tableName));
				} else {
					String countryKey = (tableName.endsWith("scored")) ? country : "";
					String leagueKey = (tableName.endsWith("scored")) ? league : "";
					String timeRange = convTimeRange(time);
					if (feature.startsWith("[SPLIT]")) {
						String[] split = feature.replace("[SPLIT]", "").split("\\|");
						String thresHoldSuccess = normalizeValue(split[0]);
						registerMap.put(feature + "_success", new TeamRangeRegisterData(
								countryKey, leagueKey, timeRange, feature + "_success", thresHoldSuccess,
								"1", "1", tableName));
						String thresHoldTry = normalizeValue(split[1]);
						registerMap.put(feature + "_try", new TeamRangeRegisterData(
								countryKey, leagueKey, timeRange, feature + "_try", thresHoldTry,
								"1", "1", tableName));
					} else {
						String thresHold = normalizeValue(value);
						registerMap.put(feature, new TeamRangeRegisterData(countryKey, leagueKey,
								timeRange, feature, thresHold, "1", "1", tableName));
					}
				}
			});
			result.put(TimeRangeFeatureCommonUtil.UPDATEMAP, updateMap);
			result.put(TimeRangeFeatureCommonUtil.REGISTERMAP, registerMap);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME,
					"enqueueCommonReturn", "非同期判定失敗", e, mapKey);
		}
		return result;
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
		List<TimeRangeFeatureScoredEntity> found = this.timeRangeFeatureScoredRepository
				.findData(country, league, timeRange, feature, thresHold, tableName);
		if (!found.isEmpty()) {
			dto.setUpdFlg(true);
			dto.setId(found.get(0).getId());
			dto.setTarget(found.get(0).getTarget());
			dto.setSearch(found.get(0).getSearch());
		} else {
			dto.setUpdFlg(false);
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
		List<TimeRangeFeatureAllLeagueEntity> found = this.timeRangeFeatureAllLeagueRepository
				.findData(timeRange, feature, thresHold, tableName);
		if (!found.isEmpty()) {
			dto.setUpdFlg(true);
			dto.setId(found.get(0).getId());
			dto.setTarget(found.get(0).getTarget());
			dto.setSearch(found.get(0).getSearch());
		} else {
			dto.setUpdFlg(false);
		}
		return dto;
	}

	/**
	 * 時間範囲に変換する
	 * @param time 時間
	 * @return
	 */
	private String convTimeRange(String time) {
		return ExecuteMainUtil.classifyMatchTime(time);
	}

	/**
	 * パーセンテージ文字列を10の位に切り捨てた形式（例: "23%" → "20%"）に変換
	 * @param value 元の文字列
	 * @return 変換後の文字列（失敗時は元のまま）
	 */
	private String normalizeValue(String value) {
		if (value != null && value.contains("%")) {
			try {
				int raw = Integer.parseInt(value.replace("%", ""));
				int rounded = (raw / 10) * 10;
				return rounded + "%";
			} catch (NumberFormatException e) {
				// 変換失敗時はそのまま
			}
		}
		return value;
	}

	@Scheduled(fixedRate = 5000) // 5秒ごと
	public void monitorQueueStatus() {
		final String METHOD_NAME = "monitorQueueStatus";
		String messageCd = "キューモニター監視";
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				"[QueueMonitor] Queue={" + getQueueSize() + "}"
						+ ", Pool={" + getPoolSize() + "}"
						+ ", TotalTask={" + getTotalTaskCount() + "}"
						+ ", ActiveTask={" + getActiveTaskCount() + "}"
						+ ", CompletedTask={" + getCompletedTaskCount() + "}");
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
