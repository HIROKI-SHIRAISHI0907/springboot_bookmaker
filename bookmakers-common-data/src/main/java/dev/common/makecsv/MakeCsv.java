package dev.common.makecsv;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import dev.common.exception.BusinessException;


public class MakeCsv {

	/**
	 * CSV作成処理
	 * @param fileName 作成ファイル名
	 * @param tableId テーブルID(ヘッダーがnullの場合)
	 * @param header ヘッダー(テーブルIDがnullの場合)
	 * @param makeList 作成リスト
	 */
	public void execute(String fileName, String tableId, List<String> header, List<List<String>> makeList) {
		// 出力ファイル
		final String CSVNAME = fileName;

		// エラー
		if (tableId == null && (header.isEmpty() || header == null)) {
			throw new BusinessException("", "", "", "tableId headerのどちらも空です。");
		}

		// CSV作成確認
		Path path = Paths.get(CSVNAME);
		try {
			if (!Files.exists(path)) {
				Files.createFile(path);
			}
		} catch (IOException e) {
			System.out.println("CSVが作成できない");
		}

		String headList = "";
		if (tableId != null) {
			headList = UniairColumnMapUtil.mkCsvChkHeader(tableId);
		} else if (header != null && !header.isEmpty()) {
			for (String head : header) {
				if (headList.length() > 0) {
					headList += ",";
				}
				headList += head;
			}
		}

		// CSVオープン
		try (FileOutputStream fileOutputStream = new FileOutputStream(CSVNAME);
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
					String data = (roll == null || roll.isEmpty()) ? "" : roll;
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
