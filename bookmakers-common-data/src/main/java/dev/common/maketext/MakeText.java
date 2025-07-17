package dev.common.maketext;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class MakeText {

	/**
	 * コンストラクタ作成禁止
	 */
	private MakeText() {

	}

	/**
	 * 新しいテキストファイルを作成
	 * @param filePath 作成するエクセルファイルのパス
	 * @throws IOException ファイル作成時にエラーが発生した場合
	 * @throws InterruptedException
	 */
	public static void createTextFile(String filePath) throws IOException, InterruptedException {
		File file = new File(filePath);

		try {
			if (file.createNewFile()) {
				//System.out.println("空ファイルを作成しました: " + file.getAbsolutePath());
			} else {
				//System.out.println("すでにファイルが存在しています: " + file.getAbsolutePath());
			}
		} catch (IOException e) {
			System.err.println("ファイル作成時のエラー: " + e.getMessage());
		}

		//Thread.sleep(2000);
	}

	/**
	 * ヘッダーを追加する
	 * @param filePath 存在するエクセルファイルのパス
	 * @param headers ヘッダー
	 * @throws Exception
	 */
	public static void addHeader(String filePath, String headers) throws Exception {
		// エクセルファイルを読み込む
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
			writer.write(headers);
			writer.newLine(); // 改行を入れる（必要に応じて）
			//System.out.println("追記が完了しました。");
		} catch (IOException e) {
			System.err.println("ファイル書き込みエラー: " + e.getMessage());
		}

		//Thread.sleep(500);
	}

	/**
	 * テキストファイルの最後の行にデータを追加するメソッド
	 *
	 * @param filePath エクセルファイルのパス
	 * @param newRecord 追加するデータ（配列）
	 * @throws IOException ファイル作成時にエラーが発生した場合
	 * @throws InterruptedException
	 */
	public static void addRecord(String filePath, List<String> newRecord) throws IOException, InterruptedException {
		// エクセルファイルを読み込む
		int row = 1;
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
			for (String record : newRecord) {
				writer.write(record);
				writer.newLine(); // 改行を入れる（必要に応じて）
				row++;
			}
			//System.out.println("追記が完了しました。");
		} catch (IOException e) {
			System.err.println("ファイル書き込みエラー: " + row + "行目 " + e.getMessage());
		}

		//Thread.sleep(500);
	}

	/**
	 * テキストファイル内のデータを更新するメソッド
	 *
	 * @param filePath エクセルファイルのパス
	 * @param new_filePath データを新規追加するファイルのパス
	 * @param updateRecord 更新するデータ（配列）
	 * @param file_checker 作成ファイルチェッカー
	 * @throws Exception
	 */
	public static void updateRecord(String filePath,
			List<String> updateRecord, String file_checker)
			throws Exception {

		List<String> recordList = new ArrayList<String>();
		String head;
		int readLine = 2;
		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
			String line;

			head = reader.readLine();

			while ((line = reader.readLine()) != null) {
				//System.out.printf("%d行目: %s%n", lineNumber++, line);
				recordList.add(line);
			}
		} catch (IOException e) {
			System.err.println("ファイル読み込みエラー: " + readLine + "行目 " + e.getMessage());
			return;
		}

		if (recordList.size() == 0) {
			System.err.println("ファイル読み込みエラー: " + readLine + "レコード件数が0");
			return;
		}

		// recordList, updateRecordを照合し,存在する試合時間と特徴量と閾値の組み合わせがrecordListにあれば更新,なければ追加
		List<String> newRecordList = new ArrayList<String>();
		List<String> tmpList = new ArrayList<String>();
		System.out.println("head: " + head);
		System.out.println("file_checker: " + file_checker);
		System.out.println("ファイルrecordList: " + recordList.get(0));
		System.out.println("ファイルupdateRecord: " + updateRecord.get(0));
//		System.out.println("データrecordList: " + recordList.get(0));
//		System.out.println("データupdateRecord: " + updateRecord.get(0));
//		System.out.println("file_checker: " + file_checker);
		for (int i = 0; i < recordList.size(); i++) {
			for (int j = 0; j < updateRecord.size(); j++) {
				String record = recordList.get(i);
				String update = updateRecord.get(j);
				String[] record_split = record.split(",");
				String[] update_split = update.split(",");
				// 国,リーグ,試合時間,特徴量,閾値が一致
				if (MakeTextConst.EACH_FEATURE.equals(file_checker)) {
					if (record_split[0].equals(update_split[0]) &&
							record_split[1].equals(update_split[1]) &&
							record_split[2].equals(update_split[2]) &&
							record_split[3].equals(update_split[3]) &&
							record_split[4].equals(update_split[4]) &&
							!tmpList.contains(record)) {
						List<String> updList = addTargetAndCounterAndRatio(
								record_split[5], record_split[6]);
						StringBuilder stringBuilder = new StringBuilder();
						stringBuilder.append(record_split[0])
								.append(",")
								.append(record_split[1])
								.append(",")
								.append(record_split[2])
								.append(",")
								.append(record_split[3])
								.append(",")
								.append(record_split[4])
								.append(",")
								.append(updList.get(0))
								.append(",")
								.append(updList.get(1))
								.append(",")
								.append(updList.get(2));
						newRecordList.add(stringBuilder.toString());
					} else {
						if (!tmpList.contains(record)) {
							List<String> updList = addCounterAndRatio(
									record_split[5], record_split[6]);
							StringBuilder stringBuilder = new StringBuilder();
							stringBuilder.append(record_split[0])
									.append(",")
									.append(record_split[1])
									.append(",")
									.append(record_split[2])
									.append(",")
									.append(record_split[3])
									.append(",")
									.append(record_split[4])
									.append(",")
									.append(updList.get(0))
									.append(",")
									.append(updList.get(1))
									.append(",")
									.append(updList.get(2));
							newRecordList.add(stringBuilder.toString());
							tmpList.add(record);
						}
						if (!tmpList.contains(update)) {
							List<String> updList = addCounterAndRatio(
									update_split[5], update_split[6]);
							StringBuilder stringBuilder = new StringBuilder();
							stringBuilder.append(update_split[0])
									.append(",")
									.append(update_split[1])
									.append(",")
									.append(update_split[2])
									.append(",")
									.append(update_split[3])
									.append(",")
									.append(update_split[4])
									.append(",")
									.append(updList.get(0))
									.append(",")
									.append(updList.get(1))
									.append(",")
									.append(updList.get(2));
							newRecordList.add(stringBuilder.toString());
							tmpList.add(update);
						}
					}
				} else if (MakeTextConst.WITHIN_TIME_EACH.equals(file_checker)) {
					if (record_split[0].equals(update_split[0]) &&
							record_split[1].equals(update_split[1]) &&
							record_split[2].equals(update_split[2]) &&
							record_split[3].equals(update_split[3]) &&
							record_split[4].equals(update_split[4]) &&
							!tmpList.contains(record)) {
//						System.out.println("equals: ");
//						System.out.println("update_split[5]: " + update_split[5]);
//						System.out.println("update_split[6]: " + update_split[6]);
						List<String> updList = addTargetAndCounterAndRatio(
								record_split[5], record_split[6]);
						StringBuilder stringBuilder = new StringBuilder();
						stringBuilder.append(record_split[0])
								.append(",")
								.append(record_split[1])
								.append(",")
								.append(record_split[2])
								.append(",")
								.append(record_split[3])
								.append(",")
								.append(record_split[4])
								.append(",")
								.append(updList.get(0))
								.append(",")
								.append(updList.get(1))
								.append(",")
								.append(updList.get(2));
						newRecordList.add(stringBuilder.toString());
					} else {
						if (!tmpList.contains(record)) {
//							System.out.println("not equals record: ");
//							System.out.println("record_split[5]: " + record_split[5]);
//							System.out.println("record_split[6]: " + record_split[6]);
							List<String> updList = addCounterAndRatio(
									record_split[5], record_split[6]);
							StringBuilder stringBuilder = new StringBuilder();
							stringBuilder.append(record_split[0])
									.append(",")
									.append(record_split[1])
									.append(",")
									.append(record_split[2])
									.append(",")
									.append(record_split[3])
									.append(",")
									.append(record_split[4])
									.append(",")
									.append(updList.get(0))
									.append(",")
									.append(updList.get(1))
									.append(",")
									.append(updList.get(2));
							newRecordList.add(stringBuilder.toString());
							tmpList.add(record);
						}
						if (!tmpList.contains(update)) {
//							System.out.println("not equals update: ");
//							System.out.println("update_split[5]: " + update_split[5]);
//							System.out.println("update_split[6]: " + update_split[6]);
							List<String> updList = addCounterAndRatio(
									update_split[5], update_split[6]);
							StringBuilder stringBuilder = new StringBuilder();
							stringBuilder.append(update_split[0])
									.append(",")
									.append(update_split[1])
									.append(",")
									.append(update_split[2])
									.append(",")
									.append(update_split[3])
									.append(",")
									.append(update_split[4])
									.append(",")
									.append(updList.get(0))
									.append(",")
									.append(updList.get(1))
									.append(",")
									.append(updList.get(2));
							newRecordList.add(stringBuilder.toString());
							tmpList.add(update);
						}
					}
				} else if (MakeTextConst.WITHIN_TIME_ALL.equals(file_checker)) {
					if (record_split[0].equals(update_split[0]) &&
							record_split[1].equals(update_split[1]) &&
							record_split[2].equals(update_split[2])) {
						List<String> updList = addTargetAndCounterAndRatio(
								record_split[3], record_split[4]);
						StringBuilder stringBuilder = new StringBuilder();
						stringBuilder.append(record_split[0])
								.append(",")
								.append(record_split[1])
								.append(",")
								.append(record_split[2])
								.append(",")
								.append(updList.get(0))
								.append(",")
								.append(updList.get(1))
								.append(",")
								.append(updList.get(2));
						newRecordList.add(stringBuilder.toString());
					} else {
						if (!tmpList.contains(record)) {
							List<String> updList = addCounterAndRatio(
									record_split[3], record_split[4]);
							StringBuilder stringBuilder = new StringBuilder();
							stringBuilder.append(record_split[0])
									.append(",")
									.append(record_split[1])
									.append(",")
									.append(record_split[2])
									.append(",")
									.append(updList.get(0))
									.append(",")
									.append(updList.get(1))
									.append(",")
									.append(updList.get(2));
							newRecordList.add(stringBuilder.toString());
							tmpList.add(record);
						}
						if (!tmpList.contains(update)) {
							List<String> updList = addCounterAndRatio(
									update_split[3], update_split[4]);
							StringBuilder stringBuilder = new StringBuilder();
							stringBuilder.append(update_split[0])
									.append(",")
									.append(update_split[1])
									.append(",")
									.append(update_split[2])
									.append(",")
									.append(updList.get(0))
									.append(",")
									.append(updList.get(1))
									.append(",")
									.append(updList.get(2));
							newRecordList.add(stringBuilder.toString());
							tmpList.add(update);
						}
					}
				}
			}
		}

//		Path paths = Paths.get(filePath);
//		long previousSize = Files.size(paths);
//		System.out.println("bef size: " + previousSize);

		// 別の作成先パスがあればそちらに作成するよう変更する
//		if (new_filePath != null) {
//			filePath = new_filePath;
//		}
		addRecord(filePath, newRecordList);

		//Thread.sleep(500);

	}

	/**
	 * 該当数と探索数を加算し,割合を求めるメソッド
	 * @param target 該当数
	 * @param count 探索数
	 */
	private static List<String> addTargetAndCounterAndRatio(String target, String count) {
		List<String> updList = new ArrayList<String>();
		Integer newTarget = Integer.parseInt(target) + 1;
		Integer newCount = Integer.parseInt(count) + 1;
		//System.out.println("newTarget, newCount: " + newTarget + ", " + newCount);
		float newRatio = ((float) newTarget / newCount) * 100;
		updList.add(String.valueOf(newTarget));
		updList.add(String.valueOf(newCount));
		updList.add(String.format("%.1f", newRatio) + "%");
		return updList;
	}

	/**
	 * 探索数を加算し,割合を求めるメソッド
	 * @param target 該当数
	 * @param count 探索数
	 */
	private static List<String> addCounterAndRatio(String target, String count) {
		List<String> updList = new ArrayList<String>();
		Integer newTarget = Integer.parseInt(target);
		Integer newCount = Integer.parseInt(count) + 1;
		//System.out.println("newTarget, newCount: " + newTarget + ", " + newCount);
		float newRatio = ((float) newTarget / newCount) * 100;
		updList.add(String.valueOf(newTarget));
		updList.add(String.valueOf(newCount));
		updList.add(String.format("%.1f", newRatio) + "%");
		return updList;
	}
}
