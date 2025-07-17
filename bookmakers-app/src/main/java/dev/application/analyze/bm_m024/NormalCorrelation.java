package dev.application.analyze.bm_m024;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import dev.application.common.constant.CalcCorrelationConst;
import dev.application.common.dto.CalcCorrelationOutputDTO;
import dev.application.common.mapping.CorrelationSummary;
import dev.application.common.util.ExecuteMainUtil;
import dev.application.entity.ThresHoldEntity;
import dev.application.entity.ThresHoldNewEntity;

/**
 * ピアソン相関係数を導出する
 * @author shiraishitoshio
 *
 */
public class NormalCorrelation {

	/**
	 * 実行メソッド
	 * @param allEntityList
	 * @param filteredList
	 * @return
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws SecurityException
	 */
	public List<CorrelationSummary> execute(ThresHoldEntity allEntityList, List<ThresHoldEntity> filteredList)
			throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
		// 初期データを格納する
		List<String> summationOfSecondPowerDataX = new ArrayList<>();
		List<String> summationOfTimesData = new ArrayList<>();
		List<String> summationOfSecondPowerDataY = new ArrayList<>();
		List<String> averageDataX = new ArrayList<>();
		List<Integer> counterDataX = new ArrayList<>();
		List<String> averageDataY = new ArrayList<>();
		List<Integer> counterDataY = new ArrayList<>();

		// 3分割データを成功率,成功数,試行数に分割したデータ用の初期データを作成する
		List<String> init = null;
		try {
			init = initFormatData(allEntityList);
		} catch (Exception e) {
			System.err.println("initFormatData err: " + e);
			throw e;
		}

		for (String initData : init) {
			// 3分割初期データの場合
			if (initData.contains("/")) {
				List<String> split = ExecuteMainUtil.splitGroup(initData);
				for (String sp : split) {
					summationOfSecondPowerDataX.add(sp);
					summationOfTimesData.add(sp);
					summationOfSecondPowerDataY.add(sp);
					averageDataX.add(sp);
					counterDataX.add(0);
					averageDataY.add(sp);
					counterDataY.add(0);
				}
			} else {
				summationOfSecondPowerDataX.add(initData);
				summationOfTimesData.add(initData);
				summationOfSecondPowerDataY.add(initData);
				averageDataX.add(initData);
				counterDataX.add(0);
				averageDataY.add(initData);
				counterDataY.add(0);
			}
		}

		System.out.println("summationOfSecondPowerDataX: " + summationOfSecondPowerDataX.size());
		System.out.println("summationOfTimesData: " + summationOfTimesData.size());
		System.out.println("summationOfSecondPowerDataY: " + summationOfSecondPowerDataY.size());
		System.out.println("averageDataX: " + averageDataX.size());
		System.out.println("averageDataY: " + averageDataY.size());

		// スタッツ情報が掲載されているentityのみ抜き出す
		List<ThresHoldNewEntity> filteredNewList = new ArrayList<ThresHoldNewEntity>();
		for (ThresHoldEntity entity : filteredList) {
			boolean hasStatField = false; // 少なくとも1つでもEXCLUSIVE_LISTに含まれないフィールドがある場合にtrueにする
			// 新しいThresHoldNewEntityを作成する
			ThresHoldNewEntity newEntity = new ThresHoldNewEntity();
			// 元のThresHoldEntityのフィールドを取得
			Field[] fields = entity.getClass().getDeclaredFields();
			for (Field field : fields) {
				field.setAccessible(true); // privateフィールドにもアクセスできるようにする
				String snake = ExecuteMainUtil.convertToSnakeCase(field.getName());
				try {
					// EXCLUSIVE_LISTに含まれないフィールドのみ処理
					if (!ExecuteMainUtil.chkExclusive2(snake)) {
						// フィールドの値を取得
						Object fieldValue = field.get(entity);
						// newEntityの対応するフィールドを取得
						Field newEntityField = newEntity.getClass().getDeclaredField(field.getName());
						newEntityField.setAccessible(true); // newEntityのprivateフィールドにもアクセスできるようにする
						// newEntityの対応するフィールドに値を設定
						newEntityField.set(newEntity, fieldValue);
						hasStatField = true; // 少なくとも一つEXCLUSIVE_LISTに含まれないフィールドが見つかれば
					}
				} catch (NoSuchFieldException | IllegalAccessException e) {
					e.printStackTrace(); // エラーハンドリング
					System.out.println("err: " + e);
					throw e;
				}
			}

			// 少なくとも一つでもEXCLUSIVE_LISTに含まれないフィールドがあれば、新しいEntityをリストに追加
			if (hasStatField) {
				filteredNewList.add(newEntity);
			}
		}

		// スコアフラグを設定(データのレコード数に対して1レコードかつ同一チーム単位で同じフラグが設定されている)
		List<List<String>> scoreFlgData = initScoreFlgData(filteredList);
		scoreFlgData = diffScoreList(scoreFlgData);

		System.out.println("filteredNewList size: " + filteredNewList.size() + ", newEntity size: "
				+ "" + ThresHoldNewEntity.class.getDeclaredFields().length);

		// データ差分を導出
		filteredNewList = diffList(filteredNewList);
		int counter = filteredList.size();

		if (!filteredNewList.isEmpty()) {
			System.out.println("difffilteredNewList size: " + filteredNewList.size() + ", newEntity size: "
					+ "" + ThresHoldNewEntity.class.getDeclaredFields().length);
		}

		if (!scoreFlgData.isEmpty()) {
			System.out.println("scoreFlgData size: " + scoreFlgData.size() + ", scoreFlgSubData size: "
					+ "" + scoreFlgData.get(0).size());
		}

		// スコアでフィルターをかけたデータ群に対してスタッツごとに各種計算
		int corrIndex = 0;
		for (ThresHoldNewEntity entity : filteredNewList) {
			int entityIndex = 0; // entityのindex
			int setIndex = 0; // 3分割も分割した状態のindex
			List<String> scoreFlgSubData = scoreFlgData.get(corrIndex);
			Field[] fields = entity.getClass().getDeclaredFields();
			for (Field field : fields) {
				//				System.out.println("corrIndex: " + corrIndex + ", setIndex: " +
				//						setIndex + ", field: " + field.getName());
				field.setAccessible(true);
				String feature_value = (String) field.get(entity);
				String scoreFlg_value = scoreFlgSubData.get(entityIndex);
				// 3分割データ
				if (feature_value.contains("/") || scoreFlg_value.contains("/")) {
					List<String> split = ExecuteMainUtil.splitGroup(feature_value);
					List<String> split_sco = ExecuteMainUtil.splitFlgGroup(scoreFlg_value);
					for (int sp = 0; sp < split.size(); sp++) {
						System.out.println(
								"corrIndex: " + corrIndex + ", setIndex: " +
										setIndex + ", field: " + field.getName());
						CalcCorrelationOutputDTO dto1 = secondPowerOfSummation(
								averageDataX.get(setIndex),
								split.get(sp),
								counterDataX.get(setIndex));
						CalcCorrelationOutputDTO dto2 = secondPowerOfSummation(
								averageDataY.get(setIndex),
								split_sco.get(sp),
								counterDataY.get(setIndex));
						averageDataX.set(setIndex, dto1.getSecondPowerOfSummation());
						counterDataX.set(setIndex, dto1.getCounter());
						averageDataY.set(setIndex, dto2.getSecondPowerOfSummation());
						counterDataY.set(setIndex, dto2.getCounter());
						setIndex++;
					}
				} else {
					System.out.println(
							"corrIndex: " + corrIndex + ", setIndex: " +
									setIndex + ", field: " + field.getName());
					CalcCorrelationOutputDTO dto1 = secondPowerOfSummation(
							averageDataX.get(setIndex),
							feature_value,
							counterDataX.get(setIndex));
					CalcCorrelationOutputDTO dto2 = secondPowerOfSummation(
							averageDataY.get(setIndex),
							scoreFlg_value,
							counterDataY.get(setIndex));
					averageDataX.set(setIndex, dto1.getSecondPowerOfSummation());
					counterDataX.set(setIndex, dto1.getCounter());
					averageDataY.set(setIndex, dto2.getSecondPowerOfSummation());
					counterDataY.set(setIndex, dto2.getCounter());
					setIndex++;
				}
				entityIndex++;
			}
			corrIndex++;
		}

		// 平均を求める
		for (int sp = 0; sp < averageDataX.size(); sp++) {
			String aveX = averageDataX.get(sp);
			int cntX = counterDataX.get(sp);
			String aveY = averageDataY.get(sp);
			int cntY = counterDataY.get(sp);
			if (aveX.contains("%")) {
				aveX = aveX.replace("%", "");
				aveX = String.valueOf(Double.parseDouble(aveX) / 100);
			}
			if (aveY.contains("%")) {
				aveY = aveY.replace("%", "");
				aveY = String.valueOf(Double.parseDouble(aveY) / 100);
			}
			if (cntX != 0) {
				averageDataX.set(sp, String.valueOf(Double.parseDouble(aveX) / cntX));
			}
			if (cntY != 0) {
				averageDataY.set(sp, String.valueOf(Double.parseDouble(aveY) / cntY));
			}
		}

		corrIndex = 0;
		for (ThresHoldNewEntity entity : filteredNewList) {
			int entityIndex = 0; // entityのindex
			int setIndex = 0; // 3分割も分割した状態のindex
			List<String> scoreFlgSubData = scoreFlgData.get(corrIndex);
			Field[] fields = entity.getClass().getDeclaredFields();
			for (Field field : fields) {
				//				System.out.println("corrIndex: " + corrIndex + ", setIndex: " +
				//						setIndex + ", field: " + field.getName());
				field.setAccessible(true);
				String feature_value = (String) field.get(entity);
				String scoreFlg_value = scoreFlgSubData.get(entityIndex);
				// 3分割データ
				if (feature_value.contains("/") || scoreFlg_value.contains("/")) {
					List<String> split = ExecuteMainUtil.splitGroup(feature_value);
					List<String> split_sco = ExecuteMainUtil.splitFlgGroup(scoreFlg_value);
					for (int sp = 0; sp < split.size(); sp++) {
						//						System.out.println(
						//								"corrIndex: " + corrIndex + ", setIndex: " +
						//										setIndex + ", field: " + field.getName());
						CalcCorrelationOutputDTO dto1 = summationOfSecondPower(
								summationOfSecondPowerDataX.get(setIndex),
								split.get(sp),
								averageDataX.get(sp),
								counterDataX.get(sp));
						String dataX = dto1.getSummationOfSecondPower();
						CalcCorrelationOutputDTO dto2 = summationOfSecondPower(
								summationOfSecondPowerDataY.get(setIndex),
								split_sco.get(sp),
								averageDataY.get(sp),
								counterDataY.get(sp));
						String dataY = dto2.getSummationOfSecondPower();
						CalcCorrelationOutputDTO dto3 = timesOfStatAndScoreFlg(
								summationOfTimesData.get(setIndex),
								split.get(sp),
								split_sco.get(sp),
								averageDataX.get(sp),
								averageDataY.get(sp));
						String dataXY = dto3.getTimesOfStatAndScoreFlg();
						summationOfSecondPowerDataX.set(setIndex, dataX);
						summationOfSecondPowerDataY.set(setIndex, dataY);
						summationOfTimesData.set(setIndex, dataXY);
						setIndex++;
					}
				} else {
					CalcCorrelationOutputDTO dto1 = summationOfSecondPower(
							summationOfSecondPowerDataX.get(setIndex),
							feature_value,
							averageDataX.get(setIndex),
							counterDataX.get(setIndex));
					String dataX = dto1.getSummationOfSecondPower();
					CalcCorrelationOutputDTO dto2 = summationOfSecondPower(
							summationOfSecondPowerDataY.get(setIndex),
							scoreFlg_value,
							averageDataY.get(setIndex),
							counterDataY.get(setIndex));
					String dataY = dto2.getSummationOfSecondPower();
					CalcCorrelationOutputDTO dto3 = timesOfStatAndScoreFlg(
							summationOfTimesData.get(setIndex),
							feature_value,
							scoreFlg_value,
							averageDataX.get(setIndex),
							averageDataY.get(setIndex));
					String dataXY = dto3.getTimesOfStatAndScoreFlg();
					summationOfSecondPowerDataX.set(setIndex, dataX);
					summationOfSecondPowerDataY.set(setIndex, dataY);
					summationOfTimesData.set(setIndex, dataXY);
					setIndex++;
				}
				entityIndex++;
			}
			corrIndex++;
		}

		// 各値を丸める
		List<CorrelationSummary> correlationData = initInstance();
		// 相関係数導出
		for (int index = 0; index < summationOfSecondPowerDataX.size(); index++) {
			double correlation = calcPearsonCorrelation(
					summationOfSecondPowerDataX.get(index),
					summationOfTimesData.get(index),
					summationOfSecondPowerDataY.get(index),
					counter);

			correlationData.get(index).setSummationOfSecondPowerX(
					String.format("%.5f", Double.parseDouble(summationOfSecondPowerDataX.get(index))));
			correlationData.get(index).setSummationOfTimes(
					String.format("%.5f", Double.parseDouble(summationOfTimesData.get(index))));
			correlationData.get(index).setSummationOfSecondPowerY(
					String.format("%.5f", Double.parseDouble(summationOfSecondPowerDataY.get(index))));
			correlationData.get(index).setCorrelation(
					String.format("%.5f", correlation));
			correlationData.get(index).setCount(counter);

			//			if (field_name.contains("home")) {
			//				if ("0".equals(allEntityList.getHomeScore())) {
			//					correlationData.get(index).setCorrelation("無得点のため導出なし");
			//				} else if (field_value == null || "".equals(field_value)) {
			//					correlationData.get(index).setCorrelation("空データのため導出なし");
			//				}
			//			} else if (field_name.contains("away")) {
			//				if ("0".equals(allEntityList.getAwayScore())) {
			//					correlationData.get(index).setCorrelation("無得点のため導出なし");
			//				} else if (field_value == null || "".equals(field_value)) {
			//					correlationData.get(index).setCorrelation("空データのため導出なし");
			//				}
			//			}
		}

		return correlationData;
	}

	/**
	 * 2乗の和を導出する
	 * @param origin 加算用変数
	 * @param target 対象データ
	 */
	private CalcCorrelationOutputDTO summationOfSecondPower(String origin, String target, String ave, int counter) {
		CalcCorrelationOutputDTO dto = new CalcCorrelationOutputDTO();
		if (origin.contains("%")) {
			origin = origin.replace("%", "");
			origin = String.valueOf(Double.parseDouble(origin) / 100);
		}
		if (target.contains("%")) {
			target = target.replace("%", "");
			target = String.valueOf(Double.parseDouble(target) / 100);
		}
		if (ave.contains("%")) {
			ave = ave.replace("%", "");
			ave = String.valueOf(Double.parseDouble(ave) / 100);
		}
		if ("".equals(target)) {
			dto.setSummationOfSecondPower(origin);
			return dto;
		}
		double originDouble = 0.0;
		double compDouble = 0.0;
		double aveDouble = 0.0;
		try {
			originDouble = Double.parseDouble(origin);
			compDouble = Double.parseDouble(target);
			aveDouble = Double.parseDouble(ave);
			originDouble += Math.pow((compDouble - aveDouble), 2);
			dto.setSummationOfSecondPower(String.valueOf(originDouble));
		} catch (NumberFormatException e) {
			System.err.println("summationOfSecondPower NumberFormatException: " + origin + ", " + target + ", " + ave);
			e.printStackTrace(); // ここでエラースタックトレースを表示
			dto.setSummationOfSecondPower(origin);
		} catch (Exception e) {
			System.err.println(
					"summationOfSecondPower originDouble: " + origin + ", compDouble: " + target + ", "
							+ "aveDouble: " + ave + ", err: " + e);
			dto.setSummationOfSecondPower(origin);
		}
		return dto;
	}

	/**
	 * 加算する(和の2乗を導出する際の途中式として使用)
	 * @param origin 加算用変数
	 * @param target 対象データ
	 */
	private CalcCorrelationOutputDTO secondPowerOfSummation(String origin, String target, int counter) {
		CalcCorrelationOutputDTO dto = new CalcCorrelationOutputDTO();
		if (origin.contains("%")) {
			origin = origin.replace("%", "");
			origin = String.valueOf(Double.parseDouble(origin) / 100);
		}
		if (target.contains("%")) {
			target = target.replace("%", "");
			target = String.valueOf(Double.parseDouble(target) / 100);
		}
		if ("".equals(target)) {
			dto.setSecondPowerOfSummation(origin);
			dto.setCounter(counter);
			return dto;
		}
		double originDouble = 0.0;
		double compDouble = 0.0;
		try {
			originDouble = Double.parseDouble(origin);
			compDouble = Double.parseDouble(target);
			originDouble += compDouble;
			dto.setSecondPowerOfSummation(String.valueOf(originDouble));
			dto.setCounter(counter + 1);
		} catch (NumberFormatException e) {
			System.err.println("secondPowerOfSummation NumberFormatException: " + origin + ", " + target);
			e.printStackTrace(); // ここでエラースタックトレースを表示
			dto.setSecondPowerOfSummation(origin);
			dto.setCounter(counter);
		} catch (Exception e) {
			System.err.println(
					"secondPowerOfSummation originDouble: " + origin + ", compDouble: " + target + ", err: " + e);
			dto.setSecondPowerOfSummation(origin);
			dto.setCounter(counter);
		}
		return dto;
	}

	/**
	 * 特徴量とスコアフラグの積を導出する
	 * @param origin 加算用変数
	 * @param stat1 特徴量
	 * @param stat2 特徴量
	 * @param ave1 平均
	 * @param ave2 平均
	 * @param scoreFlg 得点:1, 無得点:0
	 */
	private CalcCorrelationOutputDTO timesOfStatAndScoreFlg(String origin, String stat1, String stat2,
			String ave1, String ave2) {
		CalcCorrelationOutputDTO dto = new CalcCorrelationOutputDTO();
		if (origin.contains("%")) {
			origin = origin.replace("%", "");
			origin = String.valueOf(Double.parseDouble(origin) / 100);
		}
		if (stat1.contains("%")) {
			stat1 = stat1.replace("%", "");
			stat1 = String.valueOf(Double.parseDouble(stat1) / 100);
		}
		if (stat2.contains("%")) {
			stat2 = stat2.replace("%", "");
			stat2 = String.valueOf(Double.parseDouble(stat2) / 100);
		}
		if (ave1.contains("%")) {
			ave1 = ave1.replace("%", "");
			ave1 = String.valueOf(Double.parseDouble(ave1) / 100);
		}
		if (ave2.contains("%")) {
			ave2 = ave2.replace("%", "");
			ave2 = String.valueOf(Double.parseDouble(ave2) / 100);
		}
		if ("".equals(stat1) || "".equals(stat2)) {
			dto.setTimesOfStatAndScoreFlg(origin);
			return dto;
		}
		double originDouble = 0.0;
		double stat1Double = 0.0;
		double stat2Double = 0.0;
		double ave1Double = 0.0;
		double ave2Double = 0.0;
		try {
			originDouble = Double.parseDouble(origin);
			stat1Double = Double.parseDouble(stat1);
			stat2Double = Double.parseDouble(stat2);
			ave1Double = Double.parseDouble(ave1);
			ave2Double = Double.parseDouble(ave2);
			originDouble += ((stat1Double - ave1Double) * (stat2Double - ave2Double));
			dto.setTimesOfStatAndScoreFlg(String.valueOf(originDouble));
		} catch (NumberFormatException e) {
			System.err.println("timesOfStatAndScoreFlg NumberFormatException: " +
					origin + ", " + stat1 + ", " + stat2);
			e.printStackTrace(); // ここでエラースタックトレースを表示
			dto.setTimesOfStatAndScoreFlg(origin);
		} catch (Exception e) {
			System.err.println("timesOfStatAndScoreFlg originDouble: " + origin + ", "
					+ "stat1Double: " + stat1 + ", stat2Double: " + stat2 + ", err: " + e);
			dto.setTimesOfStatAndScoreFlg(origin);
		}
		return dto;
	}

	/**
	 * ピアソン相関係数を導出
	 * @param summationOfSecondPowerDataX スタッツの2乗の和
	 * @param summationOfTimesData スタッツとスコアフラグの積
	 * @param summationOfSecondPowerDataY スコアフラグの2乗の和
	 * @param counter 件数
	 */
	private double calcPearsonCorrelation(
			String summationOfSecondPowerDataX,
			String summationOfTimesData,
			String summationOfSecondPowerDataY,
			int counter) {

		if (summationOfSecondPowerDataX.contains("%")) {
			summationOfSecondPowerDataX = summationOfSecondPowerDataX.replace("%", "");
			summationOfSecondPowerDataX = String.valueOf(Double.parseDouble(summationOfSecondPowerDataX) / 100);
		}
		double summationOfSecondPowerDataXD = Double.parseDouble(summationOfSecondPowerDataX);
		if (summationOfTimesData.contains("%")) {
			summationOfTimesData = summationOfTimesData.replace("%", "");
			summationOfTimesData = String.valueOf(Double.parseDouble(summationOfTimesData) / 100);
		}
		double summationOfTimesDataD = Double.parseDouble(summationOfTimesData);
		if (summationOfSecondPowerDataY.contains("%")) {
			summationOfSecondPowerDataY = summationOfSecondPowerDataY.replace("%", "");
			summationOfSecondPowerDataY = String.valueOf(Double.parseDouble(summationOfSecondPowerDataY) / 100);
		}
		double summationOfSecondPowerDataYD = Double.parseDouble(summationOfSecondPowerDataY);
		double correlation = 0.0;
		if (summationOfSecondPowerDataXD == 0.0 || summationOfSecondPowerDataYD == 0.0) {
			correlation = Double.NaN;
		} else {
			correlation = summationOfTimesDataD
					/ Math.sqrt(summationOfSecondPowerDataXD * summationOfSecondPowerDataYD);
		}
		return correlation;
	}

	/**
	 * 計算用初期化データフォーマット設定
	 * @param ThresHoldEntity
	 * @return
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	private List<String> initFormatData(ThresHoldEntity entity)
			throws IllegalArgumentException, IllegalAccessException {
		List<String> initData = new ArrayList<>();
		int i = 0;
		Field[] fields = entity.getClass().getDeclaredFields();
		for (Field field : fields) {
			field.setAccessible(true);
			if ((i >= 0 && i <= 9) ||
					i >= 62) {
				i++;
				continue;
			} else {
				String feature_value = (String) field.get(entity);
				// データがない場合もあるため特定の特徴量の場合は強制的に分割用初期データに設定
				if (i >= 50 && i <= 57) {
					String data = feature_value;
					if (feature_value.contains("/")) {
						data = "0.0% (0.0/0.0)";
						// %のみのデータ
					} else if ("".equals(feature_value)) {
						data = "0.0% (0.0/0.0)";
						// %のみのデータ
					} else if (feature_value.contains("%")) {
						data += " (0.0/0.0)";
					}
					initData.add(data);
				} else if (feature_value.contains("%")) {
					initData.add("0.00%");
				} else {
					initData.add("0.0");
				}
				i++;
			}
		}
		return initData;
	}

	/**
	 * フラグデータ
	 * @param ThresHoldEntity
	 * @return
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	private List<List<String>> initScoreFlgData(List<ThresHoldEntity> filteredList)
			throws IllegalArgumentException, IllegalAccessException {
		List<List<String>> flgAllData = new ArrayList<>();
		// スコアフラグを設定する
		String homeScoreBef = "";
		String awayScoreBef = "";
		for (ThresHoldEntity entity : filteredList) {
			String homeScoreFlg = "0";
			String awayScoreFlg = "0";
			if (!"".equals(homeScoreBef) && !homeScoreBef.equals(entity.getHomeScore())) {
				homeScoreFlg = "1";
			}
			if (!"".equals(awayScoreBef) && !awayScoreBef.equals(entity.getAwayScore())) {
				awayScoreFlg = "1";
			}

			Field[] fields = entity.getClass().getDeclaredFields();
			List<String> flgData = new ArrayList<>();

			int i = 0;
			for (Field field : fields) {
				field.setAccessible(true);
				// フィールド名とフィールド内の値取得
				String feature_name = field.getName();
				if ((i >= 0 && i <= 9) ||
						i >= 62) {
					i++;
					continue;
				} else {
					// データがない場合もあるため特定の特徴量の場合は強制的に分割用初期データに設定
					if (i >= 50 && i <= 57) {
						if (feature_name.contains("home")) {
							flgData.add(homeScoreFlg + " (" + homeScoreFlg + "/" + homeScoreFlg + ")");
						} else if (feature_name.contains("away")) {
							flgData.add(awayScoreFlg + " (" + awayScoreFlg + "/" + awayScoreFlg + ")");
						}
					} else {
						if (feature_name.contains("home")) {
							flgData.add(homeScoreFlg);
						} else if (feature_name.contains("away")) {
							flgData.add(awayScoreFlg);
						}
					}
					i++;
				}
			}

			homeScoreBef = entity.getHomeScore();
			awayScoreBef = entity.getAwayScore();

			flgAllData.add(flgData);
		}
		return flgAllData;
	}

	/**
	 * インスタンス化する
	 * @param stat
	 * @return
	 */
	private List<CorrelationSummary> initInstance() {
		List<CorrelationSummary> corrList = new ArrayList<>();
		for (int i = 0; i < CalcCorrelationConst.COUNTER; i++) {
			corrList.add(new CorrelationSummary(null, null, null, null, 0));
		}
		return corrList;
	}

	/**
	 * データの差分を導出
	 * @param filteredList
	 * @return
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws SecurityException
	 * @throws NoSuchFieldException
	 */
	private List<ThresHoldNewEntity> diffList(List<ThresHoldNewEntity> filteredList)
			throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
		List<ThresHoldNewEntity> diffList = new ArrayList<>();
		for (int ind = 0; ind < filteredList.size() - 1; ind++) {
			ThresHoldNewEntity befEntity = filteredList.get(ind);
			ThresHoldNewEntity afEntity = filteredList.get(ind + 1);
			Field[] befFields = befEntity.getClass().getDeclaredFields();
			Field[] afFields = afEntity.getClass().getDeclaredFields();
			String data = null;
			ThresHoldNewEntity newDiffEntity = new ThresHoldNewEntity();
			for (int fiInd = 0; fiInd < befFields.length; fiInd++) {
				System.out.println("ind: " + ind + ", fiInd: " + fiInd);
				Field befField = befFields[fiInd];
				Field afField = afFields[fiInd];
				befField.setAccessible(true);
				afField.setAccessible(true);
				// フィールドの値を取得
				String befFieldValue = (String) befField.get(befEntity);
				String afFieldValue = (String) afField.get(afEntity);
				// 片方が空の場合
				if ("".equals(befFieldValue)) {
					befFieldValue = afFieldValue;
				} else if ("".equals(afFieldValue)) {
					afFieldValue = befFieldValue;
				}
				if (befFieldValue.contains("/") && afFieldValue.contains("/")) {
					List<String> connOrigin = new ArrayList<>();
					List<String> splitBefData = ExecuteMainUtil.splitGroup(befFieldValue);
					List<String> splitAfData = ExecuteMainUtil.splitGroup(afFieldValue);
					for (int inds = 0; inds < splitBefData.size(); inds++) {
						String befData = splitBefData.get(inds);
						String afData = splitAfData.get(inds);
						String diff = diffDef(befData, afData);
						connOrigin.add(diff);
					}
					data = connOrigin.get(0) + " (" + connOrigin.get(1)
							+ "/" + connOrigin.get(2) + ")";
				} else {
					data = diffDef(befFieldValue, afFieldValue);
				}
				Field newEntityField = newDiffEntity.getClass().getDeclaredField(befField.getName());
				newEntityField.setAccessible(true); // newEntityのprivateフィールドにもアクセスできるようにする
				newEntityField.set(newDiffEntity, data);
			}
			diffList.add(newDiffEntity);
		}
		return diffList;
	}

	/**
	 * 直前のデータの差分をとる
	 * @param bef
	 * @param af
	 * @return
	 */
	private String diffDef(String bef, String af) {
		String integer = "";
		String remarks = "";
		if (bef.contains("%")) {
			remarks = "%";
			bef = bef.replace("%", "");
		}
		if (af.contains("%")) {
			remarks = "%";
			af = af.replace("%", "");
		}
		if (!"".equals(bef) && !bef.contains(".")) {
			integer += "ok";
		}
		if (!"".equals(af) && !af.contains(".")) {
			integer += "ok";
		}
		if ("ok".equals(integer)) {
			if ("".equals(bef)) {
				bef = "0";
			}
			if ("".equals(af)) {
				af = "0";
			}
		}
		if (integer.contains("ok")) {
			return String.valueOf((int) (Double.parseDouble(af) - Double.parseDouble(bef))) + remarks;
		}
		if ("".equals(bef) || "".equals(af)) {
			return "0" + remarks;
		}
		return String.valueOf(Double.parseDouble(af) - Double.parseDouble(bef)) + remarks;
	}

	/**
	 * スコアデータの差分を導出
	 * @param filteredList
	 * @return
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws SecurityException
	 * @throws NoSuchFieldException
	 */
	private List<List<String>> diffScoreList(List<List<String>> scoreList)
			throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
		List<List<String>> diffList = new ArrayList<>();
		for (int ind = 0; ind < scoreList.size() - 1; ind++) {
			List<String> befList = scoreList.get(ind);
			List<String> afList = scoreList.get(ind + 1);
			String data = null;
			List<String> scoreSubList = new ArrayList<>();
			for (int fiInd = 0; fiInd < befList.size(); fiInd++) {
				//System.out.println("ind: " + ind + ", fiInd: " + fiInd);
				// フィールドの値を取得
				String befFieldValue = befList.get(fiInd);
				String afFieldValue = afList.get(fiInd);
				if (befFieldValue.contains("/") && afFieldValue.contains("/")) {
					List<String> connOrigin = new ArrayList<>();
					List<String> splitBefData = ExecuteMainUtil.splitFlgGroup(befFieldValue);
					List<String> splitAfData = ExecuteMainUtil.splitFlgGroup(afFieldValue);
					for (int inds = 0; inds < splitBefData.size(); inds++) {
						String befData = splitBefData.get(inds);
						String afData = splitAfData.get(inds);
						String diff = diffDef(befData, afData);
						connOrigin.add(diff);
					}
					data = connOrigin.get(0) + " (" + connOrigin.get(1)
							+ "/" + connOrigin.get(2) + ")";
				} else {
					data = diffDef(befFieldValue, afFieldValue);
				}
				scoreSubList.add(data);
			}
			diffList.add(scoreSubList);
		}
		return diffList;
	}

}
