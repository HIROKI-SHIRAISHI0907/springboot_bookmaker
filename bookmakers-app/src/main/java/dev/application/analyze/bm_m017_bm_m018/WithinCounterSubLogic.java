package dev.application.analyze.bm_m017_bm_m018;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import dev.application.analyze.common.entity.ThresHoldEntity;
import dev.application.analyze.common.util.ExecuteMainUtil;


/**
 * 得点が入ったデータを調査するサブロジック
 * @author shiraishitoshio
 *
 */
public class WithinCounterSubLogic {

	/**
	 * @param entityList CSV読み込みEntityリスト
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws IOException
	 * @throws InvalidFormatException
	 * @throws InterruptedException
	 */
	public void execute(List<ThresHoldEntity> entityList)
			throws IllegalArgumentException, IllegalAccessException, InvalidFormatException, IOException,
			InterruptedException {

		List<String> scoreContainsList = new ArrayList<String>();

		List<String> scoreList = new ArrayList<String>();
		List<String> dataKeyList = new ArrayList<String>();
		StringBuilder timeBuilder = new StringBuilder();
		StringBuilder homeTimeBuilder = new StringBuilder();
		StringBuilder awayTimeBuilder = new StringBuilder();
		int sumBef = -1;
		int homeBef = -1;
		int awayBef = -1;
		for (ThresHoldEntity entity : entityList) {
			List<String> data_category_list = ExecuteMainUtil.getCountryLeagueByRegex(entity.getDataCategory());
			// ホーム、アウェースコア
			int home_score = Integer.parseInt(entity.getHomeScore());
			int away_score = Integer.parseInt(entity.getAwayScore());

			// 国とリーグ
			String country = data_category_list.get(0);
			String league = data_category_list.get(1);

			// 分単位の範囲に変換した試合時間
			String game_time = entity.getTimes();
			String modify_time = ExecuteMainUtil.classifyMatchTime(game_time);

			// 得点が入った瞬間, その時の時間も保存
			if (home_score > 0 || away_score > 0) {
				StringBuilder sBuilder = new StringBuilder();
				sBuilder.append(entity.getHomeScore());
				sBuilder.append(",");
				sBuilder.append(entity.getAwayScore());
				if (!scoreContainsList.contains(sBuilder.toString())) {
					int sum = home_score + away_score;
					if (sumBef == -1 || sumBef < sum) {
						scoreList = new ArrayList<String>();
						scoreList.add(entity.getHomeScore());
						scoreList.add(entity.getAwayScore());
					}
					sumBef = sum;

					if (timeBuilder.toString().length() > 0) {
						timeBuilder.append(",");
					}
					timeBuilder.append(modify_time);

					int home = home_score;
					if (home > 0 && homeBef < home) {
						if (homeTimeBuilder.toString().length() > 0) {
							homeTimeBuilder.append(",");
						}
						homeTimeBuilder.append(modify_time);
					}
					homeBef = home;

					int away = away_score;
					if (away > 0 && awayBef < away) {
						if (awayTimeBuilder.toString().length() > 0) {
							awayTimeBuilder.append(",");
						}
						awayTimeBuilder.append(modify_time);
					}
					awayBef = away;

					scoreContainsList.add(sBuilder.toString());
				}
			}
			if (dataKeyList.isEmpty()) {
				dataKeyList.add(country);
				dataKeyList.add(league);
			}
		}

		if (scoreList.isEmpty()) {
			scoreList.add("0");
			scoreList.add("0");
		}

		executeWithinCounterMain(dataKeyList, scoreList, timeBuilder.toString(),
				homeTimeBuilder.toString(), awayTimeBuilder.toString());
	}

	/**
	 * メインロジック
	 * @param dataKeyList
	 * @param scoreList
	 * @param time
	 * @param hometime
	 * @param awaytime
	 * @throws InvalidFormatException
	 * @throws IOException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws InterruptedException
	 */
	public static void executeWithinCounterMain(List<String> dataKeyList,
			List<String> scoreList,
			String time,
			String hometime,
			String awaytime)
			throws InvalidFormatException, IOException, IllegalArgumentException, IllegalAccessException,
			InterruptedException {

		WithInCounterDbInsert withInCounterDbInsert = new WithInCounterDbInsert();
		withInCounterDbInsert.execute(dataKeyList, scoreList, time, hometime, awaytime);
	}

}
