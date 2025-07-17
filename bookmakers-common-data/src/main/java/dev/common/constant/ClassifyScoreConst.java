package dev.common.constant;

/**
 * 分類テーブル定数クラス
 * @author shiraishitoshio
 *
 */
public class ClassifyScoreConst {

	/** 1. 20分以内にホーム得点、試合終了（1-0） */
	public static final String HOME_SCORED_WITHIN_20_AND_FIN =
			"20分以内にホーム得点、試合終了（1-0）";

	/** 2. 20分以内にアウェー得点、試合終了（0-1） */
	public static final String AWAY_SCORED_WITHIN_20_AND_FIN =
			"20分以内にアウェー得点、試合終了（0-1）";

	/** 3. 20分〜前半にホーム得点、試合終了（1-0）*/
	public static final String HOME_SCORED_BETWEEN_20_AND_45_FIN =
			"20分〜前半にホーム得点、試合終了（1-0）";

	/** 4. 20分〜前半にアウェー得点、試合終了（0-1） */
	public static final String AWAY_SCORED_BETWEEN_20_AND_45_FIN =
			"20分〜前半にアウェー得点、試合終了（0-1）";

	/** 5. 20分以内にホームアウェー得点、試合終了（1-1） */
	public static final String HOME_AWAY_SCORED_WITHIN_20_AND_FIN =
			"20分以内にホームアウェー得点、試合終了（1-1）";

	/** 6. 20分以内にアウェーホーム得点、試合終了（1-1） */
	public static final String AWAY_HOME_SCORED_WITHIN_20_AND_FIN =
			"20分以内にアウェーホーム得点、試合終了（1-1）";

	/** 7. 20分以内にホームアウェー得点、以降どちらか関係なく得点 */
	public static final String HOME_AWAY_SCORED_WITHIN_20_AND_OVER_SCORED =
			"20分以内にホームアウェー得点、以降どちらか関係なく得点";

	/** 8. 20分以内にアウェーホーム得点、以降どちらか関係なく得点 */
	public static final String AWAY_HOME_SCORED_WITHIN_20_AND_OVER_SCORED =
			"20分以内にアウェーホーム得点、以降どちらか関係なく得点";

	/** 9. 20分以内にホーム、20分〜前半アウェー得点、試合終了（1-1） */
	public static final String HOME_SCORED_WITHIN_20_AWAY_BETWEEN_20_AND_45_FIN =
			"20分以内にホーム、20分〜前半アウェー得点、試合終了（1-1）";

	/** 10. 20分以内にアウェー、20分〜前半ホーム得点、試合終了（1-1） */
	public static final String AWAY_SCORED_WITHIN_20_HOME_BETWEEN_20_AND_45_FIN =
			"20分以内にアウェー、20分〜前半ホーム得点、試合終了（1-1）";

	/** 11. 20分以内にホーム、20分〜前半アウェー得点、以降どちらか関係なく得点 */
	public static final String HOME_SCORED_WITHIN_20_AWAY_BETWEEN_20_AND_45_AND_OVER_SCORED =
			"20分以内にホーム、20分〜前半アウェー得点、以降どちらか関係なく得点";

	/** 12. 20分以内にアウェー、20分〜前半ホーム得点、以降どちらか関係なく得点 */
	public static final String AWAY_SCORED_WITHIN_20_HOME_BETWEEN_20_AND_45_AND_OVER_SCORED =
			"20分以内にアウェー、20分〜前半ホーム得点、以降どちらか関係なく得点";

	/** 13. 20分〜前半にホームアウェー得点、試合終了（1-1） */
	public static final String HOME_AWAY_BETWEEN_20_AND_45_AND_FIN =
			"20分〜前半にホームアウェー得点、試合終了（1-1）";

	/** 14. 20分〜前半にアウェーホーム得点、試合終了（1-1） */
	public static final String AWAY_HOME_BETWEEN_20_AND_45_AND_FIN =
			"20分〜前半にアウェーホーム得点、試合終了（1-1）";

	/** 15. 45分以内にホーム得点、後半ホーム得点、試合終了（2-0） */
	public static final String HOME_SCORED_WITHIN_45_HOME_OVER_45_AND_FIN =
			"45分以内にホーム得点、後半ホーム得点、試合終了（2-0）";

	/** 16. 45分以内にアウェー得点、後半アウェー得点、試合終了（0-2） */
	public static final String AWAY_SCORED_WITHIN_45_AWAY_OVER_45_AND_FIN =
			"45分以内にアウェー得点、後半アウェー得点、試合終了（0-2）";

	/** 17. 45分以内にホーム得点、後半ホーム得点、以降どちらか関係なく得点 */
	public static final String HOME_SCORED_WITHIN_45_HOME_OVER_45_AND_OVER_SCORED =
			"45分以内にホーム得点、後半ホーム得点、以降どちらか関係なく得点";

	/** 18. 45分以内にアウェー得点、後半アウェー得点、以降どちらか関係なく得点 */
	public static final String AWAY_SCORED_WITHIN_45_AWAY_OVER_45_AND_OVER_SCORED =
			"45分以内にアウェー得点、後半アウェー得点、以降どちらか関係なく得点";

	/** 19. 45分間無得点、後半ホーム得点、以降無得点得点関係なし（1-0以上） */
	public static final String HOME_NO_SCORED_OVER_45_AND_OVER_SCORED =
			"45分間無得点、後半ホーム得点、以降無得点得点関係なし（1-0以上）";

	/** 20. 45分間無得点、後半アウェー得点、以降無得点得点関係なし（0-1以上） */
	public static final String AWAY_NO_SCORED_OVER_45_AND_OVER_SCORED =
			"45分間無得点、後半アウェー得点、以降無得点得点関係なし（0-1以上）";

	/** 21. 45分以内にホーム2得点、以降どちらか関係なく得点 */
	public static final String HOME_2SCORED_WITHIN_45_AND_OVER_SCORED =
			"45分以内にホーム2得点、以降どちらか関係なく得点";

	/** 22. 45分以内にアウェー2得点、以降どちらか関係なく得点 */
	public static final String AWAY_2SCORED_WITHIN_45_AND_OVER_SCORED =
			"45分以内にアウェー2得点、以降どちらか関係なく得点";

	/** 23. 45分以内にホーム2得点、そのまま試合終了 */
	public static final String HOME_2SCORED_WITHIN_45_AND_FIN =
			"45分以内にホーム2得点、そのまま試合終了";

	/** 24. 45分以内にアウェー2得点、そのまま試合終了 */
	public static final String AWAY_2SCORED_WITHIN_45_AND_FIN =
			"45分以内にアウェー2得点、そのまま試合終了";

	/** 25. 45分以内にホーム得点、後半アウェー得点、以降どちらか関係なく得点 */
	public static final String HOME_SCORED_WITHIN_45_AWAY_OVER_45_AND_OVER_SCORED =
			"45分以内にホーム得点、後半アウェー得点、以降どちらか関係なく得点";

	/** 26. 45分以内にアウェー得点、後半ホーム得点、以降どちらか関係なく得点 */
	public static final String AWAY_SCORED_WITHIN_45_HOME_OVER_45_AND_OVER_SCORED =
			"45分以内にアウェー得点、後半ホーム得点、以降どちらか関係なく得点";

	/** 27. 得点なし */
	public static final String NO_SCORED =
			"得点なし";

	/** 28. 条件対象外 */
	public static final String EXCEPT_FOR_CONDITION =
			"条件対象外";
}
