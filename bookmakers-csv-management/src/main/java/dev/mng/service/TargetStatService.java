package dev.mng.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.common.logger.ManageLoggerComponent;
import dev.mng.analyze.bm_c002.CsvMngInputDTO;
import dev.mng.analyze.bm_c002.TargetLeagueStatusCsv;
import dev.mng.analyze.interf.TargetMngIF;

/**
 * 対象スタッツサービスクラス
 * @author shiraishitoshio
 *
 */
@Service
public class TargetStatService implements TargetMngIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TargetStatService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TargetStatService.class.getSimpleName();

	/**
	 * BM_C002統計分析ロジッククラス
	 */
	@Autowired
	private TargetLeagueStatusCsv targetLeagueStatusCsv;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void execute(CsvMngInputDTO input) throws Exception {
		final String METHOD_NAME = "execute";

		// ログ出力
		this.loggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// BM_C002
		this.targetLeagueStatusCsv.updateStatus(input);

		// endLog
		this.loggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

	}

}
