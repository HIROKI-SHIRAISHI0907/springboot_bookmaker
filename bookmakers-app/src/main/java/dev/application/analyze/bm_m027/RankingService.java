package dev.application.analyze.bm_m027;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.interf.StatIF;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * 統計分析用サービスクラス
 * @author shiraishitoshio
 *
 */
@Service
@Transactional
public class RankingService implements StatIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = RankingService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = RankingService.class.getSimpleName();

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

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int execute(Map<String, Map<String, List<BookDataEntity>>> stat) throws Exception {
		final String METHOD_NAME = "execute";

		// 時間計測開始
		long startTime = System.nanoTime();

		// ログ出力
		this.loggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 統計ロジック呼び出し(@Transactionl付き)(国,リーグ単位で並列)
		this.analyzeRankingStat.calcStat(stat);

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
