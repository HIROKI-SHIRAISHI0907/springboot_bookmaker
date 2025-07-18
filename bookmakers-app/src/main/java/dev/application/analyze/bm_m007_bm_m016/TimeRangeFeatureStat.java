package dev.application.analyze.bm_m007_bm_m016;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.CountryLeagueSummaryRepository;
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

	/** CountryLeagueSummaryRepositoryレポジトリクラス */
	@Autowired
	private CountryLeagueSummaryRepository countryLeagueSummaryRepository;

	/** BookDataToTimeRangeFeatureMapperクラス */
	@Autowired
	private BookDataToTimeRangeFeatureMapper bookDataToTimeRangeFeatureMapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/** テーブル情報Map */
	private static Map<Integer, String> TABLE_MAP;
	{
		Map<Integer, String> tableMap = new LinkedHashMap<Integer, String>();
		tableMap.put(1, "within_data");
		tableMap.put(2, "within_data_20minutes_home_all_league");
		tableMap.put(3, "within_data_20minutes_home_scored");
		tableMap.put(4, "within_data_20minutes_away_all_league");
		tableMap.put(5, "within_data_20minutes_away_scored");
		tableMap.put(6, "within_data_20minutes_same_scored");
		tableMap.put(7, "within_data_45minutes_home_all_league");
		tableMap.put(8, "within_data_45minutes_home_scored");
		tableMap.put(9, "within_data_45minutes_away_all_league");
		tableMap.put(10, "within_data_45minutes_away_scored");
		TABLE_MAP = Collections.unmodifiableMap(tableMap);
	}

	/**
	 * {@inheritDoc}
	 */
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
			String[] category = leagueKey.split("-");
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
						setMap(filtered, country, league, matchKey, e, 1);
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

		if (!filtered.isEmpty()) {
			for (Map.Entry<Integer, Map<String, List<BookDataEntity>>> matchEntry : filtered.entrySet()) {
				// indexが1の場合,teamKeyの後部に通番情報が保持されている想定
				int tableSeq = matchEntry.getKey();
				String tableName = getTableMap(tableSeq);
				Map<String, List<BookDataEntity>> entityMap = matchEntry.getValue();
				for (Map.Entry<String, List<BookDataEntity>> matchSubEntry : entityMap.entrySet()) {
					String teamKey = matchSubEntry.getKey();
					BookDataEntity entity = matchSubEntry.getValue().get(0);
				}
			}
		} else {
			String messageCd = "登録しない";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd);
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * 同一キーがあれば追加
	 * @param resultMap 結果マップ
	 * @param country 国
	 * @param league リーグ
	 * @param e エンティティ
	 * @param keyCounter 連番キー
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
	 * within_data 登録メソッド
	 * @param mapKey
	 * @param entity
	 */
	private synchronized void setMainEntity(String mapKey, BookDataEntity entity) {
		TimeRangeFeatureOutputDTO dto = splitTeamKey(mapKey);
		String seq1 = dto.getSeq1(); // 連番ID
		TimeRangeFeatureEntity timeRangeFeatureEntity =
				this.bookDataToTimeRangeFeatureMapper.mapStruct(entity, seq1);

	}

	/**
	 * within_data_scored 登録メソッド
	 * @param mapKey
	 * @param entity
	 */
	private synchronized void setScoredEntity(String mapKey, BookDataEntity entity) {
		TimeRangeFeatureOutputDTO dto = splitTeamKey(mapKey);
		String country = dto.getCountry();
		String league = dto.getLeague();
		String home = dto.getHome();
		String away = dto.getAway();
		String seq1 = dto.getSeq1();

	}

	/**
	 * within_data_all_league 登録メソッド
	 * @param mapKey
	 * @param entity
	 */
	private synchronized void setAllLeagueEntity(String mapKey, BookDataEntity entity) {
		TimeRangeFeatureOutputDTO dto = splitTeamKey(mapKey);
		String country = dto.getCountry();
		String league = dto.getLeague();
		String home = dto.getHome();
		String away = dto.getAway();
		String seq1 = dto.getSeq1();

	}

	/**
	 * テーブル情報マップを取得
	 * @param key キー(国-リーグ-ホーム-アウェー-連番)
	 * @return テーブル情報のマップ
	 */
	private TimeRangeFeatureOutputDTO splitTeamKey(String mapKey) {
		TimeRangeFeatureOutputDTO timeRangeFeatureOutputDTO = new TimeRangeFeatureOutputDTO();
		String[] key = mapKey.split("-");
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
	 * テーブル情報マップを取得
	 * @param key キー
	 * @return テーブル情報のマップ
	 */
	private String getTableMap(int key) {
		final String METHOD_NAME = "getTableMap";

		String messageCd = "";
		if (!TABLE_MAP.containsKey(key)) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
			this.manageLoggerComponent.createSystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null);
		}
		return TABLE_MAP.get(key);
	}

}
