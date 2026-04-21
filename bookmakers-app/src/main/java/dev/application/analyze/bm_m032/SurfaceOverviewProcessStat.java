package dev.application.analyze.bm_m032;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.application.analyze.bm_m031.SurfaceOverviewEntity;

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
 * ラウンド番号は {@code roundConc} の {@code A=} 部分から判定する。
 * 例:
 * </p>
 *
 * <pre>
 * before: A=1,2,4|W=1|L=2,4
 * after : A=1,2,4,5|W=1,5|L=2,4
 * </pre>
 *
 * <p>
 * この場合、
 * </p>
 * <ul>
 *   <li>直前ラウンド = 4</li>
 *   <li>今回ラウンド = 5</li>
 *   <li>ラウンド差 = 1</li>
 * </ul>
 *
 * <p>
 * 差分値は基本的に {@code after - before} で算出する。
 * 数値項目が null / 空文字 / 非数値の場合は 0 として扱う。
 * </p>
 *
 * @author shiraishitoshio
 */
@Component
public class SurfaceOverviewProcessStat {

	/**
	 * 更新前後の SurfaceOverview から差分Entityを生成する。
	 *
	 * <p>
	 * 直前ラウンド番号は更新前の {@code roundConc} から取得し、
	 * 今回ラウンド番号は更新後の {@code roundConc} から取得する。
	 * </p>
	 *
	 * @param before 更新前の累積Entity
	 * @param after  更新後の累積Entity
	 * @return 差分を格納した {@link SurfaceOverviewProcessEntity}
	 */
	public SurfaceOverviewProcessEntity createProcessEntity(
			SurfaceOverviewEntity before,
			SurfaceOverviewEntity after) {

		if (after == null) {
			return null;
		}

		SurfaceOverviewProcessEntity entity = new SurfaceOverviewProcessEntity();

		entity.setCountry(after.getCountry());
		entity.setLeague(after.getLeague());
		entity.setGameYear(after.getGameYear());
		entity.setGameMonth(after.getGameMonth());
		entity.setTeam(after.getTeam());

		entity.setBeforeRoundConc(before == null ? null : before.getRoundConc());
		entity.setAfterRoundConc(after.getRoundConc());

		Integer previousRoundNo = getLastRoundNo(before == null ? null : before.getRoundConc());
		Integer currentRoundNo = getLastRoundNo(after.getRoundConc());

		entity.setPreviousRoundNo(previousRoundNo);
		entity.setCurrentRoundNo(currentRoundNo);
		entity.setRoundGap(calcRoundGap(previousRoundNo, currentRoundNo));

		entity.setGamesDiff(diff(after.getGames(), before == null ? null : before.getGames()));
		entity.setWinDiff(diff(after.getWin(), before == null ? null : before.getWin()));
		entity.setLoseDiff(diff(after.getLose(), before == null ? null : before.getLose()));
		entity.setDrawDiff(diff(after.getDraw(), before == null ? null : before.getDraw()));
		entity.setWinningPointsDiff(diff(after.getWinningPoints(), before == null ? null : before.getWinningPoints()));

		entity.setHome1stHalfScoreDiff(diff(after.getHome1stHalfScore(), before == null ? null : before.getHome1stHalfScore()));
		entity.setHome2ndHalfScoreDiff(diff(after.getHome2ndHalfScore(), before == null ? null : before.getHome2ndHalfScore()));
		entity.setHomeSumScoreDiff(diff(after.getHomeSumScore(), before == null ? null : before.getHomeSumScore()));

		entity.setAway1stHalfScoreDiff(diff(after.getAway1stHalfScore(), before == null ? null : before.getAway1stHalfScore()));
		entity.setAway2ndHalfScoreDiff(diff(after.getAway2ndHalfScore(), before == null ? null : before.getAway2ndHalfScore()));
		entity.setAwaySumScoreDiff(diff(after.getAwaySumScore(), before == null ? null : before.getAwaySumScore()));

		entity.setHome1stHalfLostDiff(diff(after.getHome1stHalfLost(), before == null ? null : before.getHome1stHalfLost()));
		entity.setHome2ndHalfLostDiff(diff(after.getHome2ndHalfLost(), before == null ? null : before.getHome2ndHalfLost()));
		entity.setHomeSumLostDiff(diff(after.getHomeSumLost(), before == null ? null : before.getHomeSumLost()));

		entity.setAway1stHalfLostDiff(diff(after.getAway1stHalfLost(), before == null ? null : before.getAway1stHalfLost()));
		entity.setAway2ndHalfLostDiff(diff(after.getAway2ndHalfLost(), before == null ? null : before.getAway2ndHalfLost()));
		entity.setAwaySumLostDiff(diff(after.getAwaySumLost(), before == null ? null : before.getAwaySumLost()));

		entity.setFailToScoreGameCountDiff(diff(after.getFailToScoreGameCount(), before == null ? null : before.getFailToScoreGameCount()));

		entity.setFirstWeekGameWinCountDiff(diff(after.getFirstWeekGameWinCount(), before == null ? null : before.getFirstWeekGameWinCount()));
		entity.setFirstWeekGameLostCountDiff(diff(after.getFirstWeekGameLostCount(), before == null ? null : before.getFirstWeekGameLostCount()));
		entity.setMidWeekGameWinCountDiff(diff(after.getMidWeekGameWinCount(), before == null ? null : before.getMidWeekGameWinCount()));
		entity.setMidWeekGameLostCountDiff(diff(after.getMidWeekGameLostCount(), before == null ? null : before.getMidWeekGameLostCount()));
		entity.setLastWeekGameWinCountDiff(diff(after.getLastWeekGameWinCount(), before == null ? null : before.getLastWeekGameWinCount()));
		entity.setLastWeekGameLostCountDiff(diff(after.getLastWeekGameLostCount(), before == null ? null : before.getLastWeekGameLostCount()));

		entity.setHomeWinCountDiff(diff(after.getHomeWinCount(), before == null ? null : before.getHomeWinCount()));
		entity.setHomeLoseCountDiff(diff(after.getHomeLoseCount(), before == null ? null : before.getHomeLoseCount()));
		entity.setHomeFirstGoalCountDiff(diff(after.getHomeFirstGoalCount(), before == null ? null : before.getHomeFirstGoalCount()));
		entity.setHomeWinBehindCountDiff(diff(after.getHomeWinBehindCount(), before == null ? null : before.getHomeWinBehindCount()));
		entity.setHomeLoseBehindCountDiff(diff(after.getHomeLoseBehindCount(), before == null ? null : before.getHomeLoseBehindCount()));

		entity.setAwayWinCountDiff(diff(after.getAwayWinCount(), before == null ? null : before.getAwayWinCount()));
		entity.setAwayLoseCountDiff(diff(after.getAwayLoseCount(), before == null ? null : before.getAwayLoseCount()));
		entity.setAwayFirstGoalCountDiff(diff(after.getAwayFirstGoalCount(), before == null ? null : before.getAwayFirstGoalCount()));
		entity.setAwayWinBehindCountDiff(diff(after.getAwayWinBehindCount(), before == null ? null : before.getAwayWinBehindCount()));
		entity.setAwayLoseBehindCountDiff(diff(after.getAwayLoseBehindCount(), before == null ? null : before.getAwayLoseBehindCount()));

		return entity;
	}

	/**
	 * roundConc 文字列から A= の最後のラウンド番号を取得する。
	 *
	 * <p>
	 * 例:
	 * </p>
	 * <pre>
	 * A=1,2,4,5|W=1,5|L=2,4 -> 5
	 * A=1,2,4|W=1|L=2,4     -> 4
	 * </pre>
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
	 * @return ラウンド差。どちらかが null の場合は null
	 */
	private Integer calcRoundGap(Integer previousRoundNo, Integer currentRoundNo) {
		if (previousRoundNo == null || currentRoundNo == null) {
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
