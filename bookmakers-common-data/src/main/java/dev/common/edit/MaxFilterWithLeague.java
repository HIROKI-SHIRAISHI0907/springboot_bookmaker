package dev.common.edit;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Data2 {
    String country;
    String league;
    String matchTime;
    String feature;
    String threshold;
    int count;
    int targetCount;
    String percentage;

    public Data2(String country, String league, String matchTime, String feature, String threshold, int count, int targetCount, String percentage) {
        this.country = country;
        this.league = league;
        this.matchTime = matchTime;
        this.feature = feature;
        this.threshold = threshold;
        this.count = count;
        this.targetCount = targetCount;
        this.percentage = percentage;
    }

    @Override
    public String toString() {
        return country + "," + league + "," + matchTime + "," + feature + "," + threshold + "," + count + "," + targetCount + "," + percentage;
    }
}

public class MaxFilterWithLeague {

	/**
	 * フィルター
	 * @param filePath
	 */
    public static void execute(String filePath) {

        List<Data2> dataList = new ArrayList<>();
        String header = "";

        // **(1) CSVファイルを読み込む**
        try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath))) {
            String line;
            boolean isHeader = true;
            while ((line = br.readLine()) != null) {
                if (isHeader) {
                    header = line; // ヘッダーを保存
                    isHeader = false;
                    continue;
                }
                String[] parts = line.split(",", -1);
                if (parts.length != 8) continue; // 不正なデータはスキップ

                try {
                    String country = parts[0].trim();
                    String league = parts[1].trim();
                    String matchTime = parts[2].trim();
                    String feature = parts[3].trim();
                    String threshold = parts[4].trim();
                    int count = Integer.parseInt(parts[5].trim());
                    int targetCount = Integer.parseInt(parts[6].trim());
                    String percentage = parts[7].trim();

                    dataList.add(new Data2(country, league, matchTime, feature, threshold, count, targetCount, percentage));
                } catch (NumberFormatException e) {
                    System.err.println("数値のパースエラー: " + line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // **(2) グループごとに最大の該当数を持つデータを取得**
        Map<String, Data2> maxMap = new HashMap<>();
        for (Data2 d : dataList) {
            String key = d.country + "-" + d.league + "-" + d.matchTime + "-" + d.feature + "-" + d.threshold;
            maxMap.putIfAbsent(key, d);
            if (d.count > maxMap.get(key).count) {
                maxMap.put(key, d);
            }
        }

        // **(3) 結果を元のファイルに上書き**
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(filePath))) {
            bw.write(header); // ヘッダーを書き込む
            bw.newLine();
            for (Data2 d : maxMap.values()) {
                bw.write(d.toString());
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("ファイルが更新されました: " + filePath);
    }
}
