package dev.application.analyze.bm_m019_bm_m020;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.MatchClassificationResultCountRepository;
import dev.application.domain.repository.MatchClassificationResultRepository;
import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.BookDataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M019_BM_M020統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
public class MatchClassificationResultStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MatchClassificationResultStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = MatchClassificationResultStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M019_BM_M020_MATCH_CLASSIFICATION_RESULT";

	/** BookDataToMatchClassificationResultMapperマッパークラス */
	@Autowired
	private BookDataToMatchClassificationResultMapper bookDataToMatchClassificationResultMapper;

	/** MatchClassificationResultRepositoryレポジトリクラス */
	@Autowired
	private MatchClassificationResultRepository matchClassificationResultRepository;

	/** MatchClassificationResultCountRepositoryレポジトリクラス */
	@Autowired
	private MatchClassificationResultCountRepository matchClassificationResultCountRepository;

	/** ログ管理ラッパー*/
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * DB項目,テーブル名Mapping
	 */
	public static final Map<Integer, String> SCORE_CLASSIFICATION_ALL_MAP;
	static {
		HashMap<Integer, String> SCORE_CLASSIFICATION_MAP = new LinkedHashMap<>();
		SCORE_CLASSIFICATION_MAP.put(1, ClassifyScoreAIConst.HOME_SCORED_WITHIN_20_NEXT_SCORE_BEFORE_HALF);
		SCORE_CLASSIFICATION_MAP.put(2, ClassifyScoreAIConst.HOME_SCORED_WITHIN_20_NEXT_SCORE_AFTER_HALF);
		SCORE_CLASSIFICATION_MAP.put(3, ClassifyScoreAIConst.HOME_SCORED_WITHIN_20_NO_FURTHER_GOAL);
		SCORE_CLASSIFICATION_MAP.put(4, ClassifyScoreAIConst.AWAY_SCORED_WITHIN_20_NEXT_SCORE_BEFORE_HALF);
		SCORE_CLASSIFICATION_MAP.put(5, ClassifyScoreAIConst.AWAY_SCORED_WITHIN_20_NEXT_SCORE_AFTER_HALF);
		SCORE_CLASSIFICATION_MAP.put(6, ClassifyScoreAIConst.AWAY_SCORED_WITHIN_20_NO_FURTHER_GOAL);
		SCORE_CLASSIFICATION_MAP.put(7, ClassifyScoreAIConst.HOME_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_BEFORE_HALF);
		SCORE_CLASSIFICATION_MAP.put(8, ClassifyScoreAIConst.HOME_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_AFTER_HALF);
		SCORE_CLASSIFICATION_MAP.put(9, ClassifyScoreAIConst.HOME_SCORED_BETWEEN_20_AND_45_NO_FURTHER_GOAL);
		SCORE_CLASSIFICATION_MAP.put(10, ClassifyScoreAIConst.AWAY_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_BEFORE_HALF);
		SCORE_CLASSIFICATION_MAP.put(11,
				ClassifyScoreAIConst.AWAY_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_AFTER_HALF);
		SCORE_CLASSIFICATION_MAP.put(12,
				ClassifyScoreAIConst.AWAY_SCORED_BETWEEN_20_AND_45_NO_FURTHER_GOAL);
		SCORE_CLASSIFICATION_MAP.put(13, ClassifyScoreAIConst.NO_GOAL_FIRST_HALF_NEXT_HOME_SCORE);
		SCORE_CLASSIFICATION_MAP.put(14, ClassifyScoreAIConst.NO_GOAL_FIRST_HALF_NEXT_AWAY_SCORE);
		SCORE_CLASSIFICATION_MAP.put(15, ClassifyScoreAIConst.NO_GOAL);
		SCORE_CLASSIFICATION_MAP.put(-1, ClassifyScoreAIConst.EXCEPT_FOR_CONDITION);
		SCORE_CLASSIFICATION_ALL_MAP = Collections.unmodifiableMap(SCORE_CLASSIFICATION_MAP);
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

		// 初期データ登録
		for (Map.Entry<String, Map<String, List<BookDataEntity>>> entiEntry : entities.entrySet()) {
			String leagueKey = entiEntry.getKey();
			String[] sp = ExecuteMainUtil.splitLeagueInfo(leagueKey);
			String country = sp[0];
			String league = sp[1];
			init(country, league);
		}

		Map<String, List<String>> mainMap = new ConcurrentHashMap<>();
		entities.entrySet().parallelStream().forEach(entry -> {
			String leagueKey = entry.getKey(); // 例: "Japan-J1"
			String[] sp = ExecuteMainUtil.splitLeagueInfo(leagueKey);
			String country = sp[0];
			String league = sp[1];
			Map<String, List<BookDataEntity>> matchMap = entry.getValue();
			for (List<BookDataEntity> dataList : matchMap.values()) {
				MatchClassificationResultOutputDTO dto = classification(dataList);
				if (dto == null) {
					continue;
				}
				List<MatchClassificationResultEntity> insertEntities = dto.getEntityList();
				// BM_M019登録
				saveResultData(insertEntities, country, league);
				String classificationMode = dto.getClassificationMode();
				mainMap.computeIfAbsent(leagueKey, k -> new ArrayList<>())
						.add(classificationMode);
			}
		});

		// データ更新
		mainMap.entrySet().stream().forEach(entry -> {
			String leagueKey = entry.getKey(); // 例: "Japan-J1"
			String[] sp = ExecuteMainUtil.splitLeagueInfo(leagueKey);
			String country = sp[0];
			String league = sp[1];
			List<String> classificationModeList = entry.getValue();
			// 内側だけ並列で処理
			classificationModeList.parallelStream()
					.filter(Objects::nonNull) // ← null を除外
					.map(String::trim) // ← 前後空白を除く（" 13" 対策）
					.filter(s -> !s.isEmpty()) // ← 空文字は捨てる
					.forEach(classificationMode -> {
						MatchClassificationResultOutputDTO dto = getData(country, league, classificationMode);
						// 基本は全て更新の想定
						String id = dto.getId();
						String cnt = String.valueOf(Integer.parseInt(dto.getCnt()) + 1);
						saveCntData(id, country, league, classificationMode, cnt);
					});
		});

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * 件数初期登録
	 * @param country 国
	 * @param league リーグ
	 */
	private synchronized void init(String country, String league) {
		final String METHOD_NAME = "init";
		String fillChar = setLoggerFillChar(country, league);
		for (int classify = 1; classify <= SCORE_CLASSIFICATION_ALL_MAP.size()
				- 1; classify++) {
			MatchClassificationResultCountEntity classifyResultDataDetailEntity = new MatchClassificationResultCountEntity();
			classifyResultDataDetailEntity.setCountry(country);
			classifyResultDataDetailEntity.setLeague(league);
			classifyResultDataDetailEntity.setCount("0");
			classifyResultDataDetailEntity.setClassifyMode(String.valueOf(classify));
			classifyResultDataDetailEntity.setRemarks(getRemarks(classify));
			List<MatchClassificationResultCountEntity> data = this.matchClassificationResultCountRepository
					.findData(country, league, String.valueOf(classify));
			if (!data.isEmpty()) {
				continue;
			}

			int result = this.matchClassificationResultCountRepository.insert(classifyResultDataDetailEntity);
			if (result != 1) {
				String messageCd = "新規登録エラー";
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						String.format("classifymode=%s", classify));
			}
		}
		MatchClassificationResultCountEntity classifyResultDataDetailEntity = new MatchClassificationResultCountEntity();
		classifyResultDataDetailEntity.setCountry(country);
		classifyResultDataDetailEntity.setLeague(league);
		classifyResultDataDetailEntity.setCount("0");
		classifyResultDataDetailEntity.setClassifyMode("-1");
		classifyResultDataDetailEntity.setRemarks(getRemarks(-1));
		List<MatchClassificationResultCountEntity> data = this.matchClassificationResultCountRepository
				.findData(country, league, "-1");
		if (data.isEmpty()) {
			int result = this.matchClassificationResultCountRepository.insert(classifyResultDataDetailEntity);
			if (result != 1) {
				String messageCd = "新規登録エラー";
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						"classifymode=-1");
			}
			String messageCd = "登録件数";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "BM_M020 登録件数: "
							+ SCORE_CLASSIFICATION_ALL_MAP.size() + "件");
		}
	}

	/**
	 * 取得データ
	 * @param country 国
	 * @param league リーグ
	 * @param classifyMode 分類モード
	 * @return MatchClassificationResultOutputDTO
	 */
	private MatchClassificationResultOutputDTO getData(String country, String league,
			String classifyMode) {
		MatchClassificationResultOutputDTO matchClassificationResultOutputDTO = new MatchClassificationResultOutputDTO();
		List<MatchClassificationResultCountEntity> datas = this.matchClassificationResultCountRepository
				.findData(country, league, classifyMode);
		if (!datas.isEmpty()) {
			matchClassificationResultOutputDTO.setUpdFlg(true);
			matchClassificationResultOutputDTO.setId(datas.get(0).getId());
			matchClassificationResultOutputDTO.setCnt(datas.get(0).getCount());
		} else {
			matchClassificationResultOutputDTO.setUpdFlg(false);
			matchClassificationResultOutputDTO.setCnt("0");
		}
		return matchClassificationResultOutputDTO;
	}

	/**
	 * 更新登録
	 * @param id ID
	 * @param country 国
	 * @param league リーグ
	 * @param classify_mode 分類
	 * @param count 件数
	 */
	private synchronized void saveCntData(String id, String country, String league, String classify_mode, String count) {
		final String METHOD_NAME = "saveCntData";
		String fillChar = setLoggerFillChar(country, league);
		int result = this.matchClassificationResultCountRepository.update(id, count);
		if (result != 1) {
			String messageCd = "更新エラー";
			this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					messageCd,
					1, result,
					String.format("id=%s, classify_mode=%s count=%s", id, classify_mode, count));
		}
		String messageCd = "更新件数";
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "BM_M020 更新件数: 1件");
	}

	/**
	 * 分類モード付きデータ初期登録
	 * @param entities エンティティ
	 */
	private synchronized void saveResultData(List<MatchClassificationResultEntity> entities,
			String country, String league) {
		final String METHOD_NAME = "saveResultData";
		String fillChar = setLoggerFillChar(country, league);
		for (MatchClassificationResultEntity entity : entities) {
			int result = this.matchClassificationResultRepository.insert(entity);
			if (result != 1) {
				String messageCd = "新規登録エラー";
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						null);
			}
		}
		String messageCd = "登録件数";
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "BM_M019 登録件数: "
						+ entities.size() + "件");
	}

	/**
	 * 分類モード設定
	 * @param entityList
	 * @return
	 */
	private MatchClassificationResultOutputDTO classification(List<BookDataEntity> entityList) {
		MatchClassificationResultOutputDTO matchClassificationResultOutputDTO = new MatchClassificationResultOutputDTO();

		Set<Integer> classifyModeConditionList = new HashSet<>();
		List<MatchClassificationResultEntity> insertEntities = new ArrayList<MatchClassificationResultEntity>();
		BookDataEntity returnMaxEntity = ExecuteMainUtil.getMaxSeqEntities(entityList);
		if (!BookMakersCommonConst.FIN.equals(returnMaxEntity.getTime())) {
			return null;
		}
		int maxHomeScore = Integer.parseInt(returnMaxEntity.getHomeScore());
		int maxAwayScore = Integer.parseInt(returnMaxEntity.getAwayScore());

		// 最終スコアが無得点, 1-0, 0-1
		if (maxHomeScore == 0 && maxAwayScore == 0) {
			classifyModeConditionList.add(-1);
		}
		if (maxHomeScore == 1 && maxAwayScore == 0) {
			classifyModeConditionList.add(-2);
		}
		if (maxHomeScore == 0 && maxAwayScore == 1) {
			classifyModeConditionList.add(-3);
		}

		int classify_mode = -1;
		List<String> scoreList = new ArrayList<String>();
		for (BookDataEntity entity : entityList) {
			// ゴール取り消しはスキップ
			if (BookMakersCommonConst.GOAL_DELETE.equals(entity.getJudge())) {
				continue;
			}

			int home_score = Integer.parseInt(entity.getHomeScore());
			int away_score = Integer.parseInt(entity.getAwayScore());
			String game_time = entity.getTime();
			double convert_game_time = ExecuteMainUtil.convertToMinutes(game_time);

			if ((int) convert_game_time <= 20 &&
					(home_score == 1 && away_score == 0)) {
				classifyModeConditionList.add(1);
			}
			if ((int) convert_game_time <= 45 &&
					(home_score == 2 && away_score == 0)) {
				classifyModeConditionList.add(2);
			}
			if ((int) convert_game_time > 45 &&
					(home_score == 2 && away_score == 0)) {
				classifyModeConditionList.add(3);
			}
			if ((int) convert_game_time <= 45 &&
					(home_score == 1 && away_score == 1)) {
				classifyModeConditionList.add(4);
			}
			if ((int) convert_game_time <= 20 &&
					(home_score == 0 && away_score == 1)) {
				classifyModeConditionList.add(5);
			}
			if ((int) convert_game_time <= 45 &&
					(home_score == 0 && away_score == 2)) {
				classifyModeConditionList.add(6);
			}
			if ((int) convert_game_time > 45 &&
					(home_score == 0 && away_score == 2)) {
				classifyModeConditionList.add(7);
			}
			if ((int) convert_game_time > 45 &&
					(home_score == 1 && away_score == 1)) {
				classifyModeConditionList.add(8);
			}
			if ((int) convert_game_time > 20 && (int) convert_game_time <= 45 &&
					(home_score == 1 && away_score == 0)) {
				classifyModeConditionList.add(9);
			}
			if ((int) convert_game_time > 20 && (int) convert_game_time <= 45 &&
					(home_score == 0 && away_score == 1)) {
				classifyModeConditionList.add(10);
			}
			if ((BookMakersCommonConst.FIRST_HALF_TIME.equals(entity.getTime()) ||
					BookMakersCommonConst.HALF_TIME.equals(entity.getTime())) &&
					(home_score == 0 && away_score == 0)) {
				classifyModeConditionList.add(11);
			}
			if ((int) convert_game_time > 45 &&
					(home_score == 1 && away_score == 0)) {
				classifyModeConditionList.add(12);
			}
			if ((int) convert_game_time > 45 &&
					(home_score == 0 && away_score == 1)) {
				classifyModeConditionList.add(13);
			}

			// [1]. 20分以内に得点が入った場合
			if (classifyModeConditionList.contains(1) || classifyModeConditionList.contains(5)) {

				// 1. ホームチームが得点後、次の得点が前半に入る
				if (classifyModeConditionList.contains(1) &&
						(classifyModeConditionList.contains(2) ||
								classifyModeConditionList.contains(4))) {
					classify_mode = 1;
				}
				// 2. ホームチームが得点後、次の得点が後半に入る
				if (classifyModeConditionList.contains(1) &&
						(classifyModeConditionList.contains(3) ||
								classifyModeConditionList.contains(8))) {
					classify_mode = 2;
				}
				// 3. ホームチームが得点後、得点が入らない
				if (classifyModeConditionList.contains(1) &&
						classifyModeConditionList.contains(-2)) {
					classify_mode = 3;
				}
				// 4. アウェーチームが得点後、次の得点が前半に入る
				if (classifyModeConditionList.contains(5) &&
						(classifyModeConditionList.contains(6) ||
								classifyModeConditionList.contains(4))) {
					classify_mode = 4;
				}
				// 5. アウェーチームが得点後、次の得点が後半に入る
				if (classifyModeConditionList.contains(5) &&
						(classifyModeConditionList.contains(7) ||
								classifyModeConditionList.contains(8))) {
					classify_mode = 5;
				}
				// 6. アウェーチームが得点後、得点が入らない
				if (classifyModeConditionList.contains(5) &&
						classifyModeConditionList.contains(-3)) {
					classify_mode = 6;
				}

				// [2]. 20分〜前半に得点が入った場合
			} else if (classifyModeConditionList.contains(9) || classifyModeConditionList.contains(10)) {

				// 7. ホームチームが得点後、次の得点が前半に入る
				if (classifyModeConditionList.contains(9) &&
						(classifyModeConditionList.contains(2) ||
								classifyModeConditionList.contains(4))) {
					classify_mode = 7;
				}
				// 8. ホームチームが得点後、次の得点が後半に入る
				if (classifyModeConditionList.contains(9) &&
						(classifyModeConditionList.contains(3) ||
								classifyModeConditionList.contains(8))) {
					classify_mode = 8;
				}
				// 9. ホームチームが得点後、得点が入らない
				if (classifyModeConditionList.contains(9) &&
						classifyModeConditionList.contains(-2)) {
					classify_mode = 9;
				}
				// 10. アウェーチームが得点後、次の得点が前半に入る
				if (classifyModeConditionList.contains(10) &&
						(classifyModeConditionList.contains(6) ||
								classifyModeConditionList.contains(4))) {
					classify_mode = 10;
				}
				// 11. アウェーチームが得点後、次の得点が後半に入る
				if (classifyModeConditionList.contains(10) &&
						(classifyModeConditionList.contains(7) ||
								classifyModeConditionList.contains(8))) {
					classify_mode = 11;
				}
				// 12. アウェーチームが得点後、得点が入らない
				if (classifyModeConditionList.contains(10) &&
						classifyModeConditionList.contains(-3)) {
					classify_mode = 12;
				}
				// [3]. 前半で無得点だった場合
			} else if (classifyModeConditionList.contains(11)) {

				// 13. 前半で無得点後、後半にホーム側の得点が入る
				if (classifyModeConditionList.contains(11) &&
						(classifyModeConditionList.contains(12) ||
								classifyModeConditionList.contains(8))) {
					classify_mode = 13;
				}
				// 14. 前半で無得点後、後半にアウェー側の得点が入る
				if (classifyModeConditionList.contains(11) &&
						(classifyModeConditionList.contains(13) ||
								classifyModeConditionList.contains(8))) {
					classify_mode = 14;
				}
				// [4]. 無得点の場合
			} else if (classifyModeConditionList.contains(-1)) {
				// 15. 前半,後半も得点が入らない
				classify_mode = 15;
			}

			// ハーフタイム,スコアが変動するタイミングで登録する
			if (BookMakersCommonConst.HALF_TIME.equals(entity.getTime()) ||
					BookMakersCommonConst.FIRST_HALF_TIME.equals(entity.getTime())) {
				insertEntities.add(this.bookDataToMatchClassificationResultMapper.mapStruct(
						entity, String.valueOf(classify_mode)));
			} else {
				if (!scoreList.contains(entity.getHomeScore() + entity.getAwayScore())) {
					insertEntities.add(this.bookDataToMatchClassificationResultMapper.mapStruct(
							entity, String.valueOf(classify_mode)));
					scoreList.add(entity.getHomeScore() + entity.getAwayScore());
				}
			}
		}

		// 最新の分類モードを設定し直す(複数のclassify_modeがdtoに設定されるのを防ぐ)
		for (MatchClassificationResultEntity entity : insertEntities) {
			entity.setClassifyMode(String.valueOf(classify_mode));
		}

		matchClassificationResultOutputDTO.setClassificationMode(String.valueOf(classify_mode));
		matchClassificationResultOutputDTO.setEntityList(insertEntities);
		return matchClassificationResultOutputDTO;
	}

	/**
	 * 埋め字設定
	 * @param country
	 * @param league
	 * @return
	 */
	private String setLoggerFillChar(String country, String league) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("国: " + country + ", ");
		stringBuilder.append("リーグ: " + league);
		return stringBuilder.toString();
	}

	/**
	 * キーを取得
	 * @param key
	 * @return
	 */
	private String getRemarks(int key) {
		return SCORE_CLASSIFICATION_ALL_MAP.get(key);
	}
}
