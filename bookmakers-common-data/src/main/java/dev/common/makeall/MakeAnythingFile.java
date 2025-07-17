package dev.common.makeall;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * ファイル作成処理
 * @author shiraishitoshio
 *
 */
public class MakeAnythingFile {

	private static final String PATH = "/Users/shiraishitoshio/bookmaker/python_analytics/";

	/**
	 * 作成処理
	 * @param tableId テーブルID
	 * @param extension 拡張子
	 * @param fileName 作成ファイル名
	 * @param makeList 作成リスト
	 */
	public void execute(String tableId, String extension, String fileName, List<List<String>> makeList) {
		// 出力ファイル
		final String NAME = PATH + fileName + extension;

		// CSV作成確認
		Path path = Paths.get(NAME);
		try {
			if (!Files.exists(path)) {
				Files.createFile(path);
			}
		} catch (IOException e) {
			System.out.println("CSVが作成できない");
		}

		//String headList = UniairColumnMapUtil.mkCsvChkHeader(tableId);
		String headList = null;
		headList = headList.replace("id,", "");
		headList = headList.replace("ID,", "");
		headList = headList.replace(",備考", "");

		// オープン
		try (FileOutputStream fileOutputStream = new FileOutputStream(NAME);
			 BufferedWriter bufferedWriter = new BufferedWriter(
						new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8))) {

			// ヘッダー
			bufferedWriter.write(headList);
			bufferedWriter.newLine();

			for (List<String> rollList : makeList) {
				StringBuilder sBuilder = new StringBuilder();
				for (String roll : rollList) {
					if (sBuilder.toString().length() > 0) {
						sBuilder.append(",");
					}
					String data = (roll == null) ? "" : roll;
					sBuilder.append(data);
				}
				bufferedWriter.write(sBuilder.toString());
				bufferedWriter.newLine();
			}
		} catch (IOException e) {
			System.out.println("書き込み失敗");
		}
	}
}
