package dev.application.analyze.bm_m004;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M004統計分析ロジック（手動データ投入の場合は適用対象外）
 *
 * 元コードを踏襲しつつ、以下を改善:
 * - parallelStream廃止
 * - save処理をWriterへ分離
 * - logger clearをfinallyで保証
 * - null安全化
 * - teamKey解析を少し堅牢化
 *
 * @author shiraishitoshio
 */
@Component
public class TeamTimeSegmentShootingStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamTimeSegmentShootingStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamTimeSegmentShootingStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M004_TEAM_TIME_SEGMENT_SHOOTING";

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M004";

	/** 登録処理 */
	private final TeamTimeSegmentShootingWriter writer;

	/** ログ管理クラス */
	private final ManageLoggerComponent manageLoggerComponent;

	@Autowired
	public TeamTimeSegmentShootingStat(
			TeamTimeSegmentShootingWriter writer,
			ManageLoggerComponent manageLoggerComponent) {
		this.writer = writer;
		this.manageLoggerComponent = manageLoggerComponent;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		try {
			if (entities == null || entities.isEmpty()) {
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, BM_NUMBER + " 入力データなし");
				return;
			}

			for (Entry<String, Map<String, List<BookDataEntity>>> outerEntry : entities.entrySet()) {
				String countryLeague = outerEntry.getKey();
				Map<String, List<BookDataEntity>> teamMap = outerEntry.getValue();

				if (teamMap == null || teamMap.isEmpty()) {
					continue;
				}

				for (Entry<String, List<BookDataEntity>> teamEntry : teamMap.entrySet()) {
					String teamKey = teamEntry.getKey();
					List<BookDataEntity> bookList = teamEntry.getValue();

					if (bookList == null || bookList.isEmpty()) {
						continue;
					}

					TeamPair teamPair = parseTeamKey(teamKey);
					if (teamPair == null) {
						this.manageLoggerComponent.debugInfoLog(
								PROJECT_NAME,
								CLASS_NAME,
								METHOD_NAME,
								null,
								BM_NUMBER + " teamKey解析失敗: " + teamKey);
						continue;
					}

					TeamTimeSegmentShootingStatsEntity entity =
							buildEntity(countryLeague, teamPair, bookList, METHOD_NAME);

					this.writer.insert(entity);
				}
			}
		} finally {
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	/**
	 * 集計結果からEntityを構築
	 */
	private TeamTimeSegmentShootingStatsEntity buildEntity(
			String countryLeague,
			TeamPair teamPair,
			List<BookDataEntity> bookList,
			String methodName) {

		Map<String, TimeSegmentAggregator> timeSegmentMap = aggregateByTimeZone(bookList, methodName);

		TeamTimeSegmentShootingStatsEntity entity = new TeamTimeSegmentShootingStatsEntity();
		entity.setDataCategory(countryLeague);
		entity.setTeamName(teamPair.getHomeTeamName());
		entity.setAwayTeamName(teamPair.getAwayTeamName());

		timeSegmentMap.forEach((zone, aggr) -> applyZoneStats(entity, zone, aggr));

		return entity;
	}

	/**
	 * 時間帯ごとに集計
	 */
	private Map<String, TimeSegmentAggregator> aggregateByTimeZone(
			List<BookDataEntity> bookList,
			String methodName) {

		Map<String, TimeSegmentAggregator> timeSegmentMap = new HashMap<>();

		for (BookDataEntity data : bookList) {
			if (data == null) {
				continue;
			}

			String messageCd = MessageCdConst.MCD00099I_LOG;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME,
					CLASS_NAME,
					methodName,
					messageCd,
					data.getFilePath());

			String timeZone = getTimeZone(data.getTime());
			TimeSegmentAggregator aggr =
					timeSegmentMap.computeIfAbsent(timeZone, key -> new TimeSegmentAggregator());

			aggr.addRecord(
					safeValue(data.getHomeShootAll()),
					safeValue(data.getHomeBoxShootIn()),
					safeValue(data.getHomeBigChance()),
					safeValue(data.getHomeFreeKick()),
					safeValue(data.getHomeOffSide()),
					safeValue(data.getHomeFoul()),
					safeValue(data.getHomeYellowCard()),
					safeValue(data.getHomeRedCard()));
		}

		return timeSegmentMap;
	}

	/**
	 * 各時間帯をEntityへセット
	 */
	private void applyZoneStats(
			TeamTimeSegmentShootingStatsEntity entity,
			String zone,
			TimeSegmentAggregator aggr) {

		String avgShoot = formatAvg(aggr.getShootAvg());
		String avgShootIn = formatAvg(aggr.getShootInAvg());
		String avgBigChance = formatAvg(aggr.getBigChanceAvg());
		String avgFreeKick = formatAvg(aggr.getFreeKickAvg());
		String avgOffside = formatAvg(aggr.getOffsideAvg());
		String avgFoul = formatAvg(aggr.getFoulAvg());
		String avgYellow = formatAvg(aggr.getYellowAvg());
		String avgRed = formatAvg(aggr.getRedAvg());

		switch (zone) {
		case "0-10":
			entity.setTeam0to10MeanShootCount(avgShoot);
			entity.setTeam0to10MeanShootInCount(avgShootIn);
			entity.setTeam0to10MeanBigChanceCount(avgBigChance);
			entity.setTeam0to10MeanFreeKickCount(avgFreeKick);
			entity.setTeam0to10MeanOffsideCount(avgOffside);
			entity.setTeam0to10MeanFoulCount(avgFoul);
			entity.setTeam0to10MeanYellowCardCount(avgYellow);
			entity.setTeam0to10MeanRedCardCount(avgRed);
			break;

		case "11-20":
			entity.setTeam11to20MeanShootCount(avgShoot);
			entity.setTeam11to20MeanShootInCount(avgShootIn);
			entity.setTeam11to20MeanBigChanceCount(avgBigChance);
			entity.setTeam11to20MeanFreeKickCount(avgFreeKick);
			entity.setTeam11to20MeanOffsideCount(avgOffside);
			entity.setTeam11to20MeanFoulCount(avgFoul);
			entity.setTeam11to20MeanYellowCardCount(avgYellow);
			entity.setTeam11to20MeanRedCardCount(avgRed);
			break;

		case "21-30":
			entity.setTeam21to30MeanShootCount(avgShoot);
			entity.setTeam21to30MeanShootInCount(avgShootIn);
			entity.setTeam21to30MeanBigChanceCount(avgBigChance);
			entity.setTeam21to30MeanFreeKickCount(avgFreeKick);
			entity.setTeam21to30MeanOffsideCount(avgOffside);
			entity.setTeam21to30MeanFoulCount(avgFoul);
			entity.setTeam21to30MeanYellowCardCount(avgYellow);
			entity.setTeam21to30MeanRedCardCount(avgRed);
			break;

		case "31-40":
			entity.setTeam31to40MeanShootCount(avgShoot);
			entity.setTeam31to40MeanShootInCount(avgShootIn);
			entity.setTeam31to40MeanBigChanceCount(avgBigChance);
			entity.setTeam31to40MeanFreeKickCount(avgFreeKick);
			entity.setTeam31to40MeanOffsideCount(avgOffside);
			entity.setTeam31to40MeanFoulCount(avgFoul);
			entity.setTeam31to40MeanYellowCardCount(avgYellow);
			entity.setTeam31to40MeanRedCardCount(avgRed);
			break;

		case "41-45":
			entity.setTeam41to45MeanShootCount(avgShoot);
			entity.setTeam41to45MeanShootInCount(avgShootIn);
			entity.setTeam41to45MeanBigChanceCount(avgBigChance);
			entity.setTeam41to45MeanFreeKickCount(avgFreeKick);
			entity.setTeam41to45MeanOffsideCount(avgOffside);
			entity.setTeam41to45MeanFoulCount(avgFoul);
			entity.setTeam41to45MeanYellowCardCount(avgYellow);
			entity.setTeam41to45MeanRedCardCount(avgRed);
			break;

		case "46-50":
			entity.setTeam46to50MeanShootCount(avgShoot);
			entity.setTeam46to50MeanShootInCount(avgShootIn);
			entity.setTeam46to50MeanBigChanceCount(avgBigChance);
			entity.setTeam46to50MeanFreeKickCount(avgFreeKick);
			entity.setTeam46to50MeanOffsideCount(avgOffside);
			entity.setTeam46to50MeanFoulCount(avgFoul);
			entity.setTeam46to50MeanYellowCardCount(avgYellow);
			entity.setTeam46to50MeanRedCardCount(avgRed);
			break;

		case "51-60":
			entity.setTeam51to60MeanShootCount(avgShoot);
			entity.setTeam51to60MeanShootInCount(avgShootIn);
			entity.setTeam51to60MeanBigChanceCount(avgBigChance);
			entity.setTeam51to60MeanFreeKickCount(avgFreeKick);
			entity.setTeam51to60MeanOffsideCount(avgOffside);
			entity.setTeam51to60MeanFoulCount(avgFoul);
			entity.setTeam51to60MeanYellowCardCount(avgYellow);
			entity.setTeam51to60MeanRedCardCount(avgRed);
			break;

		case "61-70":
			entity.setTeam61to70MeanShootCount(avgShoot);
			entity.setTeam61to70MeanShootInCount(avgShootIn);
			entity.setTeam61to70MeanBigChanceCount(avgBigChance);
			entity.setTeam61to70MeanFreeKickCount(avgFreeKick);
			entity.setTeam61to70MeanOffsideCount(avgOffside);
			entity.setTeam61to70MeanFoulCount(avgFoul);
			entity.setTeam61to70MeanYellowCardCount(avgYellow);
			entity.setTeam61to70MeanRedCardCount(avgRed);
			break;

		case "71-80":
			entity.setTeam71to80MeanShootCount(avgShoot);
			entity.setTeam71to80MeanShootInCount(avgShootIn);
			entity.setTeam71to80MeanBigChanceCount(avgBigChance);
			entity.setTeam71to80MeanFreeKickCount(avgFreeKick);
			entity.setTeam71to80MeanOffsideCount(avgOffside);
			entity.setTeam71to80MeanFoulCount(avgFoul);
			entity.setTeam71to80MeanYellowCardCount(avgYellow);
			entity.setTeam71to80MeanRedCardCount(avgRed);
			break;

		case "81-90":
			entity.setTeam81to90MeanShootCount(avgShoot);
			entity.setTeam81to90MeanShootInCount(avgShootIn);
			entity.setTeam81to90MeanBigChanceCount(avgBigChance);
			entity.setTeam81to90MeanFreeKickCount(avgFreeKick);
			entity.setTeam81to90MeanOffsideCount(avgOffside);
			entity.setTeam81to90MeanFoulCount(avgFoul);
			entity.setTeam81to90MeanYellowCardCount(avgYellow);
			entity.setTeam81to90MeanRedCardCount(avgRed);
			break;

		case "AT":
			entity.setTeamAddiMeanShootCount(avgShoot);
			entity.setTeamAddiMeanShootInCount(avgShootIn);
			entity.setTeamAddiMeanBigChanceCount(avgBigChance);
			entity.setTeamAddiMeanFreeKickCount(avgFreeKick);
			entity.setTeamAddiMeanOffsideCount(avgOffside);
			entity.setTeamAddiMeanFoulCount(avgFoul);
			entity.setTeamAddiMeanYellowCardCount(avgYellow);
			entity.setTeamAddiMeanRedCardCount(avgRed);
			break;

		default:
			break;
		}
	}

	/**
	 * 時間帯判定メソッド
	 * @param elapsedTimeStr
	 * @return
	 */
	private String getTimeZone(String elapsedTimeStr) {
		if (elapsedTimeStr == null || elapsedTimeStr.isBlank()) {
			return "AT";
		}

		if (BookMakersCommonConst.HALF_TIME.equals(elapsedTimeStr)
				|| BookMakersCommonConst.FIRST_HALF_TIME.equals(elapsedTimeStr)) {
			return "41-45";
		}

		String s = elapsedTimeStr.trim();
		try {
			int totalMinute;

			if (s.contains("+")) {
				String[] data = s.split("\\+", 2);
				String baseStr = data[0].replaceAll("\\D+", "");
				String addStr = (data.length > 1 ? data[1] : "").replaceAll("\\D+", "");
				int base = baseStr.isEmpty() ? 0 : Integer.parseInt(baseStr);
				int add = addStr.isEmpty() ? 0 : Integer.parseInt(addStr);
				totalMinute = base + add;
			} else if (s.contains(":")) {
				String mStr = s.split(":", 2)[0].replaceAll("\\D+", "");
				totalMinute = mStr.isEmpty() ? 0 : Integer.parseInt(mStr);
			} else {
				String mStr = s.replaceAll("\\D+", "");
				totalMinute = mStr.isEmpty() ? 0 : Integer.parseInt(mStr);
			}

			if (totalMinute > 90) return "AT";
			if (totalMinute <= 10) return "0-10";
			if (totalMinute <= 20) return "11-20";
			if (totalMinute <= 30) return "21-30";
			if (totalMinute <= 40) return "31-40";
			if (totalMinute <= 45) return "41-45";
			if (totalMinute <= 50) return "46-50";
			if (totalMinute <= 60) return "51-60";
			if (totalMinute <= 70) return "61-70";
			if (totalMinute <= 80) return "71-80";
			return "81-90";
		} catch (Exception e) {
			return "AT";
		}
	}

	/**
	 * teamKey解析
	 *
	 * 対応例:
	 * - "Kawasaki|Yokohama"
	 * - "Kawasaki - Yokohama"
	 * - "Kawasaki-Yokohama"（ハイフンが1個だけのとき）
	 */
	private TeamPair parseTeamKey(String teamKey) {
		if (teamKey == null || teamKey.isBlank()) {
			return null;
		}

		String normalized = teamKey.trim();

		TeamPair pair = splitTeamKey(normalized, "|");
		if (pair != null) {
			return pair;
		}

		pair = splitTeamKey(normalized, " - ");
		if (pair != null) {
			return pair;
		}

		int firstHyphen = normalized.indexOf("-");
		int lastHyphen = normalized.lastIndexOf("-");
		if (firstHyphen > 0 && firstHyphen == lastHyphen) {
			String home = normalized.substring(0, firstHyphen).trim();
			String away = normalized.substring(firstHyphen + 1).trim();
			if (!home.isEmpty() && !away.isEmpty()) {
				return new TeamPair(home, away);
			}
		}

		return null;
	}

	private TeamPair splitTeamKey(String teamKey, String separator) {
		int idx = teamKey.indexOf(separator);
		if (idx < 0) {
			return null;
		}

		String home = teamKey.substring(0, idx).trim();
		String away = teamKey.substring(idx + separator.length()).trim();

		if (home.isEmpty() || away.isEmpty()) {
			return null;
		}

		return new TeamPair(home, away);
	}

	private int safeValue(String string) {
		return string == null ? 0 : Integer.parseInt(string.trim());
	}

	private String formatAvg(double value) {
		return String.format("%.2f", value);
	}

	/**
	 * teamKey保持用
	 */
	private static class TeamPair {
		private final String homeTeamName;
		private final String awayTeamName;

		private TeamPair(String homeTeamName, String awayTeamName) {
			this.homeTeamName = homeTeamName;
			this.awayTeamName = awayTeamName;
		}

		public String getHomeTeamName() {
			return homeTeamName;
		}

		public String getAwayTeamName() {
			return awayTeamName;
		}
	}

	/**
	 * 時間帯集計用
	 */
	private static class TimeSegmentAggregator {
		private int count;
		private int shootTotal;
		private int shootInTotal;
		private int bigChanceTotal;
		private int freeKickTotal;
		private int offsideTotal;
		private int foulTotal;
		private int yellowTotal;
		private int redTotal;

		public void addRecord(
				int shoot,
				int shootIn,
				int bigChance,
				int freeKick,
				int offside,
				int foul,
				int yellow,
				int red) {
			this.count++;
			this.shootTotal += shoot;
			this.shootInTotal += shootIn;
			this.bigChanceTotal += bigChance;
			this.freeKickTotal += freeKick;
			this.offsideTotal += offside;
			this.foulTotal += foul;
			this.yellowTotal += yellow;
			this.redTotal += red;
		}

		public double getShootAvg() {
			return avg(shootTotal);
		}

		public double getShootInAvg() {
			return avg(shootInTotal);
		}

		public double getBigChanceAvg() {
			return avg(bigChanceTotal);
		}

		public double getFreeKickAvg() {
			return avg(freeKickTotal);
		}

		public double getOffsideAvg() {
			return avg(offsideTotal);
		}

		public double getFoulAvg() {
			return avg(foulTotal);
		}

		public double getYellowAvg() {
			return avg(yellowTotal);
		}

		public double getRedAvg() {
			return avg(redTotal);
		}

		private double avg(int total) {
			if (count == 0) {
				return 0.0d;
			}
			return (double) total / (double) count;
		}
	}
}
