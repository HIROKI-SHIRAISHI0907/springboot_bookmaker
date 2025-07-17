package dev.application.analyze.bm_m027;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.application.common.dto.MakeStatisticsGameCountCsvOutputDTO;
import dev.application.common.exception.SystemException;
import dev.application.common.file.MakeCsv;
import dev.application.common.util.ContainsCountryLeagueUtil;
import dev.application.common.util.ExecuteMainUtil;
import dev.application.common.util.UniairColumnMapUtil;
import dev.application.db.BookDataSelectWrapper;
import dev.application.db.ExistsUpdCsvInfo;
import dev.application.db.SqlMainLogic;
import dev.application.db.UniairConst;

/**
 * average_statistics_csv_tmp_dataを元に特定のスコアになったタイミングが何回あるかを集計したCSVを作成する
 * @author shiraishitoshio
 *
 */
public class MakeStatisticsGameCountCsvLogic {

	/** マップ */
	private HashMap<String, HashMap<String, HashMap<String, String>>> keyMap = new HashMap<String, HashMap<String, HashMap<String, String>>>();

	/** ヘッダー */
	private List<String> header = new ArrayList<>();

	/** ヘッダー詳細 */
	private List<String> headerDetail = new ArrayList<>();

	/** ヘッダー得点詳細 */
	private List<String> headerScoreDetail = new ArrayList<>();

	/** ファイルヘッダー */
	private static final String FILE_SUFFIX = "/Users/shiraishitoshio/bookmaker/python_analytics/game_count/";

	/**
	 * 実行ロジック
	 */
	public void makeLogic() {
		// ヘッダー
		this.headerDetail.add("H");
		this.headerDetail.add("");
		this.headerDetail.add("A");
		this.headerDetail.add("");
		// ヘッダー
		this.headerScoreDetail.add("1st");
		this.headerScoreDetail.add("2nd");
		this.headerScoreDetail.add("1st");
		this.headerScoreDetail.add("2nd");

		String[] selectList = new String[2];
		selectList[0] = "country";
		selectList[1] = "league";

		MakeCsv makeCsv = new MakeCsv();

		// レコード件数を取得する
		BookDataSelectWrapper selectWrapper = new BookDataSelectWrapper();
		int cnt = -1;
		try {
			cnt = selectWrapper.executeCountSelect(UniairConst.BM_M006, null);
		} catch (Exception e) {
			return;
		}

		SqlMainLogic select = new SqlMainLogic();

		List<String> selectSubList = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M027);
		String[] selList = new String[selectSubList.size()];
		for (int i = 0; i < selectSubList.size(); i++) {
			selList[i] = selectSubList.get(i);
		}

		for (int id = 1; id <= cnt; id++) {
			List<List<String>> selectResultList = null;
			String where = "id = '" + id + "'";
			try {
				selectResultList = select.executeSelect(null, UniairConst.BM_M006, selectList, where, null, "1");
			} catch (Exception e) {
				return;
			}

			String country = selectResultList.get(0).get(0);
			String category = selectResultList.get(0).get(1);

			// 必要な国,リーグの組み合わせのみ作成する
			if (!ContainsCountryLeagueUtil.containsCountryLeague(country, category)) {
				continue;
			}

			// upd_csv_infoチェック
			if (ExistsUpdCsvInfo.exist()) {
				List<List<String>> resultList = ExistsUpdCsvInfo.chk(country, category,
						UniairConst.BM_M027, "");
				if (resultList.isEmpty()) {
					continue;
				}
			}

			System.out.println("MakeStatisticsGameCountCsvLogic country: " + country + ", category: " + category +
					", id: " + id);

			List<List<String>> selectResultSubList = null;
			String subWhere = "country = '" + country + "' and league = '" + category + "'";
			try {
				selectResultSubList = select.executeSelect(null, UniairConst.BM_M027,
						selList, subWhere, null, null);
			} catch (Exception e) {
				return;
			}

			String dataKey = country + ":" + category;
			HashMap<String, HashMap<String, String>> subKeyMap = new HashMap<String, HashMap<String, String>>();
			for (List<String> list : selectResultSubList) {
				String score = list.get(1);
				String team = list.get(4);
				String count = list.get(5);
				HashMap<String, String> valueMap = new HashMap<String, String>();
				if (subKeyMap.containsKey(score)) {
					valueMap = subKeyMap.get(score);
				}
				valueMap.put(team, count);
				subKeyMap.put(score, valueMap);
			}
			this.keyMap.put(dataKey, subKeyMap);

			// スコアでソート
			// ソート処理
	        HashMap<String, HashMap<String, HashMap<String, String>>> sortedMap = new LinkedHashMap<>();
	        // 外側のマップを処理
	        for (Map.Entry<String, HashMap<String, HashMap<String, String>>> outerEntry : this.keyMap.entrySet()) {
	            // 内側のマップ（group）をリストにしてソート
	            List<Map.Entry<String, HashMap<String, String>>> sortedInnerList = new ArrayList<>(outerEntry.getValue().entrySet());

	            // 2番目の String（team名）でソート
	            sortedInnerList.sort(Comparator.comparing(Map.Entry::getKey));

	            // ソートされた結果を新しいマップに追加
	            HashMap<String, HashMap<String, String>> sortedInnerMap = new LinkedHashMap<>();
	            for (Map.Entry<String, HashMap<String, String>> innerEntry : sortedInnerList) {
	                sortedInnerMap.put(innerEntry.getKey(), innerEntry.getValue());
	            }

	            // ソートされた innerMap を外側のマップに追加
	            sortedMap.put(outerEntry.getKey(), sortedInnerMap);
	        }
	        this.keyMap = sortedMap;

			HashMap<String, HashMap<String, HashMap<String, String>>> oyaDetailMap = new HashMap<String, HashMap<String, HashMap<String, String>>>();

			for (Map.Entry<String, HashMap<String, HashMap<String, String>>> oyaMap : this.keyMap.entrySet()) {
				String header = "チーム名";
				// ヘッダー
				this.header.add(header);

				// 国,リーグ
				String file = oyaMap.getKey();
				file = file.replace(":", "-");
				// スコア,<チーム名,件数>
				HashMap<String, HashMap<String, String>> koMap = oyaMap.getValue();

				List<List<String>> makeList = new ArrayList<List<String>>();
				for (Map.Entry<String, HashMap<String, String>> kokoMap : koMap.entrySet()) {
					// ヘッダーにチーム単位で存在しているスコアを追加
					this.header.add(kokoMap.getKey());

					// <チーム名,件数>を2重リストに書き換え
					HashMap<String, String> kokokoMap = kokoMap.getValue();
					for (Map.Entry<String, String> subMap : kokokoMap.entrySet()) {
						System.out.println("header: " + this.header);
						System.out.println("kokoMap.getKey(): " + kokoMap.getKey());
						System.out.println(
								"subMap.getKey(): " + subMap.getKey() + ", subMap.getValue(): " + subMap.getValue());
						System.out.println("makeList: " + makeList);

						// 特定のスコアにおける途中・ホーム・アウェーの得点を集計
						MakeStatisticsGameCountCsvOutputDTO makeStatisticsGameCountCsvOutputDTO = totallizationDetail(
								country, category, subMap.getKey(),
								subMap.getValue(), oyaDetailMap);
						oyaDetailMap = makeStatisticsGameCountCsvOutputDTO.getOyaDetailMap();

						List<String> makeSubList = new ArrayList<>();
						// チーム名が入っているリスト番号を返す
						int ind = chkListNum(makeList, subMap.getKey());
						// -1以外ならそのリストを取り出す
						if (ind != -1) {
							makeSubList = makeList.get(ind);
							makeSubList.add(subMap.getValue());
							makeList.set(ind, makeSubList);
						} else {
							makeSubList.add(subMap.getKey());
							makeSubList.add(subMap.getValue());
							makeList.add(makeSubList);
						}
						System.out.println("makeList: " + makeList);
					}

					// リストの件数がヘッダーの件数に到達していない場合,空データを追加する
					makeList = addEmptyData(makeList, this.header.size());

				}
				makeCsv.execute(FILE_SUFFIX + file + ".csv", null, this.header, makeList);
				this.header.clear();
			}
			this.keyMap.clear();

			List<List<String>> makeDetailList = new ArrayList<List<String>>();
			List<String> makeDetailSubList = new ArrayList<>();
			makeDetailSubList.add("チーム名");
			makeDetailList.add(makeDetailSubList);
			makeDetailSubList = new ArrayList<>();
			makeDetailSubList.add("ホームorアウェー");
			makeDetailList.add(makeDetailSubList);
			makeDetailSubList = new ArrayList<>();
			makeDetailSubList.add("1stor2nd");
			makeDetailList.add(makeDetailSubList);

			// 詳細マップ<チーム名-チーム名-HorA, <1-0, <前半or後半, 3>>>
			String file = "";
			for (Map.Entry<String, HashMap<String, HashMap<String, String>>> oyaMap : oyaDetailMap.entrySet()) {
				String team = oyaMap.getKey(); // チーム名-チーム名-HorA
				HashMap<String, HashMap<String, String>> koMap = oyaMap.getValue();
				for (Map.Entry<String, HashMap<String, String>> kokoMap : koMap.entrySet()) {
					String score = kokoMap.getKey(); // 1-0
					// ヘッダ
					makeDetailList = addHeaderList(makeDetailList, "header", score, null, 0);
					// 途中・H・A
					makeDetailList = addHeaderList(makeDetailList, "1stHeader", null, this.headerDetail, 1);
					// 1st 2nd
					makeDetailList = addHeaderList(makeDetailList, "2ndHeader", null, this.headerScoreDetail, 2);

					HashMap<String, String> kokokoMap = kokoMap.getValue();
					for (Map.Entry<String, String> kokokokoMap : kokokoMap.entrySet()) {
						String firstSecond = kokokokoMap.getKey(); // 1stor2nd
						String count = kokokokoMap.getValue(); // 3

						// チームとhomeawayを分割
						String[] teamStr = team.split("-");
						String home = teamStr[0];
						String away = teamStr[1];
						String ha = teamStr[2];

						file = country + "-" + category;

						if ("H".equals(ha)) {
							makeDetailList = addValueList(makeDetailList, home, ha, score, firstSecond, count);
						} else if ("A".equals(ha)) {
							makeDetailList = addValueList(makeDetailList, away, ha, score, firstSecond, count);
						}
					}
				}
			}

			List<String> firstList = makeDetailList.get(0);
			makeDetailList.remove(0);

			makeCsv.execute(FILE_SUFFIX + file + "_detail.csv", null, firstList, makeDetailList);
		}
	}

	/**
	 * ヘッダーを追加する
	 * @param makeDetailList
	 * @param key
	 * @param value
	 * @return
	 */
	private List<List<String>> addHeaderList(List<List<String>> makeDetailList,
			String key, String value, List<String> valueList, int headerNum) {
		if ("header".equals(key) || "1stHeader".equals(key) || "2ndHeader".equals(key)) {
			int resultInd = -1;
			int ind = 0;
			for (List<String> subList : makeDetailList) {
				for (String data : subList) {
					if (data.equals(value)) {
						resultInd = ind;
						break;
					}
				}
				if (resultInd != -1) {
					break;
				}
				ind++;
			}

			if (resultInd == -1 && "header".equals(key)) {
				List<String> makeDetailSubList = makeDetailList.get(headerNum);
				if (value != null) {
					makeDetailSubList.add("");
					makeDetailSubList.add(value);
					makeDetailSubList.add("");
					makeDetailSubList.add("");
				} else if (!valueList.isEmpty()) {
					makeDetailSubList.addAll(valueList);
				}
				makeDetailList.set(headerNum, makeDetailSubList);
			}
		}

		int maxSize = makeDetailList.get(0).size();
		if ("1stHeader".equals(key)) {
			List<String> subList = makeDetailList.get(1);
			if (subList.size() > maxSize) {
				maxSize = subList.size();
			}

			// 今追加する mkList が maxSize より短ければ 0 埋め
			while (subList.size() < maxSize) {
				subList.addAll(valueList);
			}
		}

		if ("2ndHeader".equals(key)) {
			List<String> subList = makeDetailList.get(2);
			if (subList.size() > maxSize) {
				maxSize = subList.size();
			}

			// 今追加する mkList が maxSize より短ければ 0 埋め
			while (subList.size() < maxSize) {
				subList.addAll(valueList);
			}
		}

		return makeDetailList;
	}

	/**
	 * ヘッダーを追加する
	 * @param makeDetailList
	 * @param key
	 * @param value
	 * @param ha
	 * @param headerNum
	 * @return
	 */
	private List<List<String>> addValueList(List<List<String>> makeDetailList,
			String team, String ha, String score, String firstSecond, String count) {
		// データが存在したらそのデータが存在するリストに追加(件数を+1する),なければ新規でリストを作成
		// スコアが存在する配列番号を調べる
		List<String> scoreKey = makeDetailList.get(0);
		int scoreInd = -1;
		for (String key : scoreKey) {
			if ("".equals(key)) {
				continue;
			}
			if (score.equals(key)) {
				break;
			}
			scoreInd++;
		}

		// データは左から[ホーム・1st, ホーム・2nd, アウェー・1st, アウェー・2nd]の組み合わせ
		// 存在要素を決定
		int dataInd = -1;
		if ("H".equals(ha) && "1st".equals(firstSecond)) {
			dataInd = 1 + 4 * scoreInd;
		} else if ("H".equals(ha) && "2nd".equals(firstSecond)) {
			dataInd = 2 + 4 * scoreInd;
		} else if ("A".equals(ha) && "1st".equals(firstSecond)) {
			dataInd = 3 + 4 * scoreInd;
		} else if ("A".equals(ha) && "2nd".equals(firstSecond)) {
			dataInd = 4 + 4 * scoreInd;
		}

		// 存在レコードを決定（4リスト目以降にteamが含まれている場合にrecordIndを取得）
		int recordInd = -1;
		for (int i = 3; i < makeDetailList.size(); i++) {
			List<String> data = makeDetailList.get(i);
			for (String record : data) {
				if (team.equals(record)) {
					recordInd = i;
					break; // 該当レコードが見つかったら内側ループ終了
				}
			}
			if (recordInd != -1) {
				break; // 見つかったら外側ループも終了
			}
		}

		List<String> mkList = null;
		if (recordInd != -1) {
			// 全てのリストが一致しているか調べる。一致していないリストがあれば要素数が一致するよう追加する。
			// 基本的には件数に関するリストが一致しない想定
			int maxSize = 0;
			for (List<String> subList : makeDetailList) {
				if (subList.size() > maxSize) {
					maxSize = subList.size();
				}

				// 今追加する mkList が maxSize より短ければ 0 埋め
				while (subList.size() < maxSize) {
					subList.add("0");
				}
			}

			mkList = makeDetailList.get(recordInd);
			count = String.valueOf(Integer.parseInt(mkList.get(dataInd)) + Integer.parseInt(count));
			mkList.set(dataInd, count);
			makeDetailList.set(recordInd, mkList);
		} else {
			mkList = new ArrayList<>();
			mkList.add(team);
			for (int ind = 0; ind < 4; ind++) {
				mkList.add("0");
			}
			makeDetailList.add(mkList);

			// 全てのリストが一致しているか調べる。一致していないリストがあれば要素数が一致するよう追加する。
			// 基本的には件数に関するリストが一致しない想定
			int maxSize = 0;
			for (List<String> subList : makeDetailList) {
				if (subList.size() > maxSize) {
					maxSize = subList.size();
				}

				// 今追加する mkList が maxSize より短ければ 0 埋め
				while (subList.size() < maxSize) {
					subList.add("0");
				}
			}

			count = String.valueOf(Integer.parseInt(mkList.get(dataInd)) + Integer.parseInt(count));
			mkList.set(dataInd, count);
		}

		return makeDetailList;
	}

	/**
	 * 特定スコア詳細マップCSV作成用マッピングロジック
	 * @param country 国
	 * @param league リーグ
	 * @param team チーム
	 * @param sumScore 合計スコア
	 * @param oyaDetailMap 保持親マップ
	 */
	private MakeStatisticsGameCountCsvOutputDTO totallizationDetail(String country, String league, String team,
			String sumScore,
			HashMap<String, HashMap<String, HashMap<String, String>>> oyaDetailMap) {
		MakeStatisticsGameCountCsvOutputDTO makeStatisticsGameCountCsvOutputDTO = new MakeStatisticsGameCountCsvOutputDTO();

		// 指定したチームがホームの場合とアウェーの場合でそれぞれ集計
		String[] selDataList = new String[5];
		selDataList[0] = "home_team_name";
		selDataList[1] = "home_score";
		selDataList[2] = "away_team_name";
		selDataList[3] = "away_score";
		selDataList[4] = "times";

		for (int i = 1; i <= 2; i++) {
			String teams = "";
			String oppoTeams = "";
			String ha = "";
			if (i == 1) {
				teams = "home_team_name";
				oppoTeams = "away_team_name";
				ha = "H";
			} else {
				teams = "away_team_name";
				oppoTeams = "home_team_name";
				ha = "A";
			}

			String where = "data_category LIKE '" + country + "%' and data_category LIKE '%" +
					league + "%' and " + teams + " = '" + team + "'";

			String sort = teams + " ASC, " + oppoTeams + " ASC, seq ASC";

			List<List<String>> selectResultList = null;
			SqlMainLogic select = new SqlMainLogic();
			try {
				selectResultList = select.executeSelect(null, UniairConst.BM_M001, selDataList,
						where, sort, null);
			} catch (Exception e) {
				throw new SystemException("", "", "", "err");
			}

			// 集計済みデータ格納リスト
			HashSet<String> processedScores = new HashSet<>();
			// スコアの集計は得点が切り替わった時
			for (List<String> list : selectResultList) {
				String teamkey = list.get(0) + "-" + list.get(2) + "-" + ha;
				String score = list.get(1) + "-" + list.get(3);
				String dataKey = teamkey + "-" + score;
				// すでに処理した組み合わせはスキップ
				if (processedScores.contains(dataKey)) {
					continue; // 次のループに進む
				}
				processedScores.add(dataKey);

				// 前半のデータか後半のデータか
				String timesData = "";
				double time = ExecuteMainUtil.convertToMinutes(list.get(4));
				if (time <= 45.0) {
					timesData = "1st";
				} else {
					timesData = "2nd";
				}

				HashMap<String, HashMap<String, String>> koMap = new HashMap<String, HashMap<String, String>>();
				HashMap<String, String> kokoMap = new HashMap<String, String>();
				String count = "0";
				// もしkoMapがスコアで既に存在していれば、kokoMapを取得
				if (koMap.containsKey(score)) {
					kokoMap = koMap.get(score);
				} else {
					// 初めてスコアが見つかった場合は、新しいkokoMapを作成
					kokoMap = new HashMap<String, String>();
				}

				// 時間データに対してカウントが既にあれば、それを取得
				if (kokoMap.containsKey(timesData)) {
					count = kokoMap.get(timesData);
				}

				// countを1増やす
				count = String.valueOf(Integer.parseInt(count) + 1);
				// 時間データをキーにカウントを更新
				kokoMap.put(timesData, count);
				// koMapにスコアをキーにして、kokoMapを保存
				koMap.put(score, kokoMap);
				//<チーム名-チーム名-H, <1-0, <前半or後半, 3>>>
				oyaDetailMap.put(teamkey, koMap);
			}
		}

		makeStatisticsGameCountCsvOutputDTO.setOyaDetailMap(oyaDetailMap);
		return makeStatisticsGameCountCsvOutputDTO;
	}

	/**
	 * チーム名が入っているリスト番号を調べる
	 * @param list
	 * @param team
	 * @return
	 */
	private int chkListNum(List<List<String>> list, String team) {
		int ind = 0;
		for (List<String> subList : list) {
			String lists = subList.get(0);
			if (team.equals(lists)) {
				return ind;
			}
			ind++;
		}
		return -1;
	}

	/**
	 * 件数を一致させるために空データを追加する
	 * @param list
	 * @param team
	 * @return
	 */
	private List<List<String>> addEmptyData(List<List<String>> list, int count) {
		for (List<String> subList : list) {
			int size = subList.size();
			if (size != count) {
				subList.add("");
			}
		}
		return list;
	}

}
