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

class Data {
    String timeRange;
    String feature;
    String threshold;
    int count;
    int searchCount;
    String percentage;

    public Data(String timeRange, String feature, String threshold, int count, int searchCount, String percentage) {
        this.timeRange = timeRange;
        this.feature = feature;
        this.threshold = threshold;
        this.count = count;
        this.searchCount = searchCount;
        this.percentage = percentage;
    }

    @Override
    public String toString() {
        return timeRange + "," + feature + "," + threshold + "," + count + "," + searchCount + "," + percentage;
    }
}

/**
 * フィルタクラス
 * @author shiraishitoshio
 *
 */
public class MaxFilter {

	/**
	 * フィルター
	 * @param filePath
	 */
    public static void execute(String filePath) {

        List<Data> dataList = new ArrayList<>();
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

                try {
                    String timeRange = parts[0].trim();
                    String feature = parts[1].trim();
                    String threshold = parts[2].trim();
                    int count = Integer.parseInt(parts[3].trim());
                    int searchCount = Integer.parseInt(parts[4].trim());
                    String percentage = parts[5].trim();

                    dataList.add(new Data(timeRange, feature, threshold, count, searchCount, percentage));
                } catch (NumberFormatException e) {
                    System.err.println("数値のパースエラー: " + line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // **(2) グループごとに最大の該当数を持つデータを取得**
        Map<String, Data> maxMap = new HashMap<>();
        for (Data d : dataList) {
            String key = d.timeRange + "-" + d.feature + "-" + d.threshold;
            maxMap.putIfAbsent(key, d);
            if (d.count > maxMap.get(key).count) {
                maxMap.put(key, d);
            }
        }

        // **(3) 結果を元のファイルに上書き**
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(filePath))) {
            bw.write(header); // ヘッダーを書き込む
            bw.newLine();
            for (Data d : maxMap.values()) {
                bw.write(d.toString());
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("ファイルが更新されました: " + filePath);
    }
}
