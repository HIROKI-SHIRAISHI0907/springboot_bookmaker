package dev.application.analyze.bm_m019_bm_m020;

/**
 * 試合の得点状況に基づく分類モード定数クラス
 * @author shiraishitoshio
 */
public class ClassifyScoreAIConst {

	// 1. 20分以内に得点が入った場合

	/** 1. 20分以内にホームチームが得点後、次の得点が前半に入る */
	public static final String HOME_SCORED_WITHIN_20_NEXT_SCORE_BEFORE_HALF = "20分以内にホームチームが得点後、次の得点が前半に入る";

	/** 2. 20分以内にホームチームが得点後、次の得点が後半に入る */
	public static final String HOME_SCORED_WITHIN_20_NEXT_SCORE_AFTER_HALF = "20分以内にホームチームが得点後、次の得点が後半に入る";

	/** 3. 20分以内にホームチームが得点後、得点が入らない */
	public static final String HOME_SCORED_WITHIN_20_NO_FURTHER_GOAL = "20分以内にホームチームが得点後、得点が入らない";

	/** 4. 20分以内にアウェーチームが得点後、次の得点が前半に入る */
	public static final String AWAY_SCORED_WITHIN_20_NEXT_SCORE_BEFORE_HALF = "20分以内にアウェーチームが得点後、次の得点が前半に入る";

	/** 5. 20分以内にアウェーチームが得点後、次の得点が後半に入る */
	public static final String AWAY_SCORED_WITHIN_20_NEXT_SCORE_AFTER_HALF = "20分以内にアウェーチームが得点後、次の得点が後半に入る";

	/** 6. 20分以内にアウェーチームが得点後、得点が入らない */
	public static final String AWAY_SCORED_WITHIN_20_NO_FURTHER_GOAL = "20分以内にアウェーチームが得点後、得点が入らない";

	// 2. 20分〜前半に得点が入った場合

	/** 7. 20分〜前半にホームチームが得点後、次の得点が前半に入る */
	public static final String HOME_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_BEFORE_HALF = "20分〜前半にホームチームが得点後、次の得点が前半に入る";

	/** 8. 20分〜前半にホームチームが得点後、次の得点が後半に入る */
	public static final String HOME_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_AFTER_HALF = "20分〜前半にホームチームが得点後、次の得点が後半に入る";

	/** 9. 20分〜前半にホームチームが得点後、得点が入らない */
	public static final String HOME_SCORED_BETWEEN_20_AND_45_NO_FURTHER_GOAL = "20分〜前半にホームチームが得点後、得点が入らない";

	/** 10. 20分〜前半にアウェーチームが得点後、次の得点が前半に入る */
	public static final String AWAY_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_BEFORE_HALF = "20分〜前半にアウェーチームが得点後、次の得点が前半に入る";

	/** 11. 20分〜前半にアウェーチームが得点後、次の得点が後半に入る */
	public static final String AWAY_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_AFTER_HALF = "20分〜前半にアウェーチームが得点後、次の得点が後半に入る";

	/** 12. 20分〜前半にアウェーチームが得点後、得点が入らない */
	public static final String AWAY_SCORED_BETWEEN_20_AND_45_NO_FURTHER_GOAL = "20分〜前半にアウェーチームが得点後、得点が入らない";

	// 3. 前半で無得点だった場合

	/** 13. 前半で無得点後、後半にホーム側の得点が入る */
	public static final String NO_GOAL_FIRST_HALF_NEXT_HOME_SCORE = "前半で無得点後、後半にホーム側の得点が入る";

	/** 14. 前半で無得点後、後半にアウェー側の得点が入る */
	public static final String NO_GOAL_FIRST_HALF_NEXT_AWAY_SCORE = "前半で無得点後、後半にアウェー側の得点が入る";

	// 4. 無得点の場合

	/** 15. 両チーム無得点後、得点が入らない */
	public static final String NO_GOAL = "両チーム無得点後、得点が入らない";

	// 5. 条件対象外(賭け対象外もしくは1.〜4.までの条件に当てはまらない)

	/** -1. 条件対象外 */
	public static final String EXCEPT_FOR_CONDITION = "条件対象外";
}
