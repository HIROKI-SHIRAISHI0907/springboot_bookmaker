package dev.application.main.service;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.application.main.service.sub.RDataOutputMainLogic;

/**
 * BMデータ分析結果ロジック
 * @author shiraishitoshio
 *
 */
@Transactional
@Service
public class BookMakerDataAnalyzeResultLogic {

	/** Logger */
	private static final Logger logger = LoggerFactory.getLogger(BookMakerDataRegisterBusinessLogic.class);

	/** プロジェクト名 */
	private static final String PROJECT_NAME = BookMakerDataAnalyzeResultLogic.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = BookMakerDataAnalyzeResultLogic.class.getSimpleName();

	/**
	 * 処理実行
	 * <p>
	 * 1. ファイル内のデータ取得</br>
	 * 2. DB登録処理</br>
	 * 3. ファイル削除処理</br>
	 * @return 0:正常終了, 4:警告終了, 9:異常終了
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public int execute() throws IOException, InterruptedException {
		final String METHOD = "execute";

		logger.info(" analyze businessLogic start : {} ", CLASS_NAME);

		// チーム統計マスタ生成
//		TeamStatisticsDataMainLogic teamStatisticsDataMainLogic = new TeamStatisticsDataMainLogic();
//        teamStatisticsDataMainLogic.execute();

        // R言語CSV作成
//		RDataOutputSimpleLogic rDataOutputSimpleLogic = new RDataOutputSimpleLogic();
//		rDataOutputSimpleLogic.execute();

//        RDataOutputAllLogic rDataOutputAllLogic = new RDataOutputAllLogic();
//        rDataOutputAllLogic.execute();

        RDataOutputMainLogic rDataOutputMainLogic = new RDataOutputMainLogic();
        try {
			rDataOutputMainLogic.execute();
		} catch (Exception e) {
			e.printStackTrace();
		}

		logger.info(" analyze businessLogic end : {} ", CLASS_NAME);

		return 0;
	}

}
