package dev.common.edit;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.common.maketext.MakeTextConst;


/**
 * インスタンス生成,文字列返却クラス
 * @author shiraishitoshio
 *
 */
class DataRow {
	String country, league, feature;
	String threshold, time;
    int matchCount, searchCount;
    String ratio;

	public DataRow(String country, String league, String time,
			String feature, String threshold, int matchCount, int searchCount,
			String ratio) {
		this.country = country;
		this.league = league;
		this.time = time;
		this.feature = feature;
		this.threshold = threshold;
		this.matchCount = matchCount;
		this.searchCount = searchCount;
		this.ratio = ratio;
	}

	@Override
	public String toString() {
		return String.join(",", country, league, time, feature, String.valueOf(threshold),
				String.valueOf(matchCount), String.valueOf(searchCount), ratio);
	}
}

/**
 * テキスト内のレコードをソート
 * @author shiraishitoshio
 *
 */
public class SortTextWithLeague {

	/**
	 * ファイル名
	 * @param inputFile
	 * @param file_checker 作成ファイルチェッカー
	 */
	public static void execute(String inputFile, String file_checker) {
		String outputFile = null;
		if (MakeTextConst.WITHIN_TIME_EACH.equals(file_checker)) {
			outputFile = inputFile.replace("WithIn20minute", "WithIn20minute2");
		} else {
			System.out.println("WITHIN_TIME_EACHではないためreturn。");
			return;
		}

		List<DataRow> data = new ArrayList<>();

		try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {
			br.readLine(); // ヘッダーをスキップ
			String line;
			while ((line = br.readLine()) != null) {
				String[] parts = line.split(",");
				try {
                    int matchCount = Integer.parseInt(parts[5]);
                    int searchCount = Integer.parseInt(parts[6]);
                    data.add(new DataRow(parts[0], parts[1], parts[2], parts[3], parts[4],
                    		matchCount, searchCount, parts[7]));
                } catch (NumberFormatException e) {
                    System.err.println("数値変換エラー: " + line);
                    System.err.println(e);
                }
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("読み取りに失敗し、並び替えに失敗しました。");
			return;
		}

		// キー（国, リーグ, 試合時間範囲, 特徴量）ごとに閾値を昇順でソート
		Map<String, List<DataRow>> groupedData = data.stream()
				.collect(Collectors.groupingBy(row -> row.country + "," + row.league + "," + row.time + "," + row.feature));

		List<DataRow> sortedData = groupedData.values().stream()
				.flatMap(list -> list.stream().sorted(Comparator.comparingDouble(row -> {
					String threshold = row.threshold.replace("%", ""); // "%" を削除
					try {
						return Double.parseDouble(threshold); // 数値に変換
					} catch (NumberFormatException e) {
						System.err.println("閾値変換エラー: " + row.threshold);
						return Double.MAX_VALUE; // 変換できない場合は最大値扱い（ソートの最後にくる）
					}
				})))
				.collect(Collectors.toList());

		try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile))) {
			bw.write("国,リーグ,試合時間範囲,特徴量,閾値,該当数,探索数,割合\n");
			for (DataRow row : sortedData) {
				bw.write(row.toString() + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		// 元ファイルを一時ファイルで置き換える
		try {
			Files.move(Paths.get(outputFile), Paths.get(inputFile), StandardCopyOption.REPLACE_EXISTING);
			Files.deleteIfExists(Paths.get(outputFile));
		} catch (IOException e) {
			System.err.println("ファイル置き換えエラー: " + e.getMessage());
		}
	}
}
