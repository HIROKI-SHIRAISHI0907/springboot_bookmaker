package dev.application.analyze.bm_m039;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * <p>BM_M039 試合中の勢い・モメンタム統計を算出するサービスクラスです。</p>
 *
 * <ul>
 *   <li>入力: 国×リーグ×カード単位の {@link BookDataEntity} 群</li>
 *   <li>処理: 1試合の時系列データを時間順に走査し、
 *       ホーム・アウェーそれぞれの {@link MatchTeamMomentumStatsEntity} を生成</li>
 *   <li>出力: {@link MatchTeamMomentumStatsWriter} を介して登録</li>
 * </ul>
 *
 * <p>
 * 本クラスでは、各スナップショット時点における直近5分・10分の差分をもとに、
 * 勢い・モメンタムを定量化します。
 * </p>
 *
 * <p>
 * 具体的には以下を算出します。
 * </p>
 * <ul>
 *   <li>直近窓のシュート差</li>
 *   <li>直近窓の枠内シュート差</li>
 *   <li>直近窓のボックスタッチ差</li>
 *   <li>直近窓のコーナー差</li>
 *   <li>直近窓の前進量差（ファイナルサードパス差）</li>
 *   <li>得点後の攻撃反応値</li>
 *   <li>失点後の攻撃反応値</li>
 *   <li>モメンタム指数</li>
 *   <li>モメンタム傾向</li>
 * </ul>
 *
 * @author shiraishitoshio
 * @since 1.0
 */
@Component
public class MatchTeamMomentumStatsStat implements AnalyzeEntityIF {

	/** プロジェクト名（ログ用） */
	private static final String PROJECT_NAME = MatchTeamMomentumStatsStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名（ログ用） */
	private static final String CLASS_NAME = MatchTeamMomentumStatsStat.class.getName();

	/** 実行モード（ログ用） */
	private static final String EXEC_MODE = "BM_M039_MATCH_TEAM_MOMENTUM_STATS";

	/** 記録時間フォーマット */
	private static final DateTimeFormatter RECORD_TIME_FORMATTER =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX");

	/** 既定の集計窓（分） */
	private static final List<Integer> WINDOW_MINUTES_LIST = List.of(5, 10);

	/** モメンタム傾向判定のしきい値 */
	private static final BigDecimal MOMENTUM_TREND_EPSILON = new BigDecimal("0.050000");

	@Autowired
	private MatchTeamMomentumStatsWriter matchTeamMomentumStatsWriter;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 全ての国・リーグ・カードを走査し、
	 * 各時点・各窓幅のモメンタム統計を算出して登録します。
	 * </p>
	 *
	 * @param entities 国×リーグごとにまとめられた時系列データ
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		manageLoggerComponent.init(EXEC_MODE, null);
		manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		try {
			for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
				String[] dataCategory = ExecuteMainUtil.splitLeagueInfo(entry.getKey());
				String country = dataCategory != null && dataCategory.length > 0 ? dataCategory[0] : null;
				String league = dataCategory != null && dataCategory.length > 1 ? dataCategory[1] : null;

				Map<String, List<BookDataEntity>> entrySub = entry.getValue();
				for (List<BookDataEntity> entityList : entrySub.values()) {
					if (entityList == null || entityList.isEmpty()) {
						continue;
					}
					decideBasedMain(entityList, country, league);
				}
			}
		} finally {
			manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			manageLoggerComponent.clear();
		}
	}

	/**
	 * 1試合分の時系列データからモメンタム統計を算出し、
	 * 各時点・各窓幅についてホーム・アウェーの2件を登録します。
	 *
	 * @param entities 1試合分の時系列データ
	 * @param country 国
	 * @param league リーグ
	 */
	private void decideBasedMain(List<BookDataEntity> entities, String country, String league) {
		final String METHOD_NAME = "decideBasedMain";

		try {
			if (entities == null || entities.isEmpty()) {
				return;
			}

			manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, entities.get(0).getFilePath());

			List<BookDataEntity> sortedEntities = sortAndDeduplicateEntitiesByTime(entities);
			if (sortedEntities.isEmpty()) {
				return;
			}

			for (int index = 0; index < sortedEntities.size(); index++) {
				for (Integer windowMinutes : WINDOW_MINUTES_LIST) {
					MatchTeamMomentumStatsEntity homeEntity =
							createMomentumEntity(sortedEntities, index, country, league, windowMinutes, true);
					MatchTeamMomentumStatsEntity awayEntity =
							createMomentumEntity(sortedEntities, index, country, league, windowMinutes, false);

					matchTeamMomentumStatsWriter.insert(homeEntity);
					matchTeamMomentumStatsWriter.insert(awayEntity);
				}
			}

		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
					"モメンタム統計算出中に例外が発生しました。");
		}
	}

	/**
	 * 指定時点・指定窓幅・指定視点のモメンタム統計Entityを生成します。
	 *
	 * @param entities 整列済み時系列データ
	 * @param currentIndex 現在インデックス
	 * @param country 国
	 * @param league リーグ
	 * @param windowMinutes 集計窓（分）
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return モメンタム統計Entity
	 */
	private MatchTeamMomentumStatsEntity createMomentumEntity(
			List<BookDataEntity> entities,
			int currentIndex,
			String country,
			String league,
			Integer windowMinutes,
			boolean homePerspective) {

		BookDataEntity current = entities.get(currentIndex);
		int currentSeconds = resolveAsOfSeconds(current);
		int windowSeconds = windowMinutes * 60;
		int windowStartSeconds = Math.max(0, currentSeconds - windowSeconds);

		BookDataEntity baseline = findLatestEntityAtOrBefore(entities, windowStartSeconds);

		int currentTeamShots = getTeamShots(current, homePerspective);
		int currentOpponentShots = getOpponentShots(current, homePerspective);
		int baseTeamShots = getTeamShots(baseline, homePerspective);
		int baseOpponentShots = getOpponentShots(baseline, homePerspective);

		int currentTeamShotsOnTarget = getTeamShotsOnTarget(current, homePerspective);
		int currentOpponentShotsOnTarget = getOpponentShotsOnTarget(current, homePerspective);
		int baseTeamShotsOnTarget = getTeamShotsOnTarget(baseline, homePerspective);
		int baseOpponentShotsOnTarget = getOpponentShotsOnTarget(baseline, homePerspective);

		int currentTeamBoxTouches = getTeamBoxTouches(current, homePerspective);
		int currentOpponentBoxTouches = getOpponentBoxTouches(current, homePerspective);
		int baseTeamBoxTouches = getTeamBoxTouches(baseline, homePerspective);
		int baseOpponentBoxTouches = getOpponentBoxTouches(baseline, homePerspective);

		int currentTeamCorners = getTeamCorners(current, homePerspective);
		int currentOpponentCorners = getOpponentCorners(current, homePerspective);
		int baseTeamCorners = getTeamCorners(baseline, homePerspective);
		int baseOpponentCorners = getOpponentCorners(baseline, homePerspective);

		int currentTeamFinalThirdPasses = getTeamFinalThirdPasses(current, homePerspective);
		int currentOpponentFinalThirdPasses = getOpponentFinalThirdPasses(current, homePerspective);
		int baseTeamFinalThirdPasses = getTeamFinalThirdPasses(baseline, homePerspective);
		int baseOpponentFinalThirdPasses = getOpponentFinalThirdPasses(baseline, homePerspective);

		int recentTeamShots = calcDelta(currentTeamShots, baseTeamShots);
		int recentOpponentShots = calcDelta(currentOpponentShots, baseOpponentShots);
		int recentTeamShotsOnTarget = calcDelta(currentTeamShotsOnTarget, baseTeamShotsOnTarget);
		int recentOpponentShotsOnTarget = calcDelta(currentOpponentShotsOnTarget, baseOpponentShotsOnTarget);
		int recentTeamBoxTouches = calcDelta(currentTeamBoxTouches, baseTeamBoxTouches);
		int recentOpponentBoxTouches = calcDelta(currentOpponentBoxTouches, baseOpponentBoxTouches);
		int recentTeamCorners = calcDelta(currentTeamCorners, baseTeamCorners);
		int recentOpponentCorners = calcDelta(currentOpponentCorners, baseOpponentCorners);
		int recentTeamFinalThirdPasses = calcDelta(currentTeamFinalThirdPasses, baseTeamFinalThirdPasses);
		int recentOpponentFinalThirdPasses = calcDelta(currentOpponentFinalThirdPasses, baseOpponentFinalThirdPasses);

		int recentShotsDiff = recentTeamShots - recentOpponentShots;
		int recentShotsOnTargetDiff = recentTeamShotsOnTarget - recentOpponentShotsOnTarget;
		int recentBoxTouchesDiff = recentTeamBoxTouches - recentOpponentBoxTouches;
		int recentCornersDiff = recentTeamCorners - recentOpponentCorners;
		BigDecimal recentProgressionDiff = BigDecimal.valueOf(recentTeamFinalThirdPasses - recentOpponentFinalThirdPasses);

		BigDecimal postGoalAttackResponse =
				calcPostGoalAttackResponse(entities, currentIndex, windowMinutes, homePerspective);
		BigDecimal postConcededAttackResponse =
				calcPostConcededAttackResponse(entities, currentIndex, windowMinutes, homePerspective);

		BigDecimal momentumIndex = calcMomentumIndex(
				recentShotsDiff,
				recentShotsOnTargetDiff,
				recentBoxTouchesDiff,
				recentCornersDiff,
				recentProgressionDiff);

		BigDecimal previousMomentumIndex =
				currentIndex > 0 ? calcMomentumIndexAtIndex(entities, currentIndex - 1, windowMinutes, homePerspective) : null;

		String momentumTrend = resolveMomentumTrend(momentumIndex, previousMomentumIndex);

		MatchTeamMomentumStatsEntity entity = new MatchTeamMomentumStatsEntity();

		setCommonContext(
				entity,
				current.getMatchId(),
				resolveSeason(current),
				country,
				league,
				league,
				resolveTeamId(homePerspective ? current.getHomeTeamName() : current.getAwayTeamName()),
				homePerspective ? current.getHomeTeamName() : current.getAwayTeamName(),
				resolveTeamId(homePerspective ? current.getAwayTeamName() : current.getHomeTeamName()),
				homePerspective ? current.getAwayTeamName() : current.getHomeTeamName());

		entity.setAsOfSeconds(currentSeconds);
		entity.setWindowMinutes(windowMinutes);
		entity.setRecentShotsDiff(recentShotsDiff);
		entity.setRecentShotsOnTargetDiff(recentShotsOnTargetDiff);
		entity.setRecentBoxTouchesDiff(recentBoxTouchesDiff);
		entity.setRecentCornersDiff(recentCornersDiff);
		entity.setRecentProgressionDiff(recentProgressionDiff);
		entity.setPostGoalAttackResponse(postGoalAttackResponse);
		entity.setPostConcededAttackResponse(postConcededAttackResponse);
		entity.setMomentumIndex(momentumIndex);
		entity.setMomentumTrend(momentumTrend);
		entity.setCalculatedAt(LocalDateTime.now());

		return entity;
	}

	/**
	 * 指定時点のモメンタム指数のみを計算します。
	 *
	 * @param entities 整列済み時系列データ
	 * @param currentIndex 現在インデックス
	 * @param windowMinutes 集計窓（分）
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return モメンタム指数
	 */
	private BigDecimal calcMomentumIndexAtIndex(
			List<BookDataEntity> entities,
			int currentIndex,
			Integer windowMinutes,
			boolean homePerspective) {

		BookDataEntity current = entities.get(currentIndex);
		int currentSeconds = resolveAsOfSeconds(current);
		int windowStartSeconds = Math.max(0, currentSeconds - (windowMinutes * 60));
		BookDataEntity baseline = findLatestEntityAtOrBefore(entities, windowStartSeconds);

		int recentShotsDiff =
				calcDelta(getTeamShots(current, homePerspective), getTeamShots(baseline, homePerspective))
				- calcDelta(getOpponentShots(current, homePerspective), getOpponentShots(baseline, homePerspective));

		int recentShotsOnTargetDiff =
				calcDelta(getTeamShotsOnTarget(current, homePerspective), getTeamShotsOnTarget(baseline, homePerspective))
				- calcDelta(getOpponentShotsOnTarget(current, homePerspective), getOpponentShotsOnTarget(baseline, homePerspective));

		int recentBoxTouchesDiff =
				calcDelta(getTeamBoxTouches(current, homePerspective), getTeamBoxTouches(baseline, homePerspective))
				- calcDelta(getOpponentBoxTouches(current, homePerspective), getOpponentBoxTouches(baseline, homePerspective));

		int recentCornersDiff =
				calcDelta(getTeamCorners(current, homePerspective), getTeamCorners(baseline, homePerspective))
				- calcDelta(getOpponentCorners(current, homePerspective), getOpponentCorners(baseline, homePerspective));

		BigDecimal recentProgressionDiff = BigDecimal.valueOf(
				calcDelta(getTeamFinalThirdPasses(current, homePerspective), getTeamFinalThirdPasses(baseline, homePerspective))
				- calcDelta(getOpponentFinalThirdPasses(current, homePerspective), getOpponentFinalThirdPasses(baseline, homePerspective)));

		return calcMomentumIndex(
				recentShotsDiff,
				recentShotsOnTargetDiff,
				recentBoxTouchesDiff,
				recentCornersDiff,
				recentProgressionDiff);
	}

	/**
	 * 得点後の攻撃反応値を算出します。
	 *
	 * <p>
	 * 直近窓内に自チーム得点イベントが存在する場合、
	 * その得点時点から現在時点までの攻撃増分を重み付きで返します。
	 * </p>
	 *
	 * @param entities 整列済み時系列データ
	 * @param currentIndex 現在インデックス
	 * @param windowMinutes 集計窓（分）
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return 得点後の攻撃反応値。対象イベントがなければ null
	 */
	private BigDecimal calcPostGoalAttackResponse(
			List<BookDataEntity> entities,
			int currentIndex,
			Integer windowMinutes,
			boolean homePerspective) {

		Integer goalEventIndex = findLatestGoalEventIndexWithinWindow(entities, currentIndex, windowMinutes, homePerspective, true);
		if (goalEventIndex == null) {
			return null;
		}

		return calcAttackResponseBetween(entities.get(goalEventIndex), entities.get(currentIndex), homePerspective);
	}

	/**
	 * 失点後の攻撃反応値を算出します。
	 *
	 * <p>
	 * 直近窓内に相手得点イベントが存在する場合、
	 * その失点時点から現在時点までの攻撃増分を重み付きで返します。
	 * </p>
	 *
	 * @param entities 整列済み時系列データ
	 * @param currentIndex 現在インデックス
	 * @param windowMinutes 集計窓（分）
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return 失点後の攻撃反応値。対象イベントがなければ null
	 */
	private BigDecimal calcPostConcededAttackResponse(
			List<BookDataEntity> entities,
			int currentIndex,
			Integer windowMinutes,
			boolean homePerspective) {

		Integer goalEventIndex = findLatestGoalEventIndexWithinWindow(entities, currentIndex, windowMinutes, homePerspective, false);
		if (goalEventIndex == null) {
			return null;
		}

		return calcAttackResponseBetween(entities.get(goalEventIndex), entities.get(currentIndex), homePerspective);
	}

	/**
	 * 指定窓内の最新得点イベントインデックスを返します。
	 *
	 * @param entities 整列済み時系列データ
	 * @param currentIndex 現在インデックス
	 * @param windowMinutes 集計窓（分）
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @param teamGoal true: 自チーム得点を探す / false: 相手得点を探す
	 * @return 該当イベントインデックス。存在しない場合は null
	 */
	private Integer findLatestGoalEventIndexWithinWindow(
			List<BookDataEntity> entities,
			int currentIndex,
			Integer windowMinutes,
			boolean homePerspective,
			boolean teamGoal) {

		int currentSeconds = resolveAsOfSeconds(entities.get(currentIndex));
		int windowSeconds = windowMinutes * 60;

		for (int i = currentIndex; i >= 1; i--) {
			BookDataEntity prev = entities.get(i - 1);
			BookDataEntity curr = entities.get(i);

			int currSeconds = resolveAsOfSeconds(curr);
			if ((currentSeconds - currSeconds) > windowSeconds) {
				break;
			}

			int prevTeamScore = getTeamScore(prev, homePerspective);
			int currTeamScore = getTeamScore(curr, homePerspective);
			int prevOpponentScore = getOpponentScore(prev, homePerspective);
			int currOpponentScore = getOpponentScore(curr, homePerspective);

			boolean scoredByTeam = currTeamScore > prevTeamScore;
			boolean scoredByOpponent = currOpponentScore > prevOpponentScore;

			if (teamGoal && scoredByTeam) {
				return i;
			}
			if (!teamGoal && scoredByOpponent) {
				return i;
			}
		}

		return null;
	}

	/**
	 * 2時点間の攻撃反応値を算出します。
	 *
	 * <p>
	 * 以下の重み付き合計を返します。
	 * </p>
	 * <pre>
	 * シュート増分 × 1.0
	 * + 枠内シュート増分 × 1.5
	 * + ボックスタッチ増分 × 0.2
	 * + コーナー増分 × 0.3
	 * + ファイナルサードパス増分 × 0.05
	 * </pre>
	 *
	 * @param base 基準時点
	 * @param current 現在時点
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return 攻撃反応値
	 */
	private BigDecimal calcAttackResponseBetween(
			BookDataEntity base,
			BookDataEntity current,
			boolean homePerspective) {

		int deltaShots = calcDelta(getTeamShots(current, homePerspective), getTeamShots(base, homePerspective));
		int deltaShotsOnTarget = calcDelta(getTeamShotsOnTarget(current, homePerspective), getTeamShotsOnTarget(base, homePerspective));
		int deltaBoxTouches = calcDelta(getTeamBoxTouches(current, homePerspective), getTeamBoxTouches(base, homePerspective));
		int deltaCorners = calcDelta(getTeamCorners(current, homePerspective), getTeamCorners(base, homePerspective));
		int deltaFinalThirdPasses = calcDelta(getTeamFinalThirdPasses(current, homePerspective), getTeamFinalThirdPasses(base, homePerspective));

		BigDecimal result = BigDecimal.ZERO;
		result = result.add(BigDecimal.valueOf(deltaShots));
		result = result.add(BigDecimal.valueOf(deltaShotsOnTarget).multiply(new BigDecimal("1.5")));
		result = result.add(BigDecimal.valueOf(deltaBoxTouches).multiply(new BigDecimal("0.2")));
		result = result.add(BigDecimal.valueOf(deltaCorners).multiply(new BigDecimal("0.3")));
		result = result.add(BigDecimal.valueOf(deltaFinalThirdPasses).multiply(new BigDecimal("0.05")));

		return result.setScale(6, RoundingMode.HALF_UP);
	}

	/**
	 * モメンタム指数を算出します。
	 *
	 * <p>
	 * 以下の重み付き合計を返します。
	 * </p>
	 * <pre>
	 * recentShotsDiff × 0.35
	 * + recentShotsOnTargetDiff × 0.35
	 * + recentBoxTouchesDiff × 0.15
	 * + recentCornersDiff × 0.10
	 * + (recentProgressionDiff / 10) × 0.05
	 * </pre>
	 *
	 * @param recentShotsDiff 直近窓のシュート差
	 * @param recentShotsOnTargetDiff 直近窓の枠内シュート差
	 * @param recentBoxTouchesDiff 直近窓のボックスタッチ差
	 * @param recentCornersDiff 直近窓のコーナー差
	 * @param recentProgressionDiff 直近窓の前進量差
	 * @return モメンタム指数
	 */
	private BigDecimal calcMomentumIndex(
			int recentShotsDiff,
			int recentShotsOnTargetDiff,
			int recentBoxTouchesDiff,
			int recentCornersDiff,
			BigDecimal recentProgressionDiff) {

		BigDecimal result = BigDecimal.ZERO;
		result = result.add(BigDecimal.valueOf(recentShotsDiff).multiply(new BigDecimal("0.35")));
		result = result.add(BigDecimal.valueOf(recentShotsOnTargetDiff).multiply(new BigDecimal("0.35")));
		result = result.add(BigDecimal.valueOf(recentBoxTouchesDiff).multiply(new BigDecimal("0.15")));
		result = result.add(BigDecimal.valueOf(recentCornersDiff).multiply(new BigDecimal("0.10")));
		result = result.add(nvl(recentProgressionDiff)
				.divide(new BigDecimal("10"), 6, RoundingMode.HALF_UP)
				.multiply(new BigDecimal("0.05")));

		return result.setScale(6, RoundingMode.HALF_UP);
	}

	/**
	 * モメンタム傾向を判定します。
	 *
	 * @param currentMomentum 現在モメンタム
	 * @param previousMomentum 直前モメンタム
	 * @return RISING / FALLING / STABLE
	 */
	private String resolveMomentumTrend(BigDecimal currentMomentum, BigDecimal previousMomentum) {
		if (currentMomentum == null || previousMomentum == null) {
			return "STABLE";
		}

		BigDecimal diff = currentMomentum.subtract(previousMomentum);

		if (diff.compareTo(MOMENTUM_TREND_EPSILON) > 0) {
			return "RISING";
		}
		if (diff.compareTo(MOMENTUM_TREND_EPSILON.negate()) < 0) {
			return "FALLING";
		}
		return "STABLE";
	}

	/**
	 * 時系列データを経過秒・通番順で整列し、同一秒の重複を後勝ちで解消します。
	 *
	 * @param entities 元データ
	 * @return 整列・重複解消済みデータ
	 */
	private List<BookDataEntity> sortAndDeduplicateEntitiesByTime(List<BookDataEntity> entities) {
		List<BookDataEntity> sorted = new ArrayList<>(entities);
		sorted.sort(
				Comparator.comparingInt((BookDataEntity e) -> resolveAsOfSeconds(e))
						.thenComparingInt(e -> nvl(parseInteger(e.getSeq()))));

		List<BookDataEntity> deduplicated = new ArrayList<>();
		for (BookDataEntity entity : sorted) {
			if (!deduplicated.isEmpty()) {
				BookDataEntity last = deduplicated.get(deduplicated.size() - 1);
				if (resolveAsOfSeconds(last) == resolveAsOfSeconds(entity)) {
					deduplicated.set(deduplicated.size() - 1, entity);
					continue;
				}
			}
			deduplicated.add(entity);
		}
		return deduplicated;
	}

	/**
	 * 指定秒以下で最も新しいスナップショットを返します。
	 *
	 * @param entities 整列済み時系列データ
	 * @param seconds 上限秒
	 * @return 条件に合うスナップショット。存在しない場合は null
	 */
	private BookDataEntity findLatestEntityAtOrBefore(List<BookDataEntity> entities, int seconds) {
		BookDataEntity result = null;
		for (BookDataEntity entity : entities) {
			int current = resolveAsOfSeconds(entity);
			if (current <= seconds) {
				result = entity;
			} else {
				break;
			}
		}
		return result;
	}

	/**
	 * 共通文脈項目を設定します。
	 *
	 * @param entity 対象Entity
	 * @param matchId 試合ID
	 * @param season シーズン
	 * @param country 国
	 * @param leagueId リーグID
	 * @param leagueName リーグ名
	 * @param teamId チームID
	 * @param teamName チーム名
	 * @param opponentTeamId 対戦相手チームID
	 * @param opponentTeamName 対戦相手チーム名
	 */
	private void setCommonContext(
			MatchTeamMomentumStatsEntity entity,
			String matchId,
			String season,
			String country,
			String leagueId,
			String leagueName,
			String teamId,
			String teamName,
			String opponentTeamId,
			String opponentTeamName) {

		entity.setMatchId(trimToNull(matchId));
		entity.setSeason(trimToNull(season));
		entity.setCountry(trimToNull(country));
		entity.setLeagueId(trimToNull(leagueId));
		entity.setLeagueName(trimToNull(leagueName));
		entity.setTeamId(trimToNull(teamId));
		entity.setTeamName(trimToNull(teamName));
		entity.setOpponentTeamId(trimToNull(opponentTeamId));
		entity.setOpponentTeamName(trimToNull(opponentTeamName));
	}

	/**
	 * シーズン文字列を解決します。
	 *
	 * @param book 元データ
	 * @return シーズン
	 */
	private String resolveSeason(BookDataEntity book) {
		LocalDateTime record = parseLocalDateTime(book.getRecordTime());
		if (record != null) {
			return String.valueOf(record.getYear());
		}
		return null;
	}

	/**
	 * 暫定チームIDを解決します。
	 *
	 * @param teamName チーム名
	 * @return 暫定チームID
	 */
	private String resolveTeamId(String teamName) {
		return trimToNull(teamName);
	}

	/**
	 * 試合経過秒を解決します。
	 *
	 * @param entity 元データ
	 * @return 経過秒
	 */
	private int resolveAsOfSeconds(BookDataEntity entity) {
		Integer timeSortSeconds = parseInteger(entity.getTimeSortSeconds());
		if (timeSortSeconds != null) {
			return timeSortSeconds;
		}
		return parseMatchTimeToSeconds(entity.getTime());
	}

	/**
	 * 対象視点のシュート数を返します。
	 *
	 * @param entity 元データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return シュート数
	 */
	private int getTeamShots(BookDataEntity entity, boolean homePerspective) {
		if (entity == null) {
			return 0;
		}
		return homePerspective ? nvl(parseInteger(entity.getHomeShootAll())) : nvl(parseInteger(entity.getAwayShootAll()));
	}

	/**
	 * 対象視点の相手シュート数を返します。
	 *
	 * @param entity 元データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return 相手シュート数
	 */
	private int getOpponentShots(BookDataEntity entity, boolean homePerspective) {
		if (entity == null) {
			return 0;
		}
		return homePerspective ? nvl(parseInteger(entity.getAwayShootAll())) : nvl(parseInteger(entity.getHomeShootAll()));
	}

	/**
	 * 対象視点の枠内シュート数を返します。
	 *
	 * @param entity 元データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return 枠内シュート数
	 */
	private int getTeamShotsOnTarget(BookDataEntity entity, boolean homePerspective) {
		if (entity == null) {
			return 0;
		}
		return homePerspective ? nvl(parseInteger(entity.getHomeShootIn())) : nvl(parseInteger(entity.getAwayShootIn()));
	}

	/**
	 * 対象視点の相手枠内シュート数を返します。
	 *
	 * @param entity 元データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return 相手枠内シュート数
	 */
	private int getOpponentShotsOnTarget(BookDataEntity entity, boolean homePerspective) {
		if (entity == null) {
			return 0;
		}
		return homePerspective ? nvl(parseInteger(entity.getAwayShootIn())) : nvl(parseInteger(entity.getHomeShootIn()));
	}

	/**
	 * 対象視点のボックスタッチ数を返します。
	 *
	 * @param entity 元データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return ボックスタッチ数
	 */
	private int getTeamBoxTouches(BookDataEntity entity, boolean homePerspective) {
		if (entity == null) {
			return 0;
		}
		return homePerspective ? nvl(parseInteger(entity.getHomeBoxTouch())) : nvl(parseInteger(entity.getAwayBoxTouch()));
	}

	/**
	 * 対象視点の相手ボックスタッチ数を返します。
	 *
	 * @param entity 元データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return 相手ボックスタッチ数
	 */
	private int getOpponentBoxTouches(BookDataEntity entity, boolean homePerspective) {
		if (entity == null) {
			return 0;
		}
		return homePerspective ? nvl(parseInteger(entity.getAwayBoxTouch())) : nvl(parseInteger(entity.getHomeBoxTouch()));
	}

	/**
	 * 対象視点のコーナー数を返します。
	 *
	 * @param entity 元データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return コーナー数
	 */
	private int getTeamCorners(BookDataEntity entity, boolean homePerspective) {
		if (entity == null) {
			return 0;
		}
		return homePerspective ? nvl(parseInteger(entity.getHomeCornerKick())) : nvl(parseInteger(entity.getAwayCornerKick()));
	}

	/**
	 * 対象視点の相手コーナー数を返します。
	 *
	 * @param entity 元データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return 相手コーナー数
	 */
	private int getOpponentCorners(BookDataEntity entity, boolean homePerspective) {
		if (entity == null) {
			return 0;
		}
		return homePerspective ? nvl(parseInteger(entity.getAwayCornerKick())) : nvl(parseInteger(entity.getHomeCornerKick()));
	}

	/**
	 * 対象視点のファイナルサードパス数を返します。
	 *
	 * @param entity 元データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return ファイナルサードパス数
	 */
	private int getTeamFinalThirdPasses(BookDataEntity entity, boolean homePerspective) {
		if (entity == null) {
			return 0;
		}
		return homePerspective ? nvl(parseInteger(entity.getHomeFinalThirdPassCount())) : nvl(parseInteger(entity.getAwayFinalThirdPassCount()));
	}

	/**
	 * 対象視点の相手ファイナルサードパス数を返します。
	 *
	 * @param entity 元データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return 相手ファイナルサードパス数
	 */
	private int getOpponentFinalThirdPasses(BookDataEntity entity, boolean homePerspective) {
		if (entity == null) {
			return 0;
		}
		return homePerspective ? nvl(parseInteger(entity.getAwayFinalThirdPassCount())) : nvl(parseInteger(entity.getHomeFinalThirdPassCount()));
	}

	/**
	 * 対象視点の得点数を返します。
	 *
	 * @param entity 元データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return 得点数
	 */
	private int getTeamScore(BookDataEntity entity, boolean homePerspective) {
		if (entity == null) {
			return 0;
		}
		return homePerspective ? nvl(parseInteger(entity.getHomeScore())) : nvl(parseInteger(entity.getAwayScore()));
	}

	/**
	 * 対象視点の相手得点数を返します。
	 *
	 * @param entity 元データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return 相手得点数
	 */
	private int getOpponentScore(BookDataEntity entity, boolean homePerspective) {
		if (entity == null) {
			return 0;
		}
		return homePerspective ? nvl(parseInteger(entity.getAwayScore())) : nvl(parseInteger(entity.getHomeScore()));
	}

	/**
	 * 差分を算出します。
	 *
	 * @param current 現在値
	 * @param previous 直前値
	 * @return 差分
	 */
	private int calcDelta(int current, int previous) {
		return Math.max(0, current - previous);
	}

	/**
	 * 記録時間文字列を {@link LocalDateTime} に変換します。
	 *
	 * @param value 記録時間
	 * @return 変換結果。変換不能時は null
	 */
	private LocalDateTime parseLocalDateTime(String value) {
		String normalized = trimToNull(value);
		if (normalized == null) {
			return null;
		}
		try {
			return OffsetDateTime.parse(normalized, RECORD_TIME_FORMATTER).toLocalDateTime();
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * 文字列を整数へ変換します。
	 *
	 * @param value 対象文字列
	 * @return 整数。変換不能時は null
	 */
	private Integer parseInteger(String value) {
		String normalized = trimToNull(value);
		if (normalized == null) {
			return null;
		}
		try {
			return Integer.parseInt(normalized.replace(",", ""));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * 試合時間文字列を秒へ変換します。
	 *
	 * <p>
	 * 例:
	 * </p>
	 * <ul>
	 *   <li>7:50 → 470</li>
	 *   <li>45+2:10 → 2830</li>
	 *   <li>ハーフタイム → 2700</li>
	 *   <li>終了済 → 5400</li>
	 * </ul>
	 *
	 * @param time 試合時間文字列
	 * @return 秒
	 */
	private Integer parseMatchTimeToSeconds(String time) {
		String normalized = trimToNull(time);
		if (normalized == null) {
			return 0;
		}

		if (BookMakersCommonConst.HALF_TIME.equals(normalized)) {
			return 45 * 60;
		}
		if (BookMakersCommonConst.FIN.equals(normalized)) {
			return 90 * 60;
		}

		try {
			if (normalized.contains(":")) {
				String[] parts = normalized.split(":");
				String minutePart = parts[0].trim();
				int seconds = Integer.parseInt(parts[1].trim());

				int minutes;
				if (minutePart.contains("+")) {
					String[] minParts = minutePart.split("\\+");
					minutes = 0;
					for (String p : minParts) {
						minutes += Integer.parseInt(p.trim());
					}
				} else {
					minutes = Integer.parseInt(minutePart);
				}
				return minutes * 60 + seconds;
			}
			return Integer.parseInt(normalized);
		} catch (Exception e) {
			return 0;
		}
	}

	/**
	 * null の場合 0 を返します。
	 *
	 * @param value 対象値
	 * @return 値または0
	 */
	private int nvl(Integer value) {
		return value == null ? 0 : value;
	}

	/**
	 * null の場合 0 を返します。
	 *
	 * @param value 対象値
	 * @return 値または0
	 */
	private BigDecimal nvl(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	/**
	 * 文字列をtrimし、空文字ならnullを返します。
	 *
	 * @param value 対象文字列
	 * @return trim後文字列。空文字ならnull
	 */
	private String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
