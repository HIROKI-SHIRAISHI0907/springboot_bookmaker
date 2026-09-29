package dev.application.analyze.bm_m023;

import java.math.BigDecimal;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.common.entity.BookDataEntity;

/**
 * BM_M023 / BM_M026 で集計する特徴量の定義（39 項目）。
 *
 * <h2>何を表すクラスか</h2>
 * <p>
 * BookDataEntity のチーム別項目（ホーム/アウェー）を、集計用の数値に変換する方法を1か所にまとめたもの。
 * 旧実装はリフレクションでフィールドの並び順（getDeclaredFields の位置、FEATURE_START = 11 など）に頼っていたが、
 * getter を直接持つため、BookDataEntity の項目順が変わっても壊れない。
 * </p>
 * <ul>
 *   <li>NUMBER: 数値（"55%" は 55、"1.23" は 1.23）</li>
 *   <li>パス系（"85% (340/400)"）は3つに分ける: *_rate（成功率 85）/ *_success（成功数 340）/ *_try（試行数 400）。
 *       "85%" だけの値は成功率のみ。</li>
 * </ul>
 * <p>
 * 旧 BM_M023 はパス系を「成功数」だけで、旧 BM_M026 は集計対象外にしていた（不一致）。3つに分けて両方で同じにした。
 * </p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li>値は「その時点までの累計」（BookDataEntity の値そのまま）。区分内の各スナップショットを1観測として集計する（旧実装と同じ）。</li>
 *   <li>項目を追加しても DB の feature 列に新しい値が入るだけ（DDL 変更不要）。並び順は {@link #getOrder()}。</li>
 * </ul>
 */
public enum ScoreBasedFeature {

	/** 期待値 */
	EXP("exp", 0, Kind.NUMBER,
			BookDataEntity::getHomeExp, BookDataEntity::getAwayExp),

	/** 枠内ゴール期待値 */
	IN_GOAL_EXP("inGoalExp", 1, Kind.NUMBER,
			BookDataEntity::getHomeInGoalExp, BookDataEntity::getAwayInGoalExp),

	/** ポゼッション（%） */
	BALL_POSSESION("ballPossesion", 2, Kind.NUMBER,
			BookDataEntity::getHomeBallPossesion, BookDataEntity::getAwayBallPossesion),

	/** シュート数 */
	SHOOT_ALL("shootAll", 3, Kind.NUMBER,
			BookDataEntity::getHomeShootAll, BookDataEntity::getAwayShootAll),

	/** 枠内シュート */
	SHOOT_IN("shootIn", 4, Kind.NUMBER,
			BookDataEntity::getHomeShootIn, BookDataEntity::getAwayShootIn),

	/** 枠外シュート */
	SHOOT_OUT("shootOut", 5, Kind.NUMBER,
			BookDataEntity::getHomeShootOut, BookDataEntity::getAwayShootOut),

	/** ブロックシュート */
	SHOOT_BLOCKED("shootBlocked", 6, Kind.NUMBER,
			BookDataEntity::getHomeShootBlocked, BookDataEntity::getAwayShootBlocked),

	/** ビッグチャンス */
	BIG_CHANCE("bigChance", 7, Kind.NUMBER,
			BookDataEntity::getHomeBigChance, BookDataEntity::getAwayBigChance),

	/** コーナーキック */
	CORNER_KICK("cornerKick", 8, Kind.NUMBER,
			BookDataEntity::getHomeCornerKick, BookDataEntity::getAwayCornerKick),

	/** ボックス内シュート */
	BOX_SHOOT_IN("boxShootIn", 9, Kind.NUMBER,
			BookDataEntity::getHomeBoxShootIn, BookDataEntity::getAwayBoxShootIn),

	/** ボックス外シュート */
	BOX_SHOOT_OUT("boxShootOut", 10, Kind.NUMBER,
			BookDataEntity::getHomeBoxShootOut, BookDataEntity::getAwayBoxShootOut),

	/** ゴールポスト */
	GOAL_POST("goalPost", 11, Kind.NUMBER,
			BookDataEntity::getHomeGoalPost, BookDataEntity::getAwayGoalPost),

	/** ヘディングゴール */
	GOAL_HEAD("goalHead", 12, Kind.NUMBER,
			BookDataEntity::getHomeGoalHead, BookDataEntity::getAwayGoalHead),

	/** キーパーセーブ */
	KEEPER_SAVE("keeperSave", 13, Kind.NUMBER,
			BookDataEntity::getHomeKeeperSave, BookDataEntity::getAwayKeeperSave),

	/** フリーキック */
	FREE_KICK("freeKick", 14, Kind.NUMBER,
			BookDataEntity::getHomeFreeKick, BookDataEntity::getAwayFreeKick),

	/** オフサイド */
	OFF_SIDE("offSide", 15, Kind.NUMBER,
			BookDataEntity::getHomeOffSide, BookDataEntity::getAwayOffSide),

	/** ファウル */
	FOUL("foul", 16, Kind.NUMBER,
			BookDataEntity::getHomeFoul, BookDataEntity::getAwayFoul),

	/** イエローカード */
	YELLOW_CARD("yellowCard", 17, Kind.NUMBER,
			BookDataEntity::getHomeYellowCard, BookDataEntity::getAwayYellowCard),

	/** レッドカード */
	RED_CARD("redCard", 18, Kind.NUMBER,
			BookDataEntity::getHomeRedCard, BookDataEntity::getAwayRedCard),

	/** スローイン */
	SLOW_IN("slowIn", 19, Kind.NUMBER,
			BookDataEntity::getHomeSlowIn, BookDataEntity::getAwaySlowIn),

	/** ボックスタッチ */
	BOX_TOUCH("boxTouch", 20, Kind.NUMBER,
			BookDataEntity::getHomeBoxTouch, BookDataEntity::getAwayBoxTouch),

	/** パス成功率（%） */
	PASS_COUNT_RATE("passCount_rate", 21, Kind.TRI_RATE,
			BookDataEntity::getHomePassCount, BookDataEntity::getAwayPassCount),

	/** パス成功数 */
	PASS_COUNT_SUCCESS("passCount_success", 22, Kind.TRI_SUCCESS,
			BookDataEntity::getHomePassCount, BookDataEntity::getAwayPassCount),

	/** パス試行数 */
	PASS_COUNT_TRY("passCount_try", 23, Kind.TRI_TRY,
			BookDataEntity::getHomePassCount, BookDataEntity::getAwayPassCount),

	/** ロングパス成功率（%） */
	LONG_PASS_COUNT_RATE("longPassCount_rate", 24, Kind.TRI_RATE,
			BookDataEntity::getHomeLongPassCount, BookDataEntity::getAwayLongPassCount),

	/** ロングパス成功数 */
	LONG_PASS_COUNT_SUCCESS("longPassCount_success", 25, Kind.TRI_SUCCESS,
			BookDataEntity::getHomeLongPassCount, BookDataEntity::getAwayLongPassCount),

	/** ロングパス試行数 */
	LONG_PASS_COUNT_TRY("longPassCount_try", 26, Kind.TRI_TRY,
			BookDataEntity::getHomeLongPassCount, BookDataEntity::getAwayLongPassCount),

	/** ファイナルサードパス成功率（%） */
	FINAL_THIRD_PASS_COUNT_RATE("finalThirdPassCount_rate", 27, Kind.TRI_RATE,
			BookDataEntity::getHomeFinalThirdPassCount, BookDataEntity::getAwayFinalThirdPassCount),

	/** ファイナルサードパス成功数 */
	FINAL_THIRD_PASS_COUNT_SUCCESS("finalThirdPassCount_success", 28, Kind.TRI_SUCCESS,
			BookDataEntity::getHomeFinalThirdPassCount, BookDataEntity::getAwayFinalThirdPassCount),

	/** ファイナルサードパス試行数 */
	FINAL_THIRD_PASS_COUNT_TRY("finalThirdPassCount_try", 29, Kind.TRI_TRY,
			BookDataEntity::getHomeFinalThirdPassCount, BookDataEntity::getAwayFinalThirdPassCount),

	/** クロス成功率（%） */
	CROSS_COUNT_RATE("crossCount_rate", 30, Kind.TRI_RATE,
			BookDataEntity::getHomeCrossCount, BookDataEntity::getAwayCrossCount),

	/** クロス成功数 */
	CROSS_COUNT_SUCCESS("crossCount_success", 31, Kind.TRI_SUCCESS,
			BookDataEntity::getHomeCrossCount, BookDataEntity::getAwayCrossCount),

	/** クロス試行数 */
	CROSS_COUNT_TRY("crossCount_try", 32, Kind.TRI_TRY,
			BookDataEntity::getHomeCrossCount, BookDataEntity::getAwayCrossCount),

	/** タックル成功率（%） */
	TACKLE_COUNT_RATE("tackleCount_rate", 33, Kind.TRI_RATE,
			BookDataEntity::getHomeTackleCount, BookDataEntity::getAwayTackleCount),

	/** タックル成功数 */
	TACKLE_COUNT_SUCCESS("tackleCount_success", 34, Kind.TRI_SUCCESS,
			BookDataEntity::getHomeTackleCount, BookDataEntity::getAwayTackleCount),

	/** タックル試行数 */
	TACKLE_COUNT_TRY("tackleCount_try", 35, Kind.TRI_TRY,
			BookDataEntity::getHomeTackleCount, BookDataEntity::getAwayTackleCount),

	/** クリア数 */
	CLEAR_COUNT("clearCount", 36, Kind.NUMBER,
			BookDataEntity::getHomeClearCount, BookDataEntity::getAwayClearCount),

	/** デュエル勝利数 */
	DUEL_COUNT("duelCount", 37, Kind.NUMBER,
			BookDataEntity::getHomeDuelCount, BookDataEntity::getAwayDuelCount),

	/** インターセプト数 */
	INTERCEPT_COUNT("interceptCount", 38, Kind.NUMBER,
			BookDataEntity::getHomeInterceptCount, BookDataEntity::getAwayInterceptCount);

	/** 値の種類 */
	public enum Kind {
		/** 数値 */
		NUMBER,
		/** "X% (成功/試行)" の成功率 */
		TRI_RATE,
		/** "X% (成功/試行)" の成功数 */
		TRI_SUCCESS,
		/** "X% (成功/試行)" の試行数 */
		TRI_TRY
	}

	/** "X% (成功/試行)" 形式 */
	private static final Pattern TRI_PATTERN =
			Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*%\\s*\\(\\s*(\\d+)\\s*/\\s*(\\d+)\\s*\\)\\s*$");

	/** "X%" だけの形式 */
	private static final Pattern PERCENT_ONLY = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*%\\s*$");

	/** 数値（末尾の % は任意） */
	private static final Pattern NUMBER_PATTERN = Pattern.compile("^\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*%?\\s*$");

	private final String featureName;
	private final int order;
	private final Kind kind;
	private final Function<BookDataEntity, String> homeGetter;
	private final Function<BookDataEntity, String> awayGetter;

	ScoreBasedFeature(String featureName, int order, Kind kind,
			Function<BookDataEntity, String> homeGetter, Function<BookDataEntity, String> awayGetter) {
		this.featureName = featureName;
		this.order = order;
		this.kind = kind;
		this.homeGetter = homeGetter;
		this.awayGetter = awayGetter;
	}

	/** DB の feature 列の値 */
	public String getFeatureName() {
		return this.featureName;
	}

	/** 並び順 */
	public int getOrder() {
		return this.order;
	}

	/**
	 * 行からホーム側 / アウェー側の値を数値で取り出す（読めなければ null）。
	 *
	 * @param row 行
	 * @param home true: ホーム側 / false: アウェー側
	 */
	public BigDecimal value(BookDataEntity row, boolean home) {
		String raw = home ? this.homeGetter.apply(row) : this.awayGetter.apply(row);
		return parse(raw);
	}

	/**
	 * 数値に変換する（読めなければ null）。
	 */
	BigDecimal parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		Matcher m;
		switch (this.kind) {
		case NUMBER:
			m = NUMBER_PATTERN.matcher(raw);
			return m.matches() ? new BigDecimal(m.group(1)) : null;
		case TRI_RATE:
			m = TRI_PATTERN.matcher(raw);
			if (m.matches()) {
				return new BigDecimal(m.group(1));
			}
			m = PERCENT_ONLY.matcher(raw);
			return m.matches() ? new BigDecimal(m.group(1)) : null;
		case TRI_SUCCESS:
			m = TRI_PATTERN.matcher(raw);
			return m.matches() ? new BigDecimal(m.group(2)) : null;
		case TRI_TRY:
			m = TRI_PATTERN.matcher(raw);
			return m.matches() ? new BigDecimal(m.group(3)) : null;
		default:
			return null;
		}
	}
}