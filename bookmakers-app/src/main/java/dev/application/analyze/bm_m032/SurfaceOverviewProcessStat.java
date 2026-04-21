package dev.application.analyze.bm_m032;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.bm_m031.SurfaceOverviewEntity;
import dev.application.domain.repository.bm.SurfaceOverviewProcessRepository;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;

/**
 * SurfaceOverview の更新前後データから、
 * 同一チームの直前ラウンドとの差分情報を生成する統計クラス。
 *
 * <p>
 * 本クラスは {@link SurfaceOverviewEntity} の更新前後を比較し、
 * {@link SurfaceOverviewProcessEntity} を生成する。
 * </p>
 *
 * <p>
 * 差分データは、更新前ラウンドと更新後ラウンドが隣接している場合のみ生成する。
 * 例えば、前回ラウンドが5で今回ラウンドが6の場合は差分生成対象となるが、
 * 前回ラウンドが5で今回ラウンドが7の場合は、ラウンド6の欠損があるため
 * 差分を導出できず、生成対象外とする。
 * </p>
 *
 * @author shiraishitoshio
 */
@Component
public class SurfaceOverviewProcessStat {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = SurfaceOverviewProcessStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = SurfaceOverviewProcessStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M032_SURFACE_OVERVIEW_PROCESS";

	/** ロガー */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/** 差分データRepository */
	@Autowired
	private SurfaceOverviewProcessRepository surfaceOverviewProcessRepository;

	/**
	 * 最終更新から1時間経過した差分データを削除する。
	 *
	 * @return 削除件数
	 */
	public int deleteExpiredProcessEntity() {
		final String METHOD_NAME = "deleteExpiredProcessEntity";
		manageLoggerComponent.init(EXEC_MODE, null);
		manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		int deleteCount = surfaceOverviewProcessRepository.deleteExpired();

		String messageCd = MessageCdConst.MCD00099I_LOG;
		manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				"deleteCount: " + deleteCount);

		manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		manageLoggerComponent.clear();

		return deleteCount;
	}

	/**
	 * 更新前後の SurfaceOverview から差分Entityを生成する。
	 *
	 * <p>
	 * 以下の場合は差分データを生成せず null を返す。
	 * </p>
	 * <ul>
	 *   <li>更新前データが存在しない場合</li>
	 *   <li>更新前または更新後のラウンド番号が取得できない場合</li>
	 *   <li>更新前ラウンドと更新後ラウンドが隣接していない場合</li>
	 * </ul>
	 *
	 * @param before 更新前の累積Entity
	 * @param after 更新後の累積Entity
	 * @return 差分を格納した {@link SurfaceOverviewProcessEntity}。
	 *         差分導出不可の場合は null
	 */
	public SurfaceOverviewProcessEntity createProcessEntity(
			SurfaceOverviewEntity before,
			SurfaceOverviewEntity after) {
		final String METHOD_NAME = "createProcessEntity";
		manageLoggerComponent.init(EXEC_MODE, null);
		manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		if (before == null || after == null) {
			String messageCd = MessageCdConst.MCD00099I_LOG;
			manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					"before or after is null");
			manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			manageLoggerComponent.clear();
			return null;
		}

		Integer previousRoundNo = getLastRoundNo(before.getRoundConc());
		Integer currentRoundNo = getLastRoundNo(after.getRoundConc());

		if (!isAdjacentRound(previousRoundNo, currentRoundNo)) {
			String messageCd = MessageCdConst.MCD00099I_LOG;
			manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					"skip process entity. previousRoundNo: " + previousRoundNo
							+ " || currentRoundNo: " + currentRoundNo
							+ " || country: " + after.getCountry()
							+ " || league: " + after.getLeague()
							+ " || team: " + after.getTeam());

			manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			manageLoggerComponent.clear();
			return null;
		}

		SurfaceOverviewProcessEntity entity = new SurfaceOverviewProcessEntity();

		entity.setCountry(after.getCountry());
		entity.setLeague(after.getLeague());
		entity.setGameYear(after.getGameYear());
		entity.setGameMonth(after.getGameMonth());
		entity.setTeam(after.getTeam());

		entity.setBeforeRoundConc(before.getRoundConc());
		entity.setAfterRoundConc(after.getRoundConc());

		entity.setPreviousRoundNo(previousRoundNo);
		entity.setCurrentRoundNo(currentRoundNo);
		entity.setRoundGap(calcRoundGap(previousRoundNo, currentRoundNo));

		entity.setGamesDiff(diff(after.getGames(), before.getGames()));
		entity.setWinDiff(diff(after.getWin(), before.getWin()));
		entity.setLoseDiff(diff(after.getLose(), before.getLose()));
		entity.setDrawDiff(diff(after.getDraw(), before.getDraw()));
		entity.setWinningPointsDiff(diff(after.getWinningPoints(), before.getWinningPoints()));

		entity.setHome1stHalfScoreDiff(diff(after.getHome1stHalfScore(), before.getHome1stHalfScore()));
		entity.setHome2ndHalfScoreDiff(diff(after.getHome2ndHalfScore(), before.getHome2ndHalfScore()));
		entity.setHomeSumScoreDiff(diff(after.getHomeSumScore(), before.getHomeSumScore()));

		entity.setAway1stHalfScoreDiff(diff(after.getAway1stHalfScore(), before.getAway1stHalfScore()));
		entity.setAway2ndHalfScoreDiff(diff(after.getAway2ndHalfScore(), before.getAway2ndHalfScore()));
		entity.setAwaySumScoreDiff(diff(after.getAwaySumScore(), before.getAwaySumScore()));

		entity.setHome1stHalfLostDiff(diff(after.getHome1stHalfLost(), before.getHome1stHalfLost()));
		entity.setHome2ndHalfLostDiff(diff(after.getHome2ndHalfLost(), before.getHome2ndHalfLost()));
		entity.setHomeSumLostDiff(diff(after.getHomeSumLost(), before.getHomeSumLost()));

		entity.setAway1stHalfLostDiff(diff(after.getAway1stHalfLost(), before.getAway1stHalfLost()));
		entity.setAway2ndHalfLostDiff(diff(after.getAway2ndHalfLost(), before.getAway2ndHalfLost()));
		entity.setAwaySumLostDiff(diff(after.getAwaySumLost(), before.getAwaySumLost()));

		entity.setFailToScoreGameCountDiff(diff(after.getFailToScoreGameCount(), before.getFailToScoreGameCount()));

		entity.setFirstWeekGameWinCountDiff(diff(after.getFirstWeekGameWinCount(), before.getFirstWeekGameWinCount()));
		entity.setFirstWeekGameLostCountDiff(diff(after.getFirstWeekGameLostCount(), before.getFirstWeekGameLostCount()));
		entity.setMidWeekGameWinCountDiff(diff(after.getMidWeekGameWinCount(), before.getMidWeekGameWinCount()));
		entity.setMidWeekGameLostCountDiff(diff(after.getMidWeekGameLostCount(), before.getMidWeekGameLostCount()));
		entity.setLastWeekGameWinCountDiff(diff(after.getLastWeekGameWinCount(), before.getLastWeekGameWinCount()));
		entity.setLastWeekGameLostCountDiff(diff(after.getLastWeekGameLostCount(), before.getLastWeekGameLostCount()));

		entity.setHomeWinCountDiff(diff(after.getHomeWinCount(), before.getHomeWinCount()));
		entity.setHomeLoseCountDiff(diff(after.getHomeLoseCount(), before.getHomeLoseCount()));
		entity.setHomeFirstGoalCountDiff(diff(after.getHomeFirstGoalCount(), before.getHomeFirstGoalCount()));
		entity.setHomeWinBehindCountDiff(diff(after.getHomeWinBehindCount(), before.getHomeWinBehindCount()));
		entity.setHomeLoseBehindCountDiff(diff(after.getHomeLoseBehindCount(), before.getHomeLoseBehindCount()));

		entity.setAwayWinCountDiff(diff(after.getAwayWinCount(), before.getAwayWinCount()));
		entity.setAwayLoseCountDiff(diff(after.getAwayLoseCount(), before.getAwayLoseCount()));
		entity.setAwayFirstGoalCountDiff(diff(after.getAwayFirstGoalCount(), before.getAwayFirstGoalCount()));
		entity.setAwayWinBehindCountDiff(diff(after.getAwayWinBehindCount(), before.getAwayWinBehindCount()));
		entity.setAwayLoseBehindCountDiff(diff(after.getAwayLoseBehindCount(), before.getAwayLoseBehindCount()));

		manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		manageLoggerComponent.clear();

		return entity;
	}

	/**
	 * roundConc 文字列から A= の最後のラウンド番号を取得する。
	 *
	 * @param roundConc roundConc文字列
	 * @return 最後のラウンド番号。取得できない場合は null
	 */
	public Integer getLastRoundNo(String roundConc) {
		List<Integer> rounds = parseAllRounds(roundConc);
		if (rounds.isEmpty()) {
			return null;
		}
		return rounds.get(rounds.size() - 1);
	}

	/**
	 * 直前ラウンドと今回ラウンドが隣接しているか判定する。
	 *
	 * @param previousRoundNo 直前ラウンド番号
	 * @param currentRoundNo 今回ラウンド番号
	 * @return 隣接している場合 true
	 */
	private boolean isAdjacentRound(Integer previousRoundNo, Integer currentRoundNo) {
		if (previousRoundNo == null || currentRoundNo == null) {
			return false;
		}
		return currentRoundNo - previousRoundNo == 1;
	}

	/**
	 * roundConc 文字列から A= のラウンド一覧を取得する。
	 *
	 * @param roundConc roundConc文字列
	 * @return ラウンド番号一覧
	 */
	private List<Integer> parseAllRounds(String roundConc) {
		List<Integer> rounds = new ArrayList<>();

		if (roundConc == null || roundConc.isBlank()) {
			return rounds;
		}

		String[] parts = roundConc.split("\\|");
		for (String part : parts) {
			String[] kv = part.split("=", 2);
			if (kv.length != 2) {
				continue;
			}

			String key = kv[0].trim();
			if (!"A".equals(key)) {
				continue;
			}

			String value = kv[1].trim();
			if (value.isEmpty()) {
				return rounds;
			}

			String[] tokens = value.split(",");
			for (String token : tokens) {
				String v = token == null ? "" : token.trim();
				if (v.matches("\\d+")) {
					rounds.add(Integer.parseInt(v));
				}
			}
			break;
		}

		return rounds;
	}

	/**
	 * ラウンド差分を算出する。
	 *
	 * @param previousRoundNo 直前ラウンド
	 * @param currentRoundNo 今回ラウンド
	 * @return ラウンド差
	 */
	private Integer calcRoundGap(Integer previousRoundNo, Integer currentRoundNo) {
		final String METHOD_NAME = "calcRoundGap";
		if (previousRoundNo == null || currentRoundNo == null) {
			String messageCd = MessageCdConst.MCD00099I_LOG;
			manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					"previousRoundNo: " + previousRoundNo + "|| currentRoundNo: " + currentRoundNo);
			return null;
		}
		return currentRoundNo - previousRoundNo;
	}

	/**
	 * 文字列数値同士の差分を算出する。
	 *
	 * @param after 更新後値
	 * @param before 更新前値
	 * @return after - before
	 */
	private Integer diff(String after, String before) {
		return parse(after) - parse(before);
	}

	/**
	 * 数値文字列を int に変換する。
	 *
	 * <p>
	 * null / 空文字 / 非数値は 0 として扱う。
	 * </p>
	 *
	 * @param value 数値文字列
	 * @return int値
	 */
	private int parse(String value) {
		if (value == null || value.isBlank()) {
			return 0;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
