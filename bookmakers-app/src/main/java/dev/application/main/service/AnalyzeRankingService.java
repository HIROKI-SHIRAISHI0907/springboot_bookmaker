package dev.application.main.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.application.analyze.bm_m027.AnalyzeRankingStat;
import dev.common.entity.BookDataEntity;
import dev.common.getstatinfo.GetStatInfo;
import dev.common.logger.ManageLoggerComponent;

/**
 * 統計分析ランキング用サービスクラス
 * @author shiraishitoshio
 *
 */
@Service
public class AnalyzeRankingService implements StatIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = AnalyzeRankingService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = AnalyzeRankingService.class.getSimpleName();

	/**
	 * 統計情報取得管理クラス
	 */
	@Autowired
	private GetStatInfo getStatInfo;

	/**
	 * BM_M027統計分析ロジッククラス
	 */
	@Autowired
	private AnalyzeRankingStat analyzeRankingStat;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	@Override
	public int execute() throws Exception {
		final String METHOD_NAME = "execute";

		// 時間計測開始
		long startTime = System.nanoTime();

		// ログ出力
		this.loggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// シーケンスデータから取得(最大値情報取得)
		String csvNumber = "";
		String csvBackNumber = "";

		// 直近のCSVデータ情報を取得
		Map<String, Map<String, List<BookDataEntity>>> getStatMap = this.getStatInfo.getData(csvNumber, csvBackNumber);

		// 統計ロジック呼び出し(@Transactionl付き)(国,リーグ単位で並列)
		this.analyzeRankingStat.calcStat(getStatMap);

		// endLog
		this.loggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 時間計測終了
		long endTime = System.nanoTime();
		long durationMs = (endTime - startTime) / 1_000_000; // ミリ秒に変換

		System.out.println("時間: " + durationMs);

		return 0;
	}

}
