package dev.application.main.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.application.analyze.bm_m002.ConditionResultDataStat;
import dev.application.analyze.bm_m003.TeamMonthlyScoreSummaryStat;
import dev.common.entity.BookDataEntity;
import dev.common.getstatinfo.GetStatInfo;
import dev.common.logger.ManageLoggerComponent;

/**
 * 統計分析用サービスクラス
 * @author shiraishitoshio
 *
 */
@Service
public class StatService implements StatIF {

	/**
	 * 統計情報取得管理クラス
	 */
	@Autowired
	private GetStatInfo getStatInfo;

	/**
	 * BM_M002統計分析ロジッククラス
	 */
	@Autowired
	private ConditionResultDataStat conditionResultDataStat;

	/**
	 * BM_M003統計分析ロジッククラス
	 */
	@Autowired
	private TeamMonthlyScoreSummaryStat teamMonthlyScoreSummaryStat;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	@Override
	public int execute() throws Exception {
		final String METHOD = "execute";

		// シーケンスデータから取得(最大値情報取得)
		String csvNumber = "";

		// 時間計測開始
		long startTime = System.nanoTime();

		// 直近のCSVデータ情報を取得
		Map<String, Map<String, List<BookDataEntity>>> getStatMap
			= this.getStatInfo.getData(csvNumber);

		// 統計ロジック呼び出し(@Transactionl付き)(国,リーグ単位で並列)
		this.conditionResultDataStat.calcStat(getStatMap);
		this.teamMonthlyScoreSummaryStat.calcStat(getStatMap);

		// 時間計測終了
		long endTime = System.nanoTime();
		long durationMs = (endTime - startTime) / 1_000_000; // ミリ秒に変換

		System.out.println("時間: " + durationMs);

		return 0;
	}


}
