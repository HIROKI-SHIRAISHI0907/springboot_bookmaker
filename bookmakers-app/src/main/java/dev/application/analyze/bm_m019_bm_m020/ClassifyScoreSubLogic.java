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
public class ClassifyScoreSubLogic {

	// 分類モード
	private int classify_mode = -1;

	/**
	 * DB項目,テーブル名Mapping
	 */
	public static final Map<Integer, String> SCORE_CLASSIFICATION_ALL_MAP;
	static {
		HashMap<Integer, String> SCORE_CLASSIFICATION_MAP = new LinkedHashMap<>();
		SCORE_CLASSIFICATION_MAP.put(1, ClassifyScoreConst.HOME_SCORED_WITHIN_20_AND_FIN);
		SCORE_CLASSIFICATION_MAP.put(2, ClassifyScoreConst.AWAY_SCORED_WITHIN_20_AND_FIN);
		SCORE_CLASSIFICATION_MAP.put(3, ClassifyScoreConst.HOME_SCORED_BETWEEN_20_AND_45_FIN);
		SCORE_CLASSIFICATION_MAP.put(4, ClassifyScoreConst.AWAY_SCORED_BETWEEN_20_AND_45_FIN);
		SCORE_CLASSIFICATION_MAP.put(5, ClassifyScoreConst.HOME_AWAY_SCORED_WITHIN_20_AND_FIN);
		SCORE_CLASSIFICATION_MAP.put(6, ClassifyScoreConst.AWAY_HOME_SCORED_WITHIN_20_AND_FIN);
		SCORE_CLASSIFICATION_MAP.put(7, ClassifyScoreConst.HOME_AWAY_SCORED_WITHIN_20_AND_OVER_SCORED);
		SCORE_CLASSIFICATION_MAP.put(8, ClassifyScoreConst.AWAY_HOME_SCORED_WITHIN_20_AND_OVER_SCORED);
		SCORE_CLASSIFICATION_MAP.put(9, ClassifyScoreConst.HOME_SCORED_WITHIN_20_AWAY_BETWEEN_20_AND_45_FIN);
		SCORE_CLASSIFICATION_MAP.put(10, ClassifyScoreConst.AWAY_SCORED_WITHIN_20_HOME_BETWEEN_20_AND_45_FIN);
		SCORE_CLASSIFICATION_MAP.put(11,
				ClassifyScoreConst.HOME_SCORED_WITHIN_20_AWAY_BETWEEN_20_AND_45_AND_OVER_SCORED);
		SCORE_CLASSIFICATION_MAP.put(12,
				ClassifyScoreConst.AWAY_SCORED_WITHIN_20_HOME_BETWEEN_20_AND_45_AND_OVER_SCORED);
		SCORE_CLASSIFICATION_MAP.put(13, ClassifyScoreConst.HOME_AWAY_BETWEEN_20_AND_45_AND_FIN);
		SCORE_CLASSIFICATION_MAP.put(14, ClassifyScoreConst.AWAY_HOME_BETWEEN_20_AND_45_AND_FIN);
		SCORE_CLASSIFICATION_MAP.put(15, ClassifyScoreConst.HOME_SCORED_WITHIN_45_HOME_OVER_45_AND_FIN);
		SCORE_CLASSIFICATION_MAP.put(16, ClassifyScoreConst.AWAY_SCORED_WITHIN_45_AWAY_OVER_45_AND_FIN);
		SCORE_CLASSIFICATION_MAP.put(17, ClassifyScoreConst.HOME_SCORED_WITHIN_45_HOME_OVER_45_AND_OVER_SCORED);
		SCORE_CLASSIFICATION_MAP.put(18, ClassifyScoreConst.AWAY_SCORED_WITHIN_45_AWAY_OVER_45_AND_OVER_SCORED);
		SCORE_CLASSIFICATION_MAP.put(19, ClassifyScoreConst.HOME_NO_SCORED_OVER_45_AND_OVER_SCORED);
		SCORE_CLASSIFICATION_MAP.put(20, ClassifyScoreConst.AWAY_NO_SCORED_OVER_45_AND_OVER_SCORED);
		SCORE_CLASSIFICATION_MAP.put(21, ClassifyScoreConst.HOME_2SCORED_WITHIN_45_AND_OVER_SCORED);
		SCORE_CLASSIFICATION_MAP.put(22, ClassifyScoreConst.AWAY_2SCORED_WITHIN_45_AND_OVER_SCORED);
		SCORE_CLASSIFICATION_MAP.put(23, ClassifyScoreConst.HOME_2SCORED_WITHIN_45_AND_FIN);
		SCORE_CLASSIFICATION_MAP.put(24, ClassifyScoreConst.AWAY_2SCORED_WITHIN_45_AND_FIN);
		SCORE_CLASSIFICATION_MAP.put(25, ClassifyScoreConst.HOME_SCORED_WITHIN_45_AWAY_OVER_45_AND_OVER_SCORED);
		SCORE_CLASSIFICATION_MAP.put(26, ClassifyScoreConst.AWAY_SCORED_WITHIN_45_HOME_OVER_45_AND_OVER_SCORED);
		SCORE_CLASSIFICATION_MAP.put(27, ClassifyScoreConst.NO_SCORED);
		SCORE_CLASSIFICATION_MAP.put(28, ClassifyScoreConst.EXCEPT_FOR_CONDITION);
		SCORE_CLASSIFICATION_ALL_MAP = Collections.unmodifiableMap(SCORE_CLASSIFICATION_MAP);
	}

	/**
	 * コンストラクタ
	 */
	public ClassifyScoreSubLogic() {
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
		if (maxHomeScore == 1 && maxAwayScore == 1) {
			classifyModeConditionList.add(-4);
		}
		if (maxHomeScore > 1 && maxAwayScore == 1) {
			classifyModeConditionList.add(-5);
		}
		if (maxHomeScore == 1 && maxAwayScore > 1) {
			classifyModeConditionList.add(-6);
		}
		if (maxHomeScore > 1 && maxAwayScore == 0) {
			classifyModeConditionList.add(-7);
		}
		if (maxHomeScore == 0 && maxAwayScore > 1) {
			classifyModeConditionList.add(-8);
		}
		if (maxHomeScore > 1 && maxAwayScore > 1) {
			classifyModeConditionList.add(-9);
		}
		if (maxHomeScore == 2 && maxAwayScore == 0) {
			classifyModeConditionList.add(-10);
		}
		if (maxHomeScore == 0 && maxAwayScore == 2) {
			classifyModeConditionList.add(-11);
		}
		if (maxHomeScore > 0 && maxAwayScore > 0) {
			classifyModeConditionList.add(-12);
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

			if ((int) convert_game_time <= 20 && (home_score == 1 && away_score == 0)) {
				classifyModeConditionList.add(1);
			}
			if ((int) convert_game_time <= 20 && (home_score == 0 && away_score == 1)) {
				classifyModeConditionList.add(2);
			}
			if ((int) convert_game_time > 20 && (int) convert_game_time <= 45 &&
					(home_score == 1 && away_score == 0)) {
				classifyModeConditionList.add(3);
			}
			if ((int) convert_game_time > 20 && (int) convert_game_time <= 45 &&
					(home_score == 0 && away_score == 1)) {
				classifyModeConditionList.add(4);
			}
			if ((int) convert_game_time <= 20 && (home_score == 1 && away_score == 1)) {
				classifyModeConditionList.add(5);
			}
			if ((int) convert_game_time > 20 && (int) convert_game_time <= 45 &&
					(home_score == 1 && away_score == 1)) {
				classifyModeConditionList.add(6);
			}
			if ((int) convert_game_time <= 20 && (home_score == 0 && away_score == 0)) {
				classifyModeConditionList.add(7);
			}
			if ((int) convert_game_time <= 45 &&
					(home_score == 1 && away_score == 0)) {
				classifyModeConditionList.add(8);
			}
			if ((int) convert_game_time <= 45 &&
					(home_score == 0 && away_score == 1)) {
				classifyModeConditionList.add(9);
			}
			if ((int) convert_game_time > 45 &&
					(home_score == 2 && away_score == 0)) {
				classifyModeConditionList.add(10);
			}
			if ((int) convert_game_time > 45 &&
					(home_score == 0 && away_score == 2)) {
				classifyModeConditionList.add(11);
			}
			if ((int) convert_game_time > 45 &&
					(home_score == 1 && away_score == 1)) {
				classifyModeConditionList.add(12);
			}
			if ((BookMakersCommonConst.FIRST_HALF_TIME.equals(entity.getTimes()) ||
					BookMakersCommonConst.HALF_TIME.equals(entity.getTimes())) &&
					(home_score == 0 && away_score == 0)) {
				classifyModeConditionList.add(13);
			}
			if ((int) convert_game_time > 45 &&
					(home_score == 1 && away_score == 0)) {
				classifyModeConditionList.add(14);
			}
			if ((int) convert_game_time > 45 &&
					(home_score == 0 && away_score == 1)) {
				classifyModeConditionList.add(15);
			}
			if ((int) convert_game_time <= 45 &&
					(home_score == 2 && away_score == 0)) {
				classifyModeConditionList.add(16);
			}
			if ((int) convert_game_time <= 45 &&
					(home_score == 0 && away_score == 2)) {
				classifyModeConditionList.add(17);
			}

			// 1. 20分以内にホーム得点、試合終了（1-0）
			if (classifyModeConditionList.contains(1) &&
					classifyModeConditionList.contains(-2)) {
				this.classify_mode = 1;
			}
			// 2. 20分以内にアウェー得点、試合終了（0-1）
			if (classifyModeConditionList.contains(2) &&
					classifyModeConditionList.contains(-3)) {
				this.classify_mode = 2;
			}
			// 3. 20分〜前半にホーム得点、試合終了（1-0）
			if (classifyModeConditionList.contains(3) &&
					classifyModeConditionList.contains(-2)) {
				this.classify_mode = 3;
			}
			// 4. 20分〜前半にアウェー得点、試合終了（0-1）
			if (classifyModeConditionList.contains(4) &&
					classifyModeConditionList.contains(-3)) {
				this.classify_mode = 4;
			}
			// 5. 20分以内にホームアウェー得点、試合終了（1-1）
			if (classifyModeConditionList.contains(1) &&
					classifyModeConditionList.contains(5) &&
					classifyModeConditionList.contains(-4)) {
				this.classify_mode = 5;
			}
			// 6. 20分以内にアウェーホーム得点、試合終了（1-1）
			if (classifyModeConditionList.contains(2) &&
					classifyModeConditionList.contains(5) &&
					classifyModeConditionList.contains(-4)) {
				this.classify_mode = 6;
			}
			// 7. 20分以内にホームアウェー得点、以降どちらか関係なく得点
			if (classifyModeConditionList.contains(1) &&
					classifyModeConditionList.contains(5) &&
					(classifyModeConditionList.contains(-5) ||
							classifyModeConditionList.contains(-6) ||
							classifyModeConditionList.contains(-9))) {
				this.classify_mode = 7;
			}
			// 8. 20分以内にアウェーホーム得点、以降どちらか関係なく得点
			if (classifyModeConditionList.contains(2) &&
					classifyModeConditionList.contains(5) &&
					(classifyModeConditionList.contains(-5) ||
							classifyModeConditionList.contains(-6) ||
							classifyModeConditionList.contains(-9))) {
				this.classify_mode = 8;
			}
			// 9. 20分以内にホーム、20分〜前半アウェー得点、試合終了（1-1）
			if (classifyModeConditionList.contains(1) &&
					classifyModeConditionList.contains(6) &&
					classifyModeConditionList.contains(-4)) {
				this.classify_mode = 9;
			}
			// 10. 20分以内にアウェー、20分〜前半ホーム得点、試合終了（1-1）
			if (classifyModeConditionList.contains(2) &&
					classifyModeConditionList.contains(6) &&
					classifyModeConditionList.contains(-4)) {
				this.classify_mode = 10;
			}
			// 11. 20分以内にホーム、20分〜前半アウェー得点、以降どちらか関係なく得点
			if (classifyModeConditionList.contains(1) &&
					classifyModeConditionList.contains(6) &&
					(classifyModeConditionList.contains(-5) ||
							classifyModeConditionList.contains(-6) ||
							classifyModeConditionList.contains(-9))) {
				this.classify_mode = 11;
			}
			// 12. 20分以内にアウェー、20分〜前半ホーム得点、以降どちらか関係なく得点
			if (classifyModeConditionList.contains(2) &&
					classifyModeConditionList.contains(6) &&
					(classifyModeConditionList.contains(-5) ||
							classifyModeConditionList.contains(-6) ||
							classifyModeConditionList.contains(-9))) {
				this.classify_mode = 12;
			}
			// 13. 20分〜前半にホームアウェー得点、試合終了（1-1）
			if (classifyModeConditionList.contains(7) &&
					classifyModeConditionList.contains(3) &&
					classifyModeConditionList.contains(6) &&
					classifyModeConditionList.contains(-4)) {
				this.classify_mode = 13;
			}
			// 14. 20分〜前半にアウェーホーム得点、試合終了（1-1）
			if (classifyModeConditionList.contains(7) &&
					classifyModeConditionList.contains(4) &&
					classifyModeConditionList.contains(6) &&
					classifyModeConditionList.contains(-4)) {
				this.classify_mode = 14;
			}
			// 15. 45分以内にホーム得点、後半ホーム得点、試合終了（2-0）
			if (classifyModeConditionList.contains(8) &&
					classifyModeConditionList.contains(10) &&
					classifyModeConditionList.contains(-10)) {
				this.classify_mode = 15;
			}
			// 16. 45分以内にアウェー得点、後半アウェー得点、試合終了（0-2）
			if (classifyModeConditionList.contains(9) &&
					classifyModeConditionList.contains(11) &&
					classifyModeConditionList.contains(-11)) {
				this.classify_mode = 16;
			}
			// 17. 45分以内にホーム得点、後半ホーム得点、以降どちらか関係なく得点
			if (classifyModeConditionList.contains(8) &&
					classifyModeConditionList.contains(10) &&
					(classifyModeConditionList.contains(-5) ||
							classifyModeConditionList.contains(-6) ||
							classifyModeConditionList.contains(-7) ||
							classifyModeConditionList.contains(-9))) {
				this.classify_mode = 17;
			}
			// 18. 45分以内にアウェー得点、後半アウェー得点、以降どちらか関係なく得点
			if (classifyModeConditionList.contains(9) &&
					classifyModeConditionList.contains(11) &&
					(classifyModeConditionList.contains(-5) ||
							classifyModeConditionList.contains(-6) ||
							classifyModeConditionList.contains(-8) ||
							classifyModeConditionList.contains(-9))) {
				this.classify_mode = 18;
			}
			// 19. 45分間無得点、後半ホーム得点、以降無得点得点関係なし（1-0以上）
			if (classifyModeConditionList.contains(13) &&
					classifyModeConditionList.contains(14) &&
					(classifyModeConditionList.contains(-2) ||
							classifyModeConditionList.contains(-7) ||
							classifyModeConditionList.contains(-12))) {
				this.classify_mode = 19;
			}
			// 20. 45分間無得点、後半アウェー得点、以降無得点得点関係なし（0-1以上）
			if (classifyModeConditionList.contains(13) &&
					classifyModeConditionList.contains(15) &&
					(classifyModeConditionList.contains(-3) ||
							classifyModeConditionList.contains(-8) ||
							classifyModeConditionList.contains(-12))) {
				this.classify_mode = 20;
			}
			// 21. 45分以内にホーム2得点、以降どちらか関係なく得点
			if (classifyModeConditionList.contains(16) &&
					(classifyModeConditionList.contains(-5) ||
							classifyModeConditionList.contains(-6) ||
							classifyModeConditionList.contains(-7) ||
							classifyModeConditionList.contains(-9))) {
				this.classify_mode = 21;
			}
			// 22. 45分以内にアウェー2得点、以降どちらか関係なく得点
			if (classifyModeConditionList.contains(17) &&
					(classifyModeConditionList.contains(-5) ||
							classifyModeConditionList.contains(-6) ||
							classifyModeConditionList.contains(-8) ||
							classifyModeConditionList.contains(-9))) {
				this.classify_mode = 22;
			}
			// 23. 45分以内にホーム2得点、そのまま試合終了
			if (classifyModeConditionList.contains(16) &&
					classifyModeConditionList.contains(-10)) {
				this.classify_mode = 23;
			}
			// 24. 45分以内にアウェー2得点、そのまま試合終了
			if (classifyModeConditionList.contains(17) &&
					classifyModeConditionList.contains(-11)) {
				this.classify_mode = 24;
			}
			// 25. 45分以内にホーム得点、後半アウェー得点、以降どちらか関係なく得点
			if (classifyModeConditionList.contains(8) &&
					classifyModeConditionList.contains(12) &&
					(classifyModeConditionList.contains(-5) ||
							classifyModeConditionList.contains(-6) ||
							classifyModeConditionList.contains(-9))) {
				this.classify_mode = 25;
			}
			// 26. 45分以内にアウェー得点、後半ホーム得点、以降どちらか関係なく得点
			if (classifyModeConditionList.contains(9) &&
					classifyModeConditionList.contains(12) &&
					(classifyModeConditionList.contains(-5) ||
							classifyModeConditionList.contains(-6) ||
							classifyModeConditionList.contains(-9))) {
				this.classify_mode = 26;
			}
			// 27. 得点なし
			if (classifyModeConditionList.contains(-1)) {
				this.classify_mode = 27;
			}
			// 28. 条件対象外
			if (this.classify_mode == -1) {
				this.classify_mode = 28;
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

		// 条件対象外の場合,file名のcsvを除いた数字をDBに登録(具体的にどのcsvが対象外になったかを調べるため)
		String file_name = "";
		if (this.classify_mode == SCORE_CLASSIFICATION_ALL_MAP.size()) {
			String[] fileList = file.split("/");
			file_name = fileList[fileList.length - 1].replace(".csv", "");
		}

		insertEntities.add(mappingEntity(String.valueOf(classify_mode), returnMaxEntity));

		// 説明を取得
		String classify_explaination = getClassification(classify_mode);

		// 最新の分類モードを設定し直す
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
