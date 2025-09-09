package dev.application.analyze.bm_m007_bm_m016;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.TimeRangeFeatureAllLeagueRepository;
import dev.application.domain.repository.TimeRangeFeatureRepository;
import dev.application.domain.repository.TimeRangeFeatureScoredRepository;
import dev.application.domain.repository.TimeRangeFeatureUpdateRepository;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M007〜BM_M016統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class TimeRangeFeatureStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TimeRangeFeatureStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TimeRangeFeatureStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M007_BM_M016_WITH_IN";

	/** TimeRangeFeatureQueueService非同期キュークラス */
	@Autowired
	private TimeRangeFeatureQueueService queueService;

	/** TimeRangeFeatureRepositoryレポジトリクラス */
	@Autowired
	private TimeRangeFeatureRepository timeRangeFeatureRepository;

	/** TimeRangeFeatureScoredRepositoryレポジトリクラス */
	@Autowired
	private TimeRangeFeatureScoredRepository timeRangeFeatureScoredRepository;

	/** TimeRangeFeatureAllLeagueRepositoryレポジトリクラス */
	@Autowired
	private TimeRangeFeatureAllLeagueRepository timeRangeFeatureAllLeagueRepository;

	/** TimeRangeFeatureUpdateRepositoryレポジトリクラス */
	@Autowired
	private TimeRangeFeatureUpdateRepository timeRangeFeatureUpdateRepository;

	/** BookDataToTimeRangeFeatureMapperクラス */
	@Autowired
	private BookDataToTimeRangeFeatureMapper bookDataToTimeRangeFeatureMapper;

	/** RegisterDataToTimeRangeFeatureAllLeagueMapperクラス */
	@Autowired
	private RegisterDataToTimeRangeFeatureAllLeagueMapper registerDataToTimeRangeFeatureAllLeagueMapper;

	/** RegisterDataToTimeRangeFeatureScoredMapperクラス */
	@Autowired
	private RegisterDataToTimeRangeFeatureScoredMapper registerDataToTimeRangeFeatureScoredMapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 各条件に応じて登録・更新するテーブルを分ける
		Map<Integer, Map<String, List<BookDataEntity>>> filtered = new ConcurrentHashMap<>();
		entities.entrySet().parallelStream().forEach(entry -> {
			String leagueKey = entry.getKey(); // 例: "Japan-J1"
			String[] category = ExecuteMainUtil.splitLeagueInfo(leagueKey);
			String country = category[0];
			String league = category[1];
			Map<String, List<BookDataEntity>> matchMap = entry.getValue();
			for (Map.Entry<String, List<BookDataEntity>> matchEntry : matchMap.entrySet()) {
				String matchKey = matchEntry.getKey(); // 例: "Urawa-Kashima"
				List<BookDataEntity> dataList = matchEntry.getValue();
				// ホームチーム名はキーから取り出す（例: "Urawa"）
				String[] teams = matchKey.split("-");
				String homeTeam = teams[0];
				String awayTeam = teams[1];

				String prevHomeScore = "0";
				String prevAwayScore = "0";
				for (BookDataEntity e : dataList) {
					// ゴール取り消しはスキップ
					if (BookMakersCommonConst.GOAL_DELETE.equals(e.getJudge()))
						continue;

					double convTime = ExecuteMainUtil.convertToMinutes(e.getTime());
					String currentHomeScore = e.getHomeScore();
					String currentAwayScore = e.getAwayScore();
					if (convTime <= 20 && currentHomeScore.compareTo(prevHomeScore) > 0 &&
							"2".equals(currentHomeScore)) {
						// ホームの得点が増えた＝得点タイミング
						setMap(filtered, country, league, homeTeam + "-2", e, 1);
						setMap(filtered, country, league, homeTeam + "-3", e, 1);
						setMap(filtered, country, league, homeTeam, e, 2);
						setMap(filtered, country, league, homeTeam, e, 3);
					} else if (convTime <= 20 && currentAwayScore.compareTo(prevAwayScore) > 0 &&
							"2".equals(currentAwayScore)) {
						// アウェーの得点が増えた＝得点タイミング
						setMap(filtered, country, league, awayTeam + "-4", e, 1);
						setMap(filtered, country, league, awayTeam + "-5", e, 1);
						setMap(filtered, country, league, awayTeam, e, 4);
						setMap(filtered, country, league, awayTeam, e, 5);
					} else if (convTime <= 20 && currentHomeScore.compareTo(currentAwayScore) == 0 &&
							"1".equals(currentHomeScore) && "1".equals(currentAwayScore)) {
						// 同一得点
						setMap(filtered, country, league, matchKey + "-6", e, 1);
						setMap(filtered, country, league, matchKey, e, 6);
					} else if (convTime <= 45 && currentHomeScore.compareTo(prevHomeScore) > 0 &&
							"1".equals(currentHomeScore)) {
						// ホームの得点が増えた＝得点タイミング
						setMap(filtered, country, league, homeTeam + "-7", e, 1);
						setMap(filtered, country, league, homeTeam + "-8", e, 1);
						setMap(filtered, country, league, homeTeam, e, 7);
						setMap(filtered, country, league, homeTeam, e, 8);
					} else if (convTime <= 45 && currentAwayScore.compareTo(prevAwayScore) > 0 &&
							"1".equals(currentAwayScore)) {
						// アウェーの得点が増えた＝得点タイミング
						setMap(filtered, country, league, awayTeam + "-9", e, 1);
						setMap(filtered, country, league, awayTeam + "-10", e, 1);
						setMap(filtered, country, league, awayTeam, e, 9);
						setMap(filtered, country, league, awayTeam, e, 10);
					}
					prevHomeScore = currentHomeScore;
					prevAwayScore = currentAwayScore;
				}
			}
		});

		// --- 1) メイン（seq=1 → within_data）を先に直列で ---
		Map<String, List<BookDataEntity>> mainMap = filtered.get(1);
		if (mainMap != null && !mainMap.isEmpty()) {
			mainMap.forEach((k, list) -> mainInsert(k, list.get(0)));
		}

		// --- 2) サブ（seq != 1）を順序固定で直列処理（7→8→…） ---
		filtered.entrySet().stream()
				.filter(e -> e.getKey() != 1)
				.sorted(Map.Entry.comparingByKey())
				.forEach(e -> {
					final int subSeq = e.getKey(); // 外側と衝突しない別名
					final String subTableName = TimeRangeFeatureCommonUtil.getTableMap(subSeq);

					e.getValue().forEach((k, list) -> {
						BookDataEntity entity = list.get(0);
						ConcurrentMap<String, Object> result = queueService.enqueueCommonReturn(k, entity,
								subTableName);
						if (result == null)
							return;

						Map<String, TeamRangeUpdateData> updateMap = (Map<String, TeamRangeUpdateData>) result
								.get(TimeRangeFeatureCommonUtil.UPDATEMAP);
						if (updateMap != null && !updateMap.isEmpty()) {
							updateMap.values().forEach(this::commonUpdate);
						}

						Map<String, TeamRangeRegisterData> registerMap = (Map<String, TeamRangeRegisterData>) result
								.get(TimeRangeFeatureCommonUtil.REGISTERMAP);
						if (registerMap != null && !registerMap.isEmpty()) {
							registerMap.values().forEach(v -> commonInsert(v, subTableName));
						}
					});
				});

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * Mainテーブル登録キュー
	 * @param mapKey
	 * @param entity
	 */
	public synchronized void mainInsert(String mapKey, BookDataEntity entity) {
		final String METHOD_NAME = "mainInsert";
		// 末尾連番を安全に取得（なければ即エラーにして早期検知）
		int seq1Int = TimeRangeFeatureCommonUtil.extractTrailingSeq(mapKey)
				.orElseThrow(() -> new IllegalArgumentException("mapKey 末尾に連番がありません: " + mapKey));
		String seq1 = Integer.toString(seq1Int);

		// 国/リーグ/チーム等の抽出は従来ロジックでOK（seq1 は上で確定）
		TimeRangeFeatureOutputDTO dto = TimeRangeFeatureCommonUtil.splitTeamKey(mapKey);
		String country = dto.getCountry();
		String league = dto.getLeague();
		String home = dto.getHome();
		String away = dto.getAway();

		String tableId = "登録テーブルID: (" + TimeRangeFeatureCommonUtil.getTableMap(seq1Int) + ")";
		String loggers = setLogger(country, league, home, away) + ", " + tableId;

		TimeRangeFeatureEntity timeRangeFeatureEntity = this.bookDataToTimeRangeFeatureMapper.mapStruct(entity, seq1);

		int result = this.timeRangeFeatureRepository.insert(timeRangeFeatureEntity);
		if (result != 1)
			logAndThrow("新規登録エラー", METHOD_NAME, loggers);

		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, "登録件数", tableId);
	}

	/**
	 * 登録メソッド
	 * @param TeamRangeRegisterData 登録データ
	 * @param table テーブル
	 */
	private synchronized void commonInsert(TeamRangeRegisterData registerData, String table) {
		final String methodName = "commonInsert";
		String loggers = setLogger(
				registerData.getCountry(),
				registerData.getLeague(),
				"",
				"");
		if (table.endsWith("scored")) {
			TimeRangeFeatureScoredEntity entity = this.registerDataToTimeRangeFeatureScoredMapper
					.mapStruct(registerData);
			if (this.timeRangeFeatureScoredRepository.insert(entity) != 1) {
				logAndThrow("新規登録エラー", methodName, loggers);
			}
		} else if (table.endsWith("all_league")) {
			TimeRangeFeatureAllLeagueEntity entity = this.registerDataToTimeRangeFeatureAllLeagueMapper
					.mapStruct(registerData);
			if (this.timeRangeFeatureAllLeagueRepository.insert(entity) != 1) {
				logAndThrow("新規登録エラー", methodName, loggers);
			}
		}
		String messageCd = "登録件数";
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, methodName, messageCd, table);
	}

	/**
	 * 更新メソッド
	 * @param updateData 更新データ
	 */
	private synchronized void commonUpdate(TeamRangeUpdateData updateData) {
		final String methodName = "commonUpdate";
		if (this.timeRangeFeatureUpdateRepository.update(
				updateData.getId(),
				updateData.getTarget(),
				updateData.getSearch(),
				updateData.getTable()) != 1) {
			logAndThrow("更新エラー", methodName, "");
		}
		String messageCd = "更新件数";
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, methodName, messageCd, updateData.getTable());
	}

	/**
	 * 同一キーがあれば追加
	 * @param resultMap 結果マップ
	 * @param country 国
	 * @param league リーグ
	 * @param e エンティティ
	 * @param keyCounter 連番キー(<link>TimeRangeFeatureCommonUtil#TABLE_MAPのキー</link>)
	 * @return
	 */
	private void setMap(
			Map<Integer, Map<String, List<BookDataEntity>>> resultMap,
			String country,
			String league,
			String matchKey,
			BookDataEntity e,
			int keyCounter) {

		String mapKey = country + "-" + league + "-" + matchKey;
		// keyCounterに対する内側Mapを取得 or 作成
		resultMap.computeIfAbsent(keyCounter, k -> new ConcurrentHashMap<>())
				// 特定キーが存在しなければ新規追加（1件のみ）
				.computeIfAbsent(mapKey, k -> {
					List<BookDataEntity> list = new ArrayList<>();
					list.add(e);
					return list;
				});
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
	 * 例外共通メソッド
	 * @param messageCd メッセージコード
	 * @param methodName メソッド名
	 * @param loggers ログ
	 */
	private void logAndThrow(String messageCd, String methodName, String loggers) {
		this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, methodName, messageCd, null, loggers);
		this.manageLoggerComponent.createSystemException(PROJECT_NAME, CLASS_NAME, methodName, messageCd, null);
	}

}
