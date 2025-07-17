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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MakeAllCsv {

	/** Logger */
	private static final Logger logger = LoggerFactory.getLogger(MakeAllCsv.class);

	/**
	 * CSV作成処理
	 * @param makeList 作成リスト
	 * @param rDataOutputDTO rDataOutputDTO
	 */
	public RDataOutputDTO execute(List<List<String>> makeList, RDataOutputDTO rDataOutputDTO) {
		// 出力ファイル
		final String CSVNAME = "/Users/shiraishitoshio/bookmaker/csv/all.csv";

		// CSV作成確認
		Path path = Paths.get(CSVNAME);
		try {
			if (!Files.exists(path)) {
				Files.createFile(path);
			}
		} catch (IOException e) {
			System.out.println("CSVが作成できない");
		}

		logger.info(" MakeAllCsv dupList size : {} ", rDataOutputDTO.getDupList().size());

		// CSVオープン
		try (FileOutputStream fileOutputStream = new FileOutputStream(CSVNAME, true);
				BufferedWriter bufferedWriter = new BufferedWriter(
						new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8))) {

			// ヘッダー
			if (!rDataOutputDTO.isHeaderFlg()) {
				//String headList = UniairColumnMapUtil.mkCsvChkHeader(UniairConst.BM_M001);
				String headList = null;
				bufferedWriter.write(headList);
				bufferedWriter.newLine();
				rDataOutputDTO.setHeaderFlg(true);
			}

			List<String> dupList = rDataOutputDTO.getDupList();

			for (List<String> rollList : makeList) {
				StringBuilder sBuilder = new StringBuilder();
				for (String roll : rollList) {
					if (sBuilder.toString().length() > 0) {
						sBuilder.append(",");
					}
					sBuilder.append(roll);

				}

				if (!dupList.contains(rollList.get(0))) {
					bufferedWriter.write(sBuilder.toString());
					bufferedWriter.newLine();
				}

				if (!dupList.contains(rollList.get(0)))
					dupList.add(rollList.get(0));
			}
			bufferedWriter.close();
		} catch (IOException e) {
			System.out.println("書き込み失敗");
		}
		return rDataOutputDTO;
	}
}
