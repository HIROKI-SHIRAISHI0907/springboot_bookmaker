package dev.application.analyze.bm_m019_bm_m020;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.application.analyze.common.entity.ThresHoldEntity;
import dev.application.analyze.common.util.ExecuteMainUtil;
import dev.common.constant.BookMakersCommonConst;
import dev.common.exception.BusinessException;


/**
 * 集計用サブロジック
 * @author shiraishitoshio
 *
 */
public class ClassifyScoreAISubLogic {

	// 分類モード
	private int classify_mode = -1;

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
	 * コンストラクタ
	 */
	public ClassifyScoreAISubLogic() {
		classify_mode = -1;
	}

	/**
	 * @param entityList CSV読み込みEntityリスト
	 * @param halfList ハーフリスト
	 * @throws Exception
	 */
	public void execute(List<ThresHoldEntity> entityList, String file)
			throws Exception {

		List<MatchClassificationResultEntity> insertEntities = new ArrayList<MatchClassificationResultEntity>();
		// 最大の通番を持つ時間を返却する
		ThresHoldEntity returnMaxEntity = ExecuteMainUtil.getMaxSeqEntities(entityList);
		int maxHomeScore = Integer.parseInt(returnMaxEntity.getHomeScore());
		int maxAwayScore = Integer.parseInt(returnMaxEntity.getAwayScore());

		Set<Integer> classifyModeConditionList = new HashSet<>();
		if (maxHomeScore == 0 && maxAwayScore == 0) {
			classifyModeConditionList.add(-1);
		}
		if (maxHomeScore == 1 && maxAwayScore == 0) {
			classifyModeConditionList.add(-2);
		}
		if (maxHomeScore == 0 && maxAwayScore == 1) {
			classifyModeConditionList.add(-3);
		}

		List<String> scoreList = new ArrayList<String>();
		for (ThresHoldEntity entity : entityList) {
			// ゴール取り消しはスキップ
			if (BookMakersCommonConst.GOAL_DELETE.equals(entity.getJudge())) {
				continue;
			}

			int home_score = Integer.parseInt(entity.getHomeScore());
			int away_score = Integer.parseInt(entity.getAwayScore());
			String game_time = entity.getTimes();
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
			if ((BookMakersCommonConst.FIRST_HALF_TIME.equals(entity.getTimes()) ||
					BookMakersCommonConst.HALF_TIME.equals(entity.getTimes())) &&
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
					this.classify_mode = 1;
				}
				// 2. ホームチームが得点後、次の得点が後半に入る
				if (classifyModeConditionList.contains(1) &&
						(classifyModeConditionList.contains(3) ||
								classifyModeConditionList.contains(8))) {
					this.classify_mode = 2;
				}
				// 3. ホームチームが得点後、得点が入らない
				if (classifyModeConditionList.contains(1) &&
						classifyModeConditionList.contains(-2)) {
					this.classify_mode = 3;
				}
				// 4. アウェーチームが得点後、次の得点が前半に入る
				if (classifyModeConditionList.contains(5) &&
						(classifyModeConditionList.contains(6) ||
								classifyModeConditionList.contains(4))) {
					this.classify_mode = 4;
				}
				// 5. アウェーチームが得点後、次の得点が後半に入る
				if (classifyModeConditionList.contains(5) &&
						(classifyModeConditionList.contains(7) ||
								classifyModeConditionList.contains(8))) {
					this.classify_mode = 5;
				}
				// 6. アウェーチームが得点後、得点が入らない
				if (classifyModeConditionList.contains(5) &&
						classifyModeConditionList.contains(-3)) {
					this.classify_mode = 6;
				}

				// [2]. 20分〜前半に得点が入った場合
			} else if (classifyModeConditionList.contains(9) || classifyModeConditionList.contains(10)) {

				// 7. ホームチームが得点後、次の得点が前半に入る
				if (classifyModeConditionList.contains(9) &&
						(classifyModeConditionList.contains(2) ||
								classifyModeConditionList.contains(4))) {
					this.classify_mode = 7;
				}
				// 8. ホームチームが得点後、次の得点が後半に入る
				if (classifyModeConditionList.contains(9) &&
						(classifyModeConditionList.contains(3) ||
								classifyModeConditionList.contains(8))) {
					this.classify_mode = 8;
				}
				// 9. ホームチームが得点後、得点が入らない
				if (classifyModeConditionList.contains(9) &&
						classifyModeConditionList.contains(-2)) {
					this.classify_mode = 9;
				}
				// 10. アウェーチームが得点後、次の得点が前半に入る
				if (classifyModeConditionList.contains(10) &&
						(classifyModeConditionList.contains(6) ||
								classifyModeConditionList.contains(4))) {
					this.classify_mode = 10;
				}
				// 11. アウェーチームが得点後、次の得点が後半に入る
				if (classifyModeConditionList.contains(10) &&
						(classifyModeConditionList.contains(7) ||
								classifyModeConditionList.contains(8))) {
					this.classify_mode = 11;
				}
				// 12. アウェーチームが得点後、得点が入らない
				if (classifyModeConditionList.contains(10) &&
						classifyModeConditionList.contains(-3)) {
					this.classify_mode = 12;
				}
				// [3]. 前半で無得点だった場合
			} else if (classifyModeConditionList.contains(11)) {

				// 13. 前半で無得点後、後半にホーム側の得点が入る
				if (classifyModeConditionList.contains(11) &&
						(classifyModeConditionList.contains(12) ||
								classifyModeConditionList.contains(8))) {
					this.classify_mode = 13;
				}
				// 14. 前半で無得点後、後半にアウェー側の得点が入る
				if (classifyModeConditionList.contains(11) &&
						(classifyModeConditionList.contains(13) ||
								classifyModeConditionList.contains(8))) {
					this.classify_mode = 14;
				}
				// [4]. 無得点の場合
			} else if (classifyModeConditionList.contains(-1)) {
				// 15. 前半,後半も得点が入らない
				this.classify_mode = 15;
			}

			System.out.println("途中経過: time: " + entity.getTimes() + ", score(home-away): "
					+ entity.getHomeScore() + "-" + entity.getAwayScore() + ", "
					+ "classify_mode: " + classifyModeConditionList);

			// ハーフタイム,スコアが変動するタイミングで登録する
			if (BookMakersCommonConst.HALF_TIME.equals(entity.getTimes()) ||
					BookMakersCommonConst.FIRST_HALF_TIME.equals(entity.getTimes())) {
				insertEntities.add(mappingEntity(String.valueOf(classify_mode), entity));
			} else {
				if (!scoreList.contains(entity.getHomeScore() + entity.getAwayScore())) {
					insertEntities.add(mappingEntity(String.valueOf(classify_mode), entity));
					scoreList.add(entity.getHomeScore() + entity.getAwayScore());
				}
			}
		}

		// file名のcsvを除いた数字をDBに登録(具体的にどのcsvがどの分類モードになったかを調べるため)
		String[] fileList = file.split("/");
		String file_name = fileList[fileList.length - 1].replace(".csv", "");

		insertEntities.add(mappingEntity(String.valueOf(classify_mode), returnMaxEntity));

		// 説明を取得
		String classify_explaination = getClassification(classify_mode);

		// 最新の分類モードを設定し直す(複数のclassify_modeがdtoに設定されるのを防ぐ)
		for (MatchClassificationResultEntity entity : insertEntities) {
			entity.setClassifyMode(String.valueOf(classify_mode));
		}

		executeClassifyScoreMain(insertEntities, classify_explaination, file_name);
	}

	/**
	 *
	 * @param insertEntities
	 * @param classify_explaination
	 * @param file_name
	 */
	public static void executeClassifyScoreMain(List<MatchClassificationResultEntity> insertEntities,
			String classify_explaination, String file_name) {

		ClassifyResultDbInsert classifyResultDbInsert = new ClassifyResultDbInsert();
		classifyResultDbInsert.execute(insertEntities, classify_explaination, file_name);
	}

	/**
	 * 指定されたキーに対応する分類名を返却するメソッド
	 *
	 * @param key キー
	 * @return 対応する分類名、キーが存在しない場合はエラーメッセージ
	 */
	private String getClassification(int key) {
		String classification = SCORE_CLASSIFICATION_ALL_MAP.get(key);
		if (classification != null) {
			return classification;
		} else {
			throw new BusinessException("", "", "", "指定されたキーに対応する分類名が見つかりません。key: " + key);
		}
	}

	/**
	 * クラスに対応する情報を設定するメソッド
	 *
	 * @param innerSeq 内部シーケンス
	 * @param thresholdEntity ThresHoldEntityのインスタンス
	 * @return 設定されたClassifyResultDataEntity
	 */
	private MatchClassificationResultEntity mappingEntity(String classify_mode, ThresHoldEntity thresholdEntity) {
		MatchClassificationResultEntity mappingDto = new MatchClassificationResultEntity();
		// 各フィールドにThresHoldEntityのゲッターを使ってデータを設定
		mappingDto.setSeq(thresholdEntity.getSeq());
		mappingDto.setClassifyMode(classify_mode);
		mappingDto.setDataCategory(thresholdEntity.getDataCategory());
		mappingDto.setTimes(thresholdEntity.getTimes());
		mappingDto.setHomeRank(thresholdEntity.getHomeRank());
		mappingDto.setHomeTeamName(thresholdEntity.getHomeTeamName());
		mappingDto.setHomeScore(thresholdEntity.getHomeScore());
		mappingDto.setAwayRank(thresholdEntity.getAwayRank());
		mappingDto.setAwayTeamName(thresholdEntity.getAwayTeamName());
		mappingDto.setAwayScore(thresholdEntity.getAwayScore());
		mappingDto.setHomeExp(thresholdEntity.getHomeExp());
		mappingDto.setAwayExp(thresholdEntity.getAwayExp());
		mappingDto.setHomeDonation(thresholdEntity.getHomeDonation());
		mappingDto.setAwayDonation(thresholdEntity.getAwayDonation());
		mappingDto.setHomeShootAll(thresholdEntity.getHomeShootAll());
		mappingDto.setAwayShootAll(thresholdEntity.getAwayShootAll());
		mappingDto.setHomeShootIn(thresholdEntity.getHomeShootIn());
		mappingDto.setAwayShootIn(thresholdEntity.getAwayShootIn());
		mappingDto.setHomeShootOut(thresholdEntity.getHomeShootOut());
		mappingDto.setAwayShootOut(thresholdEntity.getAwayShootOut());
		mappingDto.setHomeBlockShoot(thresholdEntity.getHomeBlockShoot());
		mappingDto.setAwayBlockShoot(thresholdEntity.getAwayBlockShoot());
		mappingDto.setHomeBigChance(thresholdEntity.getHomeBigChance());
		mappingDto.setAwayBigChance(thresholdEntity.getAwayBigChance());
		mappingDto.setHomeCorner(thresholdEntity.getHomeCorner());
		mappingDto.setAwayCorner(thresholdEntity.getAwayCorner());
		mappingDto.setHomeBoxShootIn(thresholdEntity.getHomeBoxShootIn());
		mappingDto.setAwayBoxShootIn(thresholdEntity.getAwayBoxShootIn());
		mappingDto.setHomeBoxShootOut(thresholdEntity.getHomeBoxShootOut());
		mappingDto.setAwayBoxShootOut(thresholdEntity.getAwayBoxShootOut());
		mappingDto.setHomeGoalPost(thresholdEntity.getHomeGoalPost());
		mappingDto.setAwayGoalPost(thresholdEntity.getAwayGoalPost());
		mappingDto.setHomeGoalHead(thresholdEntity.getHomeGoalHead());
		mappingDto.setAwayGoalHead(thresholdEntity.getAwayGoalHead());
		mappingDto.setHomeKeeperSave(thresholdEntity.getHomeKeeperSave());
		mappingDto.setAwayKeeperSave(thresholdEntity.getAwayKeeperSave());
		mappingDto.setHomeFreeKick(thresholdEntity.getHomeFreeKick());
		mappingDto.setAwayFreeKick(thresholdEntity.getAwayFreeKick());
		mappingDto.setHomeOffside(thresholdEntity.getHomeOffside());
		mappingDto.setAwayOffside(thresholdEntity.getAwayOffside());
		mappingDto.setHomeFoul(thresholdEntity.getHomeFoul());
		mappingDto.setAwayFoul(thresholdEntity.getAwayFoul());
		mappingDto.setHomeYellowCard(thresholdEntity.getHomeYellowCard());
		mappingDto.setAwayYellowCard(thresholdEntity.getAwayYellowCard());
		mappingDto.setHomeRedCard(thresholdEntity.getHomeRedCard());
		mappingDto.setAwayRedCard(thresholdEntity.getAwayRedCard());
		mappingDto.setHomeSlowIn(thresholdEntity.getHomeSlowIn());
		mappingDto.setAwaySlowIn(thresholdEntity.getAwaySlowIn());
		mappingDto.setHomeBoxTouch(thresholdEntity.getHomeBoxTouch());
		mappingDto.setAwayBoxTouch(thresholdEntity.getAwayBoxTouch());
		mappingDto.setHomePassCount(thresholdEntity.getHomePassCount());
		mappingDto.setAwayPassCount(thresholdEntity.getAwayPassCount());
		mappingDto.setHomeFinalThirdPassCount(thresholdEntity.getHomeFinalThirdPassCount());
		mappingDto.setAwayFinalThirdPassCount(thresholdEntity.getAwayFinalThirdPassCount());
		mappingDto.setHomeCrossCount(thresholdEntity.getHomeCrossCount());
		mappingDto.setAwayCrossCount(thresholdEntity.getAwayCrossCount());
		mappingDto.setHomeTackleCount(thresholdEntity.getHomeTackleCount());
		mappingDto.setAwayTackleCount(thresholdEntity.getAwayTackleCount());
		mappingDto.setHomeClearCount(thresholdEntity.getHomeClearCount());
		mappingDto.setAwayClearCount(thresholdEntity.getAwayClearCount());
		mappingDto.setHomeInterceptCount(thresholdEntity.getHomeInterceptCount());
		mappingDto.setAwayInterceptCount(thresholdEntity.getAwayInterceptCount());
		mappingDto.setRecordTime(thresholdEntity.getRecordTime());
		mappingDto.setWeather(thresholdEntity.getWeather());
		mappingDto.setTemparature(thresholdEntity.getTemparature());
		mappingDto.setHumid(thresholdEntity.getHumid());
		mappingDto.setJudgeMember(thresholdEntity.getJudgeMember());
		mappingDto.setHomeManager(thresholdEntity.getHomeManager());
		mappingDto.setAwayManager(thresholdEntity.getAwayManager());
		mappingDto.setHomeFormation(thresholdEntity.getHomeFormation());
		mappingDto.setAwayFormation(thresholdEntity.getAwayFormation());
		mappingDto.setStudium(thresholdEntity.getStudium());
		mappingDto.setCapacity(thresholdEntity.getCapacity());
		mappingDto.setAudience(thresholdEntity.getAudience());
		mappingDto.setHomeMaxGettingScorer(thresholdEntity.getHomeMaxGettingScorer());
		mappingDto.setAwayMaxGettingScorer(thresholdEntity.getAwayMaxGettingScorer());
		mappingDto.setHomeMaxGettingScorerGameSituation(thresholdEntity.getHomeMaxGettingScorerGameSituation());
		mappingDto.setAwayMaxGettingScorerGameSituation(thresholdEntity.getAwayMaxGettingScorerGameSituation());
		mappingDto.setHomeTeamHomeScore(thresholdEntity.getHomeTeamHomeScore());
		mappingDto.setHomeTeamHomeLost(thresholdEntity.getHomeTeamHomeLost());
		mappingDto.setAwayTeamHomeScore(thresholdEntity.getAwayTeamHomeScore());
		mappingDto.setAwayTeamHomeLost(thresholdEntity.getAwayTeamHomeLost());
		mappingDto.setHomeTeamAwayScore(thresholdEntity.getHomeTeamAwayScore());
		mappingDto.setHomeTeamAwayLost(thresholdEntity.getHomeTeamAwayLost());
		mappingDto.setAwayTeamAwayScore(thresholdEntity.getAwayTeamAwayScore());
		mappingDto.setAwayTeamAwayLost(thresholdEntity.getAwayTeamAwayLost());
		mappingDto.setNoticeFlg(thresholdEntity.getNoticeFlg());
		mappingDto.setGoalTime(thresholdEntity.getGoalTime());
		mappingDto.setGoalTeamMember(thresholdEntity.getGoalTeamMember());
		mappingDto.setJudge(thresholdEntity.getJudge());
		mappingDto.setHomeTeamStyle(thresholdEntity.getHomeTeamStyle());
		mappingDto.setAwayTeamStyle(thresholdEntity.getAwayTeamStyle());
		mappingDto.setProbablity(thresholdEntity.getProbablity());
		mappingDto.setPredictionScoreTime(thresholdEntity.getPredictionScoreTime());

		return mappingDto;
	}

}
