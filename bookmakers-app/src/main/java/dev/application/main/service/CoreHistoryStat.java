package dev.application.main.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.bm_m023H_bm_m026H.BasedFeatureHistoryStat;
import dev.application.analyze.interf.HistoryStatIF;
import dev.common.logger.ManageLoggerComponent;


/**
 * 履歴統計分析用サービスクラス
 * @author shiraishitoshio
 *
 */
@Service
@Transactional
public class CoreHistoryStat implements HistoryStatIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CoreHistoryStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CoreHistoryStat.class.getSimpleName();

	/**
	 * BM_M023H_BM_M026H統計分析ロジッククラス
	 */
	@Autowired
	private BasedFeatureHistoryStat basedFeatureHistoryStat;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int execute() throws Exception {
		final String METHOD_NAME = "execute";

		// 時間計測開始
		long startTime = System.nanoTime();

		// ログ出力
		this.loggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 履歴ロジック
		//this.basedFeatureHistoryStat.calcStat(stat);

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
