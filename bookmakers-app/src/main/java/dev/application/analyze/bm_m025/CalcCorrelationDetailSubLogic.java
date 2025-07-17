package dev.application.common.logic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import dev.application.analyze.bm_m097.ExistsUpdCsvInfo;
import dev.application.analyze.common.entity.ThresHoldEntity;
import dev.application.analyze.common.util.ExecuteMainUtil;
import dev.application.common.util.UniairColumnMapUtil;
import dev.application.db.CsvRegisterImpl;
import dev.application.db.SqlMainLogic;
import dev.application.db.UpdateWrapper;
import dev.application.entity.CorrelationDetailEntity;
import dev.application.entity.CorrelationEntity;
import dev.common.constant.UniairConst;
import dev.common.exception.SystemException;

/**
 * 相関係数の結果を相関値が高い順に並べるロジック
 * @author shiraishitoshio
 *
 */
public class CalcCorrelationDetailSubLogic {

	/** クラスタ関連の件数 */
	private static final int CLUSTER_DATA_COUNT = 68;

	/** PEARSON */
	private static final String PEARSON = "PEARSON";

	/** KmeansCluster */
	private static final String KMEANS_CLUSTER = "KmeansCluster";

	/** hierarchical */
	private static final String HIERARCHICAL = "hierarchical";

	/**
	 * ロジック
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	public void execute(List<ThresHoldEntity> entityList, String file)
			throws IllegalArgumentException, IllegalAccessException {

		// 1. 引数でThresHold型のデータと現在読み込んでいるfileパスを取得
		// 以下の2ケース実行する
		// 2. PEARSON, KmeansCluster関係なく実行
		// 3. upd_csv_infoにM025がある場合,相関係数データからKmeansClusterのみデータを取得して,ranking更新

		// 共通鍵暗号化
		SecretKey keys = null;
		try {
			String key = "encryption";
			keys = EncryptLogic.generateKey(key);
		} catch (Exception e) {
			throw new SystemException("", "", "", "encryption make key err: " + e);
		}

		String[] data_category = ExecuteMainUtil.splitLeagueInfo(entityList.get(0).getDataCategory());

		String country = data_category[0];
		String league = data_category[1];
		String home = entityList.get(0).getHomeTeamName();
		String away = entityList.get(0).getAwayTeamName();

		// upd_csv_infoチェック
		boolean kmeansUpdFlg = true;
		if (ExistsUpdCsvInfo.exist()) {
			List<List<String>> resultHomeList = ExistsUpdCsvInfo.chkVague25(country, league,
					home, "home");
			List<List<String>> resultAwayList = ExistsUpdCsvInfo.chkVague25(country, league,
					away, "away");
			if (resultHomeList.isEmpty() && resultAwayList.isEmpty()) {
				kmeansUpdFlg = false;
			}
		}

		List<String> selDataAllList = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M024);
		String[] selDataList = new String[selDataAllList.size()];
		for (int i = 0; i < selDataAllList.size(); i++) {
			selDataList[i] = selDataAllList.get(i);
		}

		String where = "country = '" + country + "' and league = '" + league + "' and "
				+ "home = '" + home + "' and away = '" + away + "'";

		List<List<String>> selectResultList = null;
		SqlMainLogic select = new SqlMainLogic();
		try {
			selectResultList = select.executeSelect(null, UniairConst.BM_M024, selDataList,
					where, null, null);
		} catch (Exception e) {
			throw new SystemException("", "", "", "err: " + e);
		}

		for (List<String> data : selectResultList) {
			String score = data.get(6);
			String chkBody = data.get(7);

			List<List<String>> chkList = new ArrayList<>();
			chkList.add(data);

			List<String> conList = new ArrayList<>();
			// PEARSON
			CalcCorrelationDetailOutputDTO calcCorrelationDetailOutputDTO = null;
			if (PEARSON.equals(chkBody)) {
				calcCorrelationDetailOutputDTO = pearsonConvert(chkList);

				if (!calcCorrelationDetailOutputDTO.isRegisterFlg()) {
					continue;
				}

				List<Integer> indices = calcCorrelationDetailOutputDTO.getInducesList();
				List<String> corrStrs = calcCorrelationDetailOutputDTO.getCorrList();

				// フィールド名抽出
				List<String> fieldList = new ArrayList<>();
				CorrelationEntity entity = new CorrelationEntity();
				Field[] fields = entity.getClass().getDeclaredFields();
				for (int i = 0; i < fields.length; i++) {
					if (i < 8)
						continue; // 最初の7つはスキップ（例：メタ情報）
					fieldList.add(fields[i].getName());
				}

				// 相関係数とフィールド名の対応づけ
				for (int i = 0; i < indices.size(); i++) {
					int fieldIndex = indices.get(i); // 元の特徴量インデックス
					String featureName = fieldList.get(fieldIndex);
					conList.add(featureName + "," + corrStrs.get(i));
				}
				registerTeamStaticsCorrData(country, league, home, away, score, chkBody, conList);
				System.out.println(PEARSON + "を登録しました。country: " + country + ", league: " + league +
						", home: " + home + ", away: " + away + ", score: " + score);
			} else if ((KMEANS_CLUSTER.equals(chkBody) || HIERARCHICAL.equals(chkBody))
					&& kmeansUpdFlg) {
 				calcCorrelationDetailOutputDTO = clusterConvert(chkList, keys);
				List<List<String>> homeResultList = calcCorrelationDetailOutputDTO.getRangeResultList();
				List<List<String>> awayResultList = calcCorrelationDetailOutputDTO.getRangeResultOtherList();
				// home
				if (homeResultList != null && !homeResultList.isEmpty()) {
					if (calcCorrelationDetailOutputDTO.isExistFlg()) {
						updateTeamStaticsClusterData(calcCorrelationDetailOutputDTO.getId(), homeResultList);
					} else {
						registerTeamStaticsClusterData(country, league, home, null, score, chkBody, homeResultList);
					}
				}
				// away
				if (awayResultList != null && !awayResultList.isEmpty()) {
					if (calcCorrelationDetailOutputDTO.isExistOtherFlg()) {
						updateTeamStaticsClusterData(calcCorrelationDetailOutputDTO.getOtherId(), awayResultList);
					} else {
						registerTeamStaticsClusterData(country, league, null, away, score, chkBody, awayResultList);
					}
				}
				String msg = (kmeansUpdFlg) ? chkBody + "を更新しました。country: " + country + ", league: " + league +
						", home: " + home + ", away: " + away + ", score: " + score
				:
					chkBody + "を登録しました。country: " + country + ", league: " + league +
							", home: " + home + ", away: " + away + ", score: " + score;
				System.out.println(msg);
			}
		}
	}

	/**
	 * ピアソン相関係数用変換メソッド
	 * @param selectResultList
	 * @return
	 */
	private CalcCorrelationDetailOutputDTO pearsonConvert(List<List<String>> selectResultList) {
		CalcCorrelationDetailOutputDTO calcCorrelationDetailOutputDTO = new CalcCorrelationDetailOutputDTO();
		// 7以降の番号を0始まり換算
		List<Double> corrList = new ArrayList<>();
		List<String> corrStrList = new ArrayList<>();
		int chk = 0;
		for (List<String> corrs : selectResultList) {
			for (String co : corrs) {
				if (chk >= 0 && chk <= 7) {
					chk++;
					continue;
				}
				String corr = co.split(",")[3];
				corrList.add(Double.parseDouble(corr));
				corrStrList.add(corr);
				chk++;
			}
		}

		List<Integer> originalIndices = new ArrayList<>();
		for (int i = 0; i < corrList.size(); i++) {
			originalIndices.add(i);
		}

		// NaN 除外
		List<Integer> filteredIndices = originalIndices.stream()
				.filter(i -> !Double.isNaN(corrList.get(i)))
				.collect(Collectors.toList());

		// 残indexを取得
		originalIndices.removeAll(filteredIndices);
		List<Integer> zanIndices = originalIndices;

		// 全てNaNである場合スキップ
		if (filteredIndices.isEmpty()) {
			System.out.println("ランキングを作成する量がありません。");
			calcCorrelationDetailOutputDTO.setRegisterFlg(false);
			return calcCorrelationDetailOutputDTO;
		}

		System.out.println("対象データは" + filteredIndices.size() + "件です。");

		// インデックスを corrList の値に従ってソート（降順）
		filteredIndices.sort((i1, i2) -> Double.compare(corrList.get(i2), corrList.get(i1)));
		// 残indexを結合
		filteredIndices.addAll(zanIndices);

		// 相関係数を文字列に変換（表示用）
		List<String> corrStrs = filteredIndices.stream()
				.map(i -> String.format("%.5f", corrList.get(i))) // 小数第5位まで
				.collect(Collectors.toList());

		calcCorrelationDetailOutputDTO.setRegisterFlg(true);
		calcCorrelationDetailOutputDTO.setInducesList(filteredIndices);
		calcCorrelationDetailOutputDTO.setCorrList(corrStrs);
		return calcCorrelationDetailOutputDTO;
	}

	/**
	 * cluster用変換メソッド
	 * @param selectResultList
	 * @param existChk
	 * @param keys
	 * @return
	 */
	private CalcCorrelationDetailOutputDTO clusterConvert(List<List<String>> selectResultList,
			SecretKey keys) {
		CalcCorrelationDetailOutputDTO calcCorrelationDetailOutputDTO = new CalcCorrelationDetailOutputDTO();

		// homeまたはawayが同一レコード同士のものを再度DBから取得
		String country = selectResultList.get(0).get(2);
		String league = selectResultList.get(0).get(3);
		String home = selectResultList.get(0).get(4);
		String away = selectResultList.get(0).get(5);
		String score = selectResultList.get(0).get(6);
		String chkBody = selectResultList.get(0).get(7);

		// 6以降の番号を0始まり換算
		boolean chkFlg = false;
		List<Integer> clusterNumberList = new ArrayList<>();
		List<List<Double>> clusterList = new ArrayList<>();
		int chk = 0;
		for (List<String> clusters : selectResultList) {
			for (String clu : clusters) {
				if (chk >= 0 && chk <= 7) {
					chk++;
					continue;
				}
				//System.out.println(chk + ": " + clu);
				List<Double> clusterSubList = new ArrayList<>();
				if ("".equals(clu)) {
					for (int clus = 0; clus <= 3; clus++) {
						clusterSubList.add(0.0);
					}
					clusterList.add(clusterSubList);
				} else if (clu.contains("kwargs")) {
					// 特殊文字が紛れている場合return
					return calcCorrelationDetailOutputDTO;
				} else {
					String[] cluster = clu.split(",");
					for (int clus = 0; clus <= 3; clus++) {
						clusterSubList.add(Double.parseDouble(cluster[clus]));
					}
					clusterList.add(clusterSubList);
					if (!chkFlg) {
						for (int clus = 4; clus < cluster.length; clus++) {
							clusterNumberList.add(Integer.parseInt(cluster[clus]));
							chkFlg = true;
						}
					}
				}
				chk++;
			}
		}

		// homeとawayそれぞれで計算
		for (int team = 0; team <= 1; team++) {
			String db_home = null;
			String db_away = null;
			if (team == 0) {
				db_home = home;
				db_away = null;
			} else {
				db_home = null;
				db_away = away;
			}

			// 初期化(クラスタ0,1,2,3の特徴量平均, 件数, 一致クラスタの件数)
			List<List<Double>> clusterAveAllList = new ArrayList<>();
			for (int i = 0; i < CLUSTER_DATA_COUNT; i++) {
				List<Double> clusterAveList = new ArrayList<>();
				clusterAveList.add(0.0);
				clusterAveList.add(0.0);
				clusterAveList.add(0.0);
				clusterAveList.add(0.0);
				clusterAveAllList.add(clusterAveList);
			}
			List<List<Integer>> counterAllList = new ArrayList<>();
			for (int i = 0; i < CLUSTER_DATA_COUNT; i++) {
				List<Integer> counterList = new ArrayList<>();
				counterList.add(0);
				counterList.add(0);
				counterList.add(0);
				counterList.add(0);
				counterAllList.add(counterList);
			}
			List<List<String>> clusterCountAllList = new ArrayList<>();
			for (int i = 0; i < CLUSTER_DATA_COUNT; i++) {
				List<String> clusterCountList = new ArrayList<>();
				clusterCountList.add("0-0-0-0,0.0");
				clusterCountAllList.add(clusterCountList);
			}

			List<String> resultList = null;
			CalcCorrelationDetailOutputDTO outputDTO = getTeamStatics25Data(country, league, db_home,
					db_away, score, chkBody);
			boolean existFlg = outputDTO.isExistFlg();
			if (existFlg) {
				resultList = outputDTO.getSelectResultList().get(0);
				int index = 0;
				int listIndex = 0;
				for (String origin : resultList) {
					if (index >= 0 && index <= 6) {
						index++;
						continue;
					}
					String[] split = origin.split(",");
					// 初期化情報に上書き(ただし平均値は件数を乗算したものを初期値とする。またMappingは後処理で一括更新されるので上書きなし)
					List<Integer> counterList = counterAllList.get(listIndex);
					counterList.set(0, Integer.parseInt(split[5]));
					counterList.set(1, Integer.parseInt(split[6]));
					counterList.set(2, Integer.parseInt(split[7]));
					counterList.set(3, Integer.parseInt(split[8]));
					counterAllList.set(listIndex, counterList);

					List<Double> clusterAveList = clusterAveAllList.get(listIndex);
					clusterAveList.set(0, Double.parseDouble(split[1]) * counterList.get(0));
					clusterAveList.set(1, Double.parseDouble(split[2]) * counterList.get(1));
					clusterAveList.set(2, Double.parseDouble(split[3]) * counterList.get(2));
					clusterAveList.set(3, Double.parseDouble(split[4]) * counterList.get(3));
					clusterAveAllList.set(listIndex, clusterAveList);

					index++;
					listIndex++;
				}
			}

			// 各特徴量ごとでクラスタ単位の平均データを保持する
			// clusterListの各リストのindexが同クラスタ平均値
			List<Double> cluster0List = new ArrayList<>();
			List<Double> cluster1List = new ArrayList<>();
			List<Double> cluster2List = new ArrayList<>();
			List<Double> cluster3List = new ArrayList<>();
			for (List<Double> clusters : clusterList) {
				// 各クラスタリストは[homeExpInfo, awayExpInfo, ...]の順で並ぶ
				cluster0List.add(clusters.get(0));
				cluster1List.add(clusters.get(1));
				cluster2List.add(clusters.get(2));
				cluster3List.add(clusters.get(3));
			}
			// 近いクラスタ同士でさらにクラスタの平均をとり,データの語尾に代表クラスタを取り付ける
			for (int ave = 0; ave < clusterAveAllList.size(); ave++) {
				List<Double> clusterAveUpdList = clusterAveAllList.get(ave);
				List<Integer> counts = counterAllList.get(ave);
				int count0 = counts.get(0);
				int count1 = counts.get(1);
				int count2 = counts.get(2);
				int count3 = counts.get(3);
				double feature0 = cluster0List.get(ave);
				double feature1 = cluster1List.get(ave);
				double feature2 = cluster2List.get(ave);
				double feature3 = cluster3List.get(ave);
				if (feature0 != 0.0) {
					clusterAveUpdList.set(0, clusterAveUpdList.get(0) + feature0);
					count0++;
				}
				if (feature1 != 0.0) {
					clusterAveUpdList.set(1, clusterAveUpdList.get(1) + feature1);
					count1++;
				}
				if (feature2 != 0.0) {
					clusterAveUpdList.set(2, clusterAveUpdList.get(2) + feature2);
					count2++;
				}
				if (feature3 != 0.0) {
					clusterAveUpdList.set(3, clusterAveUpdList.get(3) + feature3);
					count3++;
				}
				if (count0 != 0) {
					clusterAveUpdList.set(0, clusterAveUpdList.get(0) / count0);
				}
				if (count1 != 0) {
					clusterAveUpdList.set(1, clusterAveUpdList.get(1) / count1);
				}
				if (count2 != 0) {
					clusterAveUpdList.set(2, clusterAveUpdList.get(2) / count2);
				}
				if (count3 != 0) {
					clusterAveUpdList.set(3, clusterAveUpdList.get(3) / count3);
				}
				counts.set(0, count0);
				counts.set(1, count1);
				counts.set(2, count2);
				counts.set(3, count3);
				counterAllList.set(ave, counts);
				clusterAveAllList.set(ave, clusterAveUpdList);
			}

			// クラスタのMapping(一括更新)
			CalcCorrelationDetailOutputDTO outputMappingDTO = getTeamStatics24Data(country, league, db_home,
					db_away, score, chkBody);
			List<List<String>> outputReturnList = outputMappingDTO.getSelectResultList();
			// クラスタの平均値のみ取得し,それぞれのクラスタ平均値で最も近いものを同じクラスタとする
			// home試合時同士,away試合時同士
			Map<String, Map<String, Double>> distAlterMap = new HashMap<String, Map<String, Double>>();
			for (int i = 0; i < outputReturnList.size(); i++) {
				List<String> list1 = outputReturnList.get(i);
				for (int j = i + 1; j < outputReturnList.size(); j++) {
					List<String> list2 = outputReturnList.get(j);
					// 最小距離を保存
					Map<String, Double> distMap = new HashMap<String, Double>();
					// subInd=8(homeExpInfo)から順に確認
					for (int subInd = 8; subInd < list1.size(); subInd++) { // subInd 7以下はスキップ済み
						String[] split1 = list1.get(subInd).split(",");
						String[] split2 = list2.get(subInd).split(",");

						if (split1.length < 4 || split2.length < 4) {
							continue;
						}

						// cluster0, cluster1, cluster2, cluster3
						for (int c1 = 0; c1 < 4; c1++) {
							for (int c2 = 0; c2 < 4; c2++) {
								String distKey = String.valueOf(c1) + "-" + String.valueOf(c2);
								double dist = 0.0;
								if (distMap.containsKey(distKey)) {
									dist = distMap.get(distKey);
								}
								try {
									dist += (Math.abs(Double.parseDouble(split1[c1]) -
											Double.parseDouble(split2[c2]))); // ユークリッド距離（1次元）
								} catch (NumberFormatException e) {
									// NaN や不正値は無視
								}
								distMap.put(distKey, dist);
							}
						}
					}

					// distMapが空
					if (distMap.isEmpty()) {
						continue;
					}

					String distAlterKey = String.valueOf(i) + "-" + String.valueOf(j);
					distAlterMap.put(distAlterKey, distMap);
				}
			}

			// 暗号化
			String encryptKey = null;
			try {
				byte[] encrypt = EncryptLogic.encryptObject(distAlterMap, keys);
				encryptKey = Base64.getEncoder().encodeToString(encrypt);
			} catch (Exception e) {
				throw new SystemException("", "", "", "encryption err: " + e);
			}

			CorrelationEntity entity = new CorrelationEntity();
			Field[] fields = entity.getClass().getDeclaredFields();

			// 返却用リストに格納し,先頭にフィールド名を結合
			List<List<String>> returnList = new ArrayList<>();
			int reg = 0;
			for (Field field : fields) {
				if (reg < 8) {
					reg++;
					continue;
				}
				List<String> returnSubList = new ArrayList<>();
				StringBuilder sb = new StringBuilder();
				sb.append(field.getName());
				List<Double> clustList = clusterAveAllList.get(reg - 8);
				for (Double clu : clustList) {
					if (sb.toString().length() > 0) {
						sb.append(",");
					}
					sb.append(String.valueOf(clu));
				}
				List<Integer> countList = counterAllList.get(reg - 8);
				for (Integer ints : countList) {
					if (sb.toString().length() > 0) {
						sb.append(",");
					}
					sb.append(String.valueOf(ints));
				}
				sb.append(",");
				sb.append(encryptKey);
				returnSubList.add(sb.toString());
				returnList.add(returnSubList);
				reg++;
			}


			if (team == 0) {
				// 存在フラグ設定(true:更新,false:新規登録)
				// id設定
				calcCorrelationDetailOutputDTO.setId(outputDTO.getId());
				calcCorrelationDetailOutputDTO.setExistFlg(existFlg);
				calcCorrelationDetailOutputDTO.setRangeResultList(returnList);
			} else {
				// 存在フラグ設定(true:更新,false:新規登録)
				// id設定
				calcCorrelationDetailOutputDTO.setOtherId(outputDTO.getId());
				calcCorrelationDetailOutputDTO.setExistOtherFlg(existFlg);
				calcCorrelationDetailOutputDTO.setRangeResultOtherList(returnList);
			}
		}
		return calcCorrelationDetailOutputDTO;

	}

	/**
	 * 取得メソッド
	 * @param country 国
	 * @param league リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @param score フラグ
	 * @param chkBody 検証内容
	 */
	private CalcCorrelationDetailOutputDTO getTeamStatics24Data(String country, String league, String home, String away,
			String score,
			String chkBody) {
		CalcCorrelationDetailOutputDTO calcCorrelationDetailOutputDTO = new CalcCorrelationDetailOutputDTO();

		List<String> selDataAllList = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M024);
		String[] selDataList = new String[selDataAllList.size()];
		for (int i = 0; i < selDataAllList.size(); i++) {
			selDataList[i] = selDataAllList.get(i);
		}

		String where = "country = '" + country + "' and league = '" + league + "' "
				+ "and ";

		if (home != null) {
			where += "home = '" + home + "' and ";
		}
		if (away != null) {
			where += "away = '" + away + "' and ";
		}

		where += "score = '" + score + "' and "
				+ "chk_body = '" + chkBody + "'";

		List<List<String>> selectResultList = null;
		SqlMainLogic select = new SqlMainLogic();
		try {
			selectResultList = select.executeSelect(null, UniairConst.BM_M024, selDataList,
					where, null, null);
		} catch (Exception e) {
			throw new SystemException("", "", "", "err: " + e);
		}

		if (!selectResultList.isEmpty()) {
			calcCorrelationDetailOutputDTO.setExistFlg(true);
			calcCorrelationDetailOutputDTO.setSelectResultList(selectResultList);
			return calcCorrelationDetailOutputDTO;
		}
		calcCorrelationDetailOutputDTO.setExistFlg(false);
		return calcCorrelationDetailOutputDTO;
	}

	/**
	 * 取得メソッド
	 * @param country 国
	 * @param league リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @param score フラグ
	 * @param chkBody 検証内容
	 */
	private CalcCorrelationDetailOutputDTO getTeamStatics25Data(String country, String league, String home, String away,
			String score,
			String chkBody) {
		CalcCorrelationDetailOutputDTO calcCorrelationDetailOutputDTO = new CalcCorrelationDetailOutputDTO();

		List<String> selDataAllList = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M025);
		String[] selDataList = new String[selDataAllList.size()];
		for (int i = 0; i < selDataAllList.size(); i++) {
			selDataList[i] = selDataAllList.get(i);
		}

		String where = "country = '" + country + "' and league = '" + league + "' "
				+ "and ";

		if (home != null) {
			where += "home = '" + home + "' and ";
		}
		if (away != null) {
			where += "away = '" + away + "' and ";
		}
		if (score != null) {
			where += "score = '" + score + "' and ";
		}

		where += "chk_body = '" + chkBody + "'";

		List<List<String>> selectResultList = null;
		SqlMainLogic select = new SqlMainLogic();
		try {
			selectResultList = select.executeSelect(null, UniairConst.BM_M025, selDataList,
					where, null, null);
		} catch (Exception e) {
			throw new SystemException("", "", "", "err: " + e);
		}

		if (!selectResultList.isEmpty()) {
			calcCorrelationDetailOutputDTO.setId(selectResultList.get(0).get(0));
			calcCorrelationDetailOutputDTO.setExistFlg(true);
			calcCorrelationDetailOutputDTO.setSelectResultList(selectResultList);
			return calcCorrelationDetailOutputDTO;
		}
		calcCorrelationDetailOutputDTO.setExistFlg(false);
		return calcCorrelationDetailOutputDTO;
	}

	/**
	 * 登録メソッド
	 * @param country 国
	 * @param league リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @param score スコア
	 * @param chkBody 検証内容
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	private void registerTeamStaticsCorrData(String country, String league,
			String home, String away, String score, String chkBody, List<String> corrList)
			throws IllegalArgumentException, IllegalAccessException {
		List<CorrelationDetailEntity> insertEntities = new ArrayList<CorrelationDetailEntity>();
		CorrelationDetailEntity statSummaries = new CorrelationDetailEntity();
		statSummaries.setCountry(country);
		statSummaries.setLeague(league);
		statSummaries.setHome(home);
		statSummaries.setAway(away);
		statSummaries.setScore(score);
		statSummaries.setChkBody(chkBody);

		// フィールド名抽出
		Field[] fields = statSummaries.getClass().getDeclaredFields();
		int ind = 0;
		for (Field field : fields) {
			if (ind < 7) {
				ind++;
				continue; // 最初の7つはスキップ（例：メタ情報）
			}
			String cluster = corrList.get(ind - 7);
			field.setAccessible(true);
			field.set(statSummaries, cluster);
			ind++;
		}

		insertEntities.add(statSummaries);

		CsvRegisterImpl csvRegisterImpl = new CsvRegisterImpl();
		try {
			csvRegisterImpl.executeInsert(UniairConst.BM_M025,
					insertEntities, 1, insertEntities.size());
		} catch (Exception e) {
			System.err.println("correlation_detail_data insert err execute: " + e);
		}
	}

	/**
	 * 登録メソッド
	 * @param country 国
	 * @param league リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @param score スコア
	 * @param chkBody 検証内容
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	private void registerTeamStaticsClusterData(String country, String league,
			String home, String away, String score, String chkBody, List<List<String>> clusterList)
			throws IllegalArgumentException, IllegalAccessException {
		List<CorrelationDetailEntity> insertEntities = new ArrayList<CorrelationDetailEntity>();
		CorrelationDetailEntity statSummaries = new CorrelationDetailEntity();
		statSummaries.setCountry(country);
		statSummaries.setLeague(league);
		statSummaries.setHome(home);
		statSummaries.setAway(away);
		statSummaries.setScore(score);
		statSummaries.setChkBody(chkBody);

		// フィールド名抽出
		Field[] fields = statSummaries.getClass().getDeclaredFields();
		int ind = 0;
		for (Field field : fields) {
			if (ind < 7) {
				ind++;
				continue; // 最初の7つはスキップ（例：メタ情報）
			}
			String cluster = clusterList.get(ind - 7).get(0);
			field.setAccessible(true);
			field.set(statSummaries, cluster);
			ind++;
		}

		insertEntities.add(statSummaries);

		CsvRegisterImpl csvRegisterImpl = new CsvRegisterImpl();
		try {
			csvRegisterImpl.executeInsert(UniairConst.BM_M025,
					insertEntities, 1, insertEntities.size());
		} catch (Exception e) {
			System.err.println("correlation_detail_data insert err execute: " + e);
		}
	}

	/**
	 * 登録メソッド
	 * @param country 国
	 * @param league リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @param score スコア
	 * @param chkBody 検証内容
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	private void updateTeamStaticsClusterData(String id, List<List<String>> clusterList)
			throws IllegalArgumentException, IllegalAccessException {

		StringBuilder sb = new StringBuilder();
		sb.append("id = '" + id + "'");

		String where = sb.toString();

		StringBuilder sbBuilder = new StringBuilder();
		for (int upd = 1; upd <= CLUSTER_DATA_COUNT; upd++) {
			if (sbBuilder.toString().length() > 0) {
				sbBuilder.append(", ");
			}
			String updList = clusterList.get(upd - 1).get(0);
			String suffix = "th";
			if (upd == 1)
				suffix = "st";
			if (upd == 2)
				suffix = "nd";
			if (upd == 3)
				suffix = "rd";
			sbBuilder.append(upd + suffix + "_rank = '" + updList + "'");
		}

		UpdateWrapper updateWrapper = new UpdateWrapper();
		// 更新日時も連結
		sbBuilder.append(", update_time = '" + DateUtil.getSysDate() + "'");
		// 決定した判定結果に更新
		int updateResult = updateWrapper.updateExecute(UniairConst.BM_M025, where,
				sbBuilder.toString());
		// 結果が異常である場合エラー
		if (updateResult == -1) {
			throw new SystemException(
					"",
					"",
					"",
					"更新エラー");
		}
	}

}
