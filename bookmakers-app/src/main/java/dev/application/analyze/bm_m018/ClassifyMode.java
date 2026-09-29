package dev.application.analyze.bm_m018;

import java.util.List;

/**
 * 試合の得点状況による分類モード（BM_M019 / BM_M020）。
 *
 * <h2>何を表すクラスか</h2>
 * <p>
 * 試合終了した試合を「最初のゴールの時間帯・得点した側」と「次のゴールが前半／後半／なし」で分類する。
 * 番号（{@link #getCode()}）は DB の classify_mode、文言（{@link #getLabel()}）は BM_M020 ビューの remarks になる。
 * </p>
 * <ul>
 *   <li>「20分以内」: 最初のゴールの経過分数（整数に切り捨て）が 20 以下。"20:59" も 20分以内。</li>
 *   <li>「20分〜前半」: 21分〜前半終了。前半アディショナルタイム（"45+2'"・ハーフタイム行）も前半。</li>
 *   <li>「次の得点」: 2点目（どちらのチームでもよい）。</li>
 * </ul>
 *
 * <h2>統合したクラス</h2>
 * <p>
 * 旧 ClassifyScoreAIConst（文言の定数）と MatchClassificationResultStat.SCORE_CLASSIFICATION_ALL_MAP（番号→文言）を
 * このクラス1つにまとめた。ClassifyScoreAIConst を他のクラスで使っていなければ削除してよい。
 * </p>
 *
 * @author shiraishitoshio
 */
public enum ClassifyMode {

	/** 1. 20分以内にホームチームが得点後、次の得点が前半に入る */
	HOME_SCORED_WITHIN_20_NEXT_SCORE_BEFORE_HALF(1, "20分以内にホームチームが得点後、次の得点が前半に入る"),

	/** 2. 20分以内にホームチームが得点後、次の得点が後半に入る */
	HOME_SCORED_WITHIN_20_NEXT_SCORE_AFTER_HALF(2, "20分以内にホームチームが得点後、次の得点が後半に入る"),

	/** 3. 20分以内にホームチームが得点後、得点が入らない */
	HOME_SCORED_WITHIN_20_NO_FURTHER_GOAL(3, "20分以内にホームチームが得点後、得点が入らない"),

	/** 4. 20分以内にアウェーチームが得点後、次の得点が前半に入る */
	AWAY_SCORED_WITHIN_20_NEXT_SCORE_BEFORE_HALF(4, "20分以内にアウェーチームが得点後、次の得点が前半に入る"),

	/** 5. 20分以内にアウェーチームが得点後、次の得点が後半に入る */
	AWAY_SCORED_WITHIN_20_NEXT_SCORE_AFTER_HALF(5, "20分以内にアウェーチームが得点後、次の得点が後半に入る"),

	/** 6. 20分以内にアウェーチームが得点後、得点が入らない */
	AWAY_SCORED_WITHIN_20_NO_FURTHER_GOAL(6, "20分以内にアウェーチームが得点後、得点が入らない"),

	/** 7. 20分〜前半にホームチームが得点後、次の得点が前半に入る */
	HOME_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_BEFORE_HALF(7, "20分〜前半にホームチームが得点後、次の得点が前半に入る"),

	/** 8. 20分〜前半にホームチームが得点後、次の得点が後半に入る */
	HOME_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_AFTER_HALF(8, "20分〜前半にホームチームが得点後、次の得点が後半に入る"),

	/** 9. 20分〜前半にホームチームが得点後、得点が入らない */
	HOME_SCORED_BETWEEN_20_AND_45_NO_FURTHER_GOAL(9, "20分〜前半にホームチームが得点後、得点が入らない"),

	/** 10. 20分〜前半にアウェーチームが得点後、次の得点が前半に入る */
	AWAY_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_BEFORE_HALF(10, "20分〜前半にアウェーチームが得点後、次の得点が前半に入る"),

	/** 11. 20分〜前半にアウェーチームが得点後、次の得点が後半に入る */
	AWAY_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_AFTER_HALF(11, "20分〜前半にアウェーチームが得点後、次の得点が後半に入る"),

	/** 12. 20分〜前半にアウェーチームが得点後、得点が入らない */
	AWAY_SCORED_BETWEEN_20_AND_45_NO_FURTHER_GOAL(12, "20分〜前半にアウェーチームが得点後、得点が入らない"),

	/** 13. 前半で無得点後、後半にホーム側の得点が入る */
	NO_GOAL_FIRST_HALF_NEXT_HOME_SCORE(13, "前半で無得点後、後半にホーム側の得点が入る"),

	/** 14. 前半で無得点後、後半にアウェー側の得点が入る */
	NO_GOAL_FIRST_HALF_NEXT_AWAY_SCORE(14, "前半で無得点後、後半にアウェー側の得点が入る"),

	/** 15. 両チーム無得点後、得点が入らない */
	NO_GOAL(15, "両チーム無得点後、得点が入らない"),

	/** -1. 条件対象外（ゴールの時間が読めず分類できない） */
	EXCEPT_FOR_CONDITION(-1, "条件対象外");

	/** 最初のゴールの時間帯 */
	public enum FirstGoalBand {
		/** 20分以内 */
		WITHIN_20,
		/** 21分〜前半終了 */
		BETWEEN_20_AND_45
	}

	/** 次のゴール */
	public enum NextGoal {
		/** 前半 */
		BEFORE_HALF,
		/** 後半 */
		AFTER_HALF,
		/** なし */
		NONE
	}

	private final int code;
	private final String label;

	ClassifyMode(int code, String label) {
		this.code = code;
		this.label = label;
	}

	/** DB の classify_mode */
	public int getCode() {
		return this.code;
	}

	/** 文言（BM_M020 の remarks） */
	public String getLabel() {
		return this.label;
	}

	/** 全モード（BM_M020 ビューの行の並びと同じ: 1〜15, -1） */
	public static List<ClassifyMode> all() {
		return List.of(values());
	}

	/**
	 * 前半に最初のゴールが入った試合のモード（1〜12）。
	 *
	 * @param homeScoredFirst 最初のゴールがホーム
	 * @param band 最初のゴールの時間帯
	 * @param next 次のゴール
	 */
	public static ClassifyMode ofFirstHalfGoal(boolean homeScoredFirst, FirstGoalBand band, NextGoal next) {
		int base = (band == FirstGoalBand.WITHIN_20) ? 1 : 7;
		if (!homeScoredFirst) {
			base += 3;
		}
		int offset;
		switch (next) {
		case BEFORE_HALF:
			offset = 0;
			break;
		case AFTER_HALF:
			offset = 1;
			break;
		default:
			offset = 2;
			break;
		}
		return ofCode(base + offset);
	}

	/**
	 * 番号からモードを返す（該当なしは EXCEPT_FOR_CONDITION）。
	 */
	public static ClassifyMode ofCode(int code) {
		for (ClassifyMode m : values()) {
			if (m.code == code) {
				return m;
			}
		}
		return EXCEPT_FOR_CONDITION;
	}
}
