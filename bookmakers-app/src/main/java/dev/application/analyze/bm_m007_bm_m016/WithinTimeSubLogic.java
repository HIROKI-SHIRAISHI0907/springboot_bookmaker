package dev.application.analyze.bm_m007_bm_m016;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import dev.application.analyze.common.entity.ThresHoldEntity;
import dev.application.analyze.common.util.ExecuteMainUtil;
import dev.common.constant.UniairConst;
import dev.common.maketext.MakeTextConst;

/**
 * 特定の時間までに得点が入っているデータを調査するサブロジック
 * <p>
 * 1. 20分以内にどちらか1点とっている場合に,合計2点目が入るレコード<br>
 * 2. ハーフタイム時にスコアレスになっている場合に,得点が入るレコード<br>
 * を取得する。
 * </p>
 * @author shiraishitoshio
 *
 */
public class WithinTimeSubLogic {

	/**
	 * @param entityList CSV読み込みEntityリスト
	 * @param halfList ハーフリスト
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws IOException
	 * @throws InvalidFormatException
	 * @throws InterruptedException
	 */
	public List<Integer> execute(List<ThresHoldEntity> entityList, List<Integer> searchList)
			throws IllegalArgumentException, IllegalAccessException, InvalidFormatException, IOException, InterruptedException {

		// 特徴量に当たる論理名を取得
		List<String> ronriList = UniairColumnMapUtil.getWhichKeyValueMap(UniairConst.BM_M001, "連番");
		// 特徴量に当たる物理名を取得
		List<String> butsuriList = UniairColumnMapUtil.getWhichKeyValueMap(UniairConst.BM_M001, "seq");

		System.out.println("searchList: " + searchList);

		List<Integer> newSearchList = new ArrayList<Integer>();
		Integer search1 = searchList.get(0);
		Integer search2 = searchList.get(1);

		List<String> seq20List = new ArrayList<String>();
		List<String> seq45List = new ArrayList<String>();

		String flg = "";

		// entityをループし,特定のスコアの閾値試合時間を取得する
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
			double convert_time_range = ExecuteMainUtil.convertToMinutes(game_time);

			String seq = entity.getSeq();

			seq20List.add(seq);
			seq45List.add(seq);

			// ホーム > 0, アウェー = 0 or ホーム = 0, アウェー > 0 20:00以内
			if (((home_score == 1 && away_score == 0) || (home_score == 0 && away_score == 1))
					&& ((int) convert_time_range <= 20)) {

				boolean chkFlg = false;
				for (ThresHoldEntity subEntity : entityList) {

					if (seq20List.contains(subEntity.getSeq())) {
						continue;
					}

					int home_sub_score = Integer.parseInt(subEntity.getHomeScore());
					int away_sub_score = Integer.parseInt(subEntity.getAwayScore());

					if (home_sub_score == 1 && away_sub_score == 1) {
						flg = "20minutes_same";
						chkFlg = true;
						break;
					} else if (home_sub_score == 2 && away_sub_score == 0) {
						flg = "20minutes_home";
						chkFlg = true;
						break;
					} else if (home_sub_score == 0 && away_sub_score == 2) {
						flg = "20minutes_away";
						chkFlg = true;
						break;
					}
				}

				if (chkFlg) {
					//String convert_time = ExecuteMain.classifyMatchTime(game_time);
					String book_name_suffix = country + "-" + league + "-HomeScore_" + home_score
							+ "-AwayScore_" + away_score + "_WithIn20minute";
					executeWithinTimeMain(entity, country, league, game_time,
							ronriList, butsuriList, book_name_suffix,
							MakeTextConst.WITHIN_TIME_EACH, seq, search1, flg);

					search1++;

					//book_name_suffix = "HomeScore_" + home_score
					//		+ "-AwayScore_" + away_score + "_WithIn20minute";
					//executeWithinTimeMain(entity, country, league, game_time,
					//		ronriList, butsuriList, book_name_suffix,
					//		MakeTextConst.WITHIN_TIME_ALL, seq);
				}
				break;
			}

			// ホーム = 0, アウェー = 0 ハーフタイム
			else if ((home_score == 0 && away_score == 0) && ((int) convert_time_range >= 45 && (int) convert_time_range < 48)) {

				boolean chkFlg = false;
				for (ThresHoldEntity subEntity : entityList) {

					if (seq45List.contains(subEntity.getSeq())) {
						continue;
					}

					int home_sub_score = Integer.parseInt(subEntity.getHomeScore());
					int away_sub_score = Integer.parseInt(subEntity.getAwayScore());

					if (home_sub_score == 1 && away_sub_score == 0) {
						flg = "45minutes_home";
						chkFlg = true;
						break;
					} else if (home_sub_score == 0 && away_sub_score == 1) {
						flg = "45minutes_away";
						chkFlg = true;
						break;
					}
				}

				if (chkFlg) {
					//String convert_time = ExecuteMain.classifyMatchTime(game_time);
					String book_name_suffix = country + "-" + league + "-HomeScore_" + home_score
							+ "-AwayScore_" + away_score + "_WithIn45minute";

					executeWithinTimeMain(entity, country, league, game_time,
							ronriList, butsuriList, book_name_suffix,
							MakeTextConst.WITHIN_TIME_EACH, seq, search2, flg);

					search2++;
					//book_name_suffix = "HomeScore_" + home_score
					//		+ "-AwayScore_" + away_score + "_WithIn45minute";
					//executeWithinTimeMain(entity, country, league, game_time,
					//		ronriList, butsuriList, book_name_suffix,
					//		MakeTextConst.WITHIN_TIME_ALL, seq);
				}
				break;
			}
		}
		newSearchList.add(search1);
		newSearchList.add(search2);

		return newSearchList;
	}

	/**
	 * メインロジック
	 * @param entity エンティティ
	 * @param game_time
	 * @param featureMap
	 * @param ronriList
	 * @param butsuriList
	 * @param book_name_suffix
	 * @param data_category_list
	 * @param dataInitList
	 * @param scoredWhich1
	 * @throws InvalidFormatException
	 * @throws IOException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws InterruptedException
	 */
	public static void executeWithinTimeMain(ThresHoldEntity entity, String country, String league,
			String game_time,
			List<String> ronriList, List<String> butsuriList,
			String book_name_suffix,
			String file_checker,
			String seq,
			Integer search,
			String flg)
			throws InvalidFormatException, IOException, IllegalArgumentException, IllegalAccessException, InterruptedException {

		// この試合時間における各ホームの特徴量とアウェーの特徴量を取得
		Field[] fields = entity.getClass().getDeclaredFields();

		// フィールドごとに処理
		Map<String, String> featureMap = new LinkedHashMap<String, String>();
		for (Field field : fields) {
			field.setAccessible(true);
			// フィールド名とフィールド内の値取得
			String feature_name = field.getName();
			// フィールド名をスネーク方式に変更
			String snake = ExecuteMainUtil.convertToSnakeCase(feature_name);

			// 除去リストに入っていたらスキップ
			if (ExecuteMainUtil.chkExclusive(snake)) {
				continue;
			}

			// 特定のフィールドの値を取得し、論理名とともにマップに格納
			String feature_value = (String) field.get(entity);
			String ronri = ExecuteMainUtil.getFeatureRonriField(snake, ronriList, butsuriList);
			featureMap.put(ronri, feature_value);
		}

		// マップに格納されているfeature_valueを整理しやすいように入れ替える
		Map<String, String> newFeatureMap = new LinkedHashMap<String, String>();
		for (Map.Entry<String, String> feaEntry : featureMap.entrySet()) {
			if (feaEntry.getValue() == null || "".equals(feaEntry.getValue()) ||
					"0".equals(feaEntry.getValue()) || "0.0".equals(feaEntry.getValue())) {
				continue;
			}
			// 3データが同一値になっている場合(成功と試行のみ取得)(34%(3/7)などの形式)
			if (ExecuteMainUtil.chkSplit(feaEntry.getKey())) {
				List<String> split_feature = ExecuteMainUtil.splitGroup(feaEntry.getValue());
				newFeatureMap.put(feaEntry.getKey() + "_成功", split_feature.get(1));
				newFeatureMap.put(feaEntry.getKey() + "_試行", split_feature.get(2));
			} else {
				newFeatureMap.put(feaEntry.getKey(), feaEntry.getValue());
			}
		}

		// newFeatureMapのvalue側を調べて閾値を調べる(具体的には切り捨てを行う)
		Map<String, Map<String, Integer>> countMap = new HashMap<>();
		for (Map.Entry<String, String> newFeaEntry : newFeatureMap.entrySet()) {
			// すでにcountMapにそのkeyがあるか確認して取得
			Map<String, Integer> countSubMap = countMap.getOrDefault(newFeaEntry.getKey(), new HashMap<>());
			// まずはその値が整数か小数かパーセンテージ表記かを調べ、閾値を調査、マップへと格納する。
			String threshold_value = ExecuteMainUtil.checkNumberTypeAndFloor(newFeaEntry.getValue());
			countSubMap.put(threshold_value, countSubMap.getOrDefault(threshold_value, 0) + 1);
			// ↓ 閾値順にソート（数値部分だけ取り出して）
			Map<String, Integer> sortedSubMap = countSubMap.entrySet()
					.stream()
					.sorted(Comparator.comparingDouble(e -> ExecuteMainUtil.extractNumericValue(e.getKey())))
					.collect(
							LinkedHashMap::new,
							(m, e) -> m.put(e.getKey(), e.getValue()),
							LinkedHashMap::putAll);
			countMap.put(newFeaEntry.getKey(), sortedSubMap);
		}

		// ソートをまとめて最後に適用（任意）
		Map<String, Map<String, Integer>> sortedCountMap = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, Integer>> entry : countMap.entrySet()) {
			Map<String, Integer> sortedSubMap = entry.getValue().entrySet()
					.stream()
					.sorted(Comparator.comparingDouble(e -> ExecuteMainUtil.extractNumericValue(e.getKey())))
					.collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()),
							LinkedHashMap::putAll);
			// 特徴量をキーに持つ、閾値とその数の組み合わせを持ったマップ完成（閾値は特徴量単位でソート）
			sortedCountMap.put(entry.getKey(), sortedSubMap);
		}

		//System.out.println("sortedCountMap: " + sortedCountMap);

		WithInDbInsert withInDbInsert = new WithInDbInsert();
		withInDbInsert.execute(seq, search, flg);

		// ブック更新(ブックsuffix名, dataInitList, 一意キー(国とカテゴリ))を引数にする
		//WithInTimeBook withInTimeBook = new WithInTimeBook(file_checker);
		//withInTimeBook.makeBook(country, league, game_time, 1, book_name_suffix, sortedCountMap,
		//		file_checker);
	}

}
