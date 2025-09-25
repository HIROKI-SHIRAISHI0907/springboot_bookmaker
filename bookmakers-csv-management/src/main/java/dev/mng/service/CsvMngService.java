package dev.mng.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.common.logger.ManageLoggerComponent;
import dev.mng.analyze.bm_c001.StatSizeFinalizeMasterCsv;
import dev.mng.analyze.interf.CsvMngIF;
import dev.mng.csvmng.ExportCsv;
import dev.mng.dto.CsvTargetCommonInputDTO;

/**
 * CSV管理用サービスクラス
 * @author shiraishitoshio
 *
 */
@Service
public class CsvMngService implements CsvMngIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CsvMngService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CsvMngService.class.getSimpleName();

	/**
	 * BM_C001統計分析ロジッククラス
	 */
	@Autowired
	private StatSizeFinalizeMasterCsv statSizeFinalizeMasterCsv;

	/**
	 * CSV作成クラス
	 */
	@Autowired
	private ExportCsv exportCsv;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void execute(CsvTargetCommonInputDTO input) throws Exception {
		final String METHOD_NAME = "execute";

		// ログ出力
		this.loggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// BM_C001
		this.statSizeFinalizeMasterCsv.calcCsv(input);
		// CSV作成
		this.exportCsv.execute(input);

		// endLog
		this.loggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

	}

}
