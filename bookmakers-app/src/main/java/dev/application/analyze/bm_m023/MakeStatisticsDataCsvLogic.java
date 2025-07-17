package dev.application.analyze.bm_m023;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import dev.application.analyze.common.util.ExecuteMainUtil;
import dev.common.constant.UniairConst;
import dev.common.exception.SystemException;
import dev.common.makecsv.MakeCsv;


/**
 * 統計データをCSVにするロジック
 * @author shiraishitoshio
 *
 */
public class MakeStatisticsDataCsvLogic {

	/**
	 * 除去リスト
	 */
	private static final List<String> HEADER;
	static {
		List<String> list = new ArrayList<>();
		list.add("スコア");
		list.add("フィールド");
		list.add("範囲");
		list.add("取得時間");
		list.add("ランキング");
		HEADER = Collections.unmodifiableList(list);
	}

	/** Map */
	private HashMap<String, LinkedHashMap<String, String>> map = new LinkedHashMap<String, LinkedHashMap<String, String>>();

	/**
	 * 実行メソッド
	 */
	public void execute() {

		BookDataSelectWrapper selectWrapper = new BookDataSelectWrapper();
		// レコード件数を取得する
		int cnt = -1;
		try {
			cnt = selectWrapper.executeCountSelect(UniairConst.BM_M006, null);
		} catch (Exception e) {
			throw new SystemException(
					"",
					"",
					"",
					"",
					e.getCause());
		}

		List<String> selDataAllList = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M006);
		String[] selDataList = new String[selDataAllList.size()];
		for (int i = 0; i < selDataAllList.size(); i++) {
			selDataList[i] = selDataAllList.get(i);
		}

		List<String> selDataSubList = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M023);
		String[] selDataSubSubList = new String[selDataSubList.size()];
		for (int i = 0; i < selDataSubList.size(); i++) {
			selDataSubSubList[i] = selDataSubList.get(i);
		}

		MakeCsv makeCsv = new MakeCsv();
		for (int id = 1; id <= cnt; id++) {
			String where = "id = '" + id + "'";

			List<List<String>> selectResultList = null;
			SqlMainLogic select = new SqlMainLogic();
			try {
				selectResultList = select.executeSelect(null, UniairConst.BM_M006, selDataList,
						where, null, "1");
			} catch (Exception e) {
				throw new SystemException("", "", "", "err");
			}

			String country = selectResultList.get(0).get(1);
			String league = selectResultList.get(0).get(2);

			System.out.println("対象データ: " + country + " : " + league);

			String whereSub = "country = '" + country + "' and league = '" + league + "'";

			List<List<String>> selectResultSubList = null;
			try {
				selectResultSubList = select.executeSelect(null, UniairConst.BM_M023, selDataSubSubList,
						whereSub, null, null);
			} catch (Exception e) {
				throw new SystemException("", "", "", "err");
			}

			// rank取得する
			HashMap<String, Integer> rankMaps = getCorrRank(country, league);

			LinkedHashMap<String, String> maps = new LinkedHashMap<String, String>();
			for (List<String> list : selectResultSubList) {
				String score = list.get(2);

				int field = 0;
				for (String subList : list) {
					if (field < 5) {
						field++;
						continue;
					}
					String[] data = subList.split(",");
					String ave = data[2];
					String sigma = data[3];
					String aveTime = data[7];
					String sigmaTime = data[8];
					setData(score, ave, sigma, aveTime, sigmaTime, field, maps, rankMaps);
					field++;
				}

				List<List<String>> lists = convert();

				if (!lists.isEmpty()) {
					String filename = "/Users/shiraishitoshio/bookmaker/python_analytics/stat/" + country + "-" + league
							+ "-" + score + "_stat.csv";
					makeCsv.execute(filename, null, HEADER, lists);
				}

				maps.clear();
				map.clear();
			}
		}

	}

	/**
	 * 平均,標準偏差,試合時間設定
	 * @param score
	 * @param ave
	 * @param sigma
	 * @param aveTime
	 * @param sigmaTime
	 */
	private void setData(String score, String ave, String sigma, String aveTime, String sigmaTime,
			int fieldInd, LinkedHashMap<String, String> maps, HashMap<String, Integer> rankMaps) {
		if (!("10000.0".equals(ave) && "10000.0".equals(sigma))) {
			String high = "";
			String low = "";
			List<String> threeHighList = new ArrayList<>();
			List<String> threeLowList = new ArrayList<>();
			if (ave.contains("/") || sigma.contains("/")) {
				List<String> splitAve = ExecuteMainUtil.splitGroup(ave);
				List<String> splitSigma = ExecuteMainUtil.splitGroup(sigma);

				for (int sp = 0; sp < splitAve.size(); sp++) {
					String remarks = "";
					String chkAve = splitAve.get(sp);
					String chkSigma = splitSigma.get(sp);
					if (chkAve.contains("%")) {
						remarks = "%";
						chkAve = chkAve.replace("%", "");
					}
					if (chkSigma.contains("%")) {
						remarks = "%";
						chkSigma = chkSigma.replace("%", "");
					}
					double aveD = Double.parseDouble(chkAve);
					double sigmaD = Double.parseDouble(chkSigma);
					// 平均+標準偏差,平均-標準偏差を導出する
					String highSub = String.format("%.2f", aveD + sigmaD) + remarks;
					String lowSub = String.format("%.2f", aveD - sigmaD) + remarks;
					threeHighList.add(highSub);
					threeLowList.add(lowSub);
				}
				high = threeHighList.get(0) + " (" + threeHighList.get(1)
						+ "/" + threeHighList.get(2) + ")";
				low = threeLowList.get(0) + " (" + threeLowList.get(1)
						+ "/" + threeLowList.get(2) + ")";
			} else {
				String remarks = "";
				if (ave.contains("%")) {
					remarks = "%";
					ave = ave.replace("%", "");
				}
				if (sigma.contains("%")) {
					remarks = "%";
					sigma = sigma.replace("%", "");
				}
				double aveD = Double.parseDouble(ave);
				double sigmaD = Double.parseDouble(sigma);
				// 平均+標準偏差,平均-標準偏差を導出する
				high = String.format("%.2f", aveD + sigmaD) + remarks;
				low = String.format("%.2f", aveD - sigmaD) + remarks;
			}

			String times = "";
			if (aveTime.contains("'")) {
				times = "'";
				aveTime = aveTime.replace("'", "");
			}
			if (sigmaTime.contains("'")) {
				times = "'";
				sigmaTime = sigmaTime.replace("'", "");
			}
			double aveTimeD = Double.parseDouble(aveTime);
			double sigmaTimeD = Double.parseDouble(sigmaTime);

			// 平均+標準偏差,平均-標準偏差を導出する
			String timeHigh = String.format("%.2f", aveTimeD + sigmaTimeD) + times;
			String timeLow = String.format("%.2f", aveTimeD - sigmaTimeD) + times;

			ScoreBasedFeatureStatsEntity entity = new ScoreBasedFeatureStatsEntity();
			Field[] fields = entity.getClass().getDeclaredFields();
			// fieldInd=5以降がデータ値
			String field_name = "";
			int subInd = 0;
			for (Field field : fields) {
				field.setAccessible(true);
				if (subInd < fieldInd) {
					subInd++;
					continue;
				}
				field_name = field.getName();
				break;
			}

			// 件数を取得
			String count = "---";
			if (rankMaps.containsKey(field_name)) {
				count = String.valueOf(rankMaps.get(field_name));
			}

			maps.put(field_name, low + "-" + high + "," + timeLow + "-" + timeHigh + "," + count);
			map.put(score, maps);
		}
	}

	/**
	 * 変換
	 * @return
	 */
	private List<List<String>> convert() {
		List<List<String>> returnAllList = new ArrayList<List<String>>();
		if (this.map.isEmpty()) {
			return returnAllList;
		}
		for (Entry<String, LinkedHashMap<String, String>> maps : this.map.entrySet()) {
			String score = maps.getKey();
			HashMap<String, String> data = maps.getValue();
			for (Entry<String, String> mapSub : data.entrySet()) {
				List<String> returnList = new ArrayList<>();
				StringBuilder sBuilder = new StringBuilder();
				String field_name = mapSub.getKey();
				String[] key = mapSub.getValue().split(",");
				sBuilder.append(score);
				sBuilder.append(",");
				sBuilder.append(field_name);
				sBuilder.append(",");
				sBuilder.append(key[0]);
				sBuilder.append(",");
				sBuilder.append(key[1]);
				sBuilder.append(",");
				sBuilder.append(key[2]);
				returnList.add(sBuilder.toString());
				returnAllList.add(returnList);
			}
		}
		return returnAllList;
	}

	/**
	 * フィールド値に応じたランキングを取得する
	 * @param country
	 * @param league
	 */
	private HashMap<String, Integer> getCorrRank(String country, String league) {
		HashMap<String, Integer> rankMaps = new HashMap<String, Integer>();
		String[] selDataCorrsList = new String[5];
		selDataCorrsList[0] = "1st_rank";
		selDataCorrsList[1] = "2nd_rank";
		selDataCorrsList[2] = "3rd_rank";
		selDataCorrsList[3] = "4th_rank";
		selDataCorrsList[4] = "5th_rank";

		String whereSub = "country = '" + country + "' and league = '" + league + "' and chk_body = '"
				+ "PEARSON'";

		List<List<String>> selectResultSubList = null;
		SqlMainLogic select = new SqlMainLogic();
		try {
			selectResultSubList = select.executeSelect(null, UniairConst.BM_M025, selDataCorrsList,
					whereSub, null, null);
		} catch (Exception e) {
			throw new SystemException("", "", "", "err");
		}

		for (List<String> list : selectResultSubList) {
			for (String subList : list) {
				// 相関係数のみ導出し,相関係数をキーにして件数を調べる
				String[] corrs = subList.split(",");
				String corr = corrs[0];
				int count = (rankMaps.containsKey(corr)) ? rankMaps.get(corr) : 0;
				// 加算
				count += 1;
				rankMaps.put(corr, count);
			}
		}
		return rankMaps;
	}

}
