package dev.application.analyze.bm_m004;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.TeamTimeSegmentShootingStatsRepository;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M004統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class TeamTimeSegmentShootingStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamTimeSegmentShootingStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamTimeSegmentShootingStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M004_TEAM_TIME_SEGMENT_SHOOTING";

	/** TeamTimeSegmentShootingStatsRepositoryレポジトリクラス */
	@Autowired
	private TeamTimeSegmentShootingStatsRepository teamTimeSegmentShootingStatsRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// Mapを回す
		entities.entrySet().parallelStream().forEach(outerEntry -> {
			String countryLeague = outerEntry.getKey();
			Map<String, List<BookDataEntity>> teamMap = outerEntry.getValue();

			for (Map.Entry<String, List<BookDataEntity>> teamEntry : teamMap.entrySet()) {
				String teamKey = teamEntry.getKey(); // 例: "Kawasaki-home"
				String home = teamKey.split("-")[0];
				String away = teamKey.split("-")[1];
				List<BookDataEntity> bookList = teamEntry.getValue();

				// 時間帯別集計用Map（例：0-10, 11-20...）
				Map<String, TimeSegmentAggregator> timeSegmentMap = new ConcurrentHashMap<>();

				// BookDataEntityを走査し時間帯ごとの値を加算
				for (BookDataEntity data : bookList) {
					String timeZone = getTimeZone(data.getTime()); // 0-10, 11-20 など
					timeSegmentMap.putIfAbsent(timeZone, new TimeSegmentAggregator());
					TimeSegmentAggregator aggr = timeSegmentMap.get(timeZone);

					aggr.addShoot(data.getHomeShootAll());
					aggr.addShootIn(data.getHomeBoxShootIn());
					aggr.addBigChance(data.getHomeBigChance());
					aggr.addFreeKick(data.getHomeFreeKick());
					aggr.addOffside(data.getHomeOffSide());
					aggr.addFoul(data.getHomeFoul());
					aggr.addYellow(data.getHomeYellowCard());
					aggr.addRed(data.getHomeRedCard());
				}

				// Entityに詰める
				TeamTimeSegmentShootingStatsEntity entity = new TeamTimeSegmentShootingStatsEntity();
				entity.setDataCategory(countryLeague);
				entity.setTeamName(home);
				entity.setAwayTeamName(away);

				// 各時間帯をセット
				timeSegmentMap.forEach((zone, aggr) -> {
					String avgShoot = String.format("%.2f", aggr.getShootAvg());
					String avgShootIn = String.format("%.2f", aggr.getShootInAvg());
					String avgBigChance = String.format("%.2f", aggr.getBigChanceAvg());
					String avgFreeKick = String.format("%.2f", aggr.getFreeKickAvg());
					String avgOffside = String.format("%.2f", aggr.getOffsideAvg());
					String avgFoul = String.format("%.2f", aggr.getFoulAvg());
					String avgYellow = String.format("%.2f", aggr.getYellowAvg());
					String avgRed = String.format("%.2f", aggr.getRedAvg());

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
					}
				});

				// 保存処理
				save(entity);
			}
		});

		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();

	}

	/**
	 * 時間帯判定メソッド
	 * @param elapsedTimeStr (XX:XX, 第一ハーフなど)
	 * @return
	 */
	private synchronized String getTimeZone(String elapsedTimeStr) {
		if (BookMakersCommonConst.HALF_TIME.equals(elapsedTimeStr) ||
				BookMakersCommonConst.FIRST_HALF_TIME.equals(elapsedTimeStr)) {
			return "41-45";
		}
		// 45+,90+
		if (elapsedTimeStr.contains("+")) {
			String[] data = elapsedTimeStr.split("+");
			int minute = Integer.parseInt(data[0]);
			int second = 0;
			if (data.length > 1) {
				second = Integer.parseInt(data[1].replace("'", ""));
			}
			int sumtime = minute + second;
			if (sumtime > 90) return "AT";
			if (minute > 45 && minute < 50)
				return "46-50";
			if (minute > 51 && minute < 60)
				return "51-60";
		}
		try {
			int minute = Integer.parseInt(elapsedTimeStr.split(":")[0]);
			if (minute <= 10)
				return "0-10";
			else if (minute <= 20)
				return "11-20";
			else if (minute <= 30)
				return "21-30";
			else if (minute <= 40)
				return "31-40";
			else if (minute <= 45)
				return "41-45";
			else if (minute <= 50)
				return "46-50";
			else if (minute <= 60)
				return "51-60";
			else if (minute <= 70)
				return "61-70";
			else if (minute <= 80)
				return "71-80";
			else if (minute <= 90)
				return "81-90";
			else
				return "AT";
		} catch (Exception e) {
			return "AT"; // 失敗時はATとみなす
		}
	}

	/**
	 * 登録ロジック
	 * @param entity
	 */
	private synchronized void save(TeamTimeSegmentShootingStatsEntity entity) {
		final String METHOD_NAME = "save";

		int result = this.teamTimeSegmentShootingStatsRepository.insert(entity);
		if (result != 1) {
			String messageCd = "新規登録エラー";
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
			this.manageLoggerComponent.createSystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null);
		}

		String messageCd = "登録件数";
		String fillChar = setLoggerFillChar(entity);
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "BM_M004 登録件数: 1件");
	}

	/**
	 * 埋め字設定
	 * @param entity
	 * @return
	 */
	private String setLoggerFillChar(TeamTimeSegmentShootingStatsEntity entity) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("国,リーグ: " + entity.getDataCategory() + ", ");
		stringBuilder.append("ホームチーム: " + entity.getTeamName() + ", ");
		stringBuilder.append("アウェーチーム: " + entity.getAwayTeamName());
		return stringBuilder.toString();
	}
}
