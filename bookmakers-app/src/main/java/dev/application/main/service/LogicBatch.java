package dev.application.main.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.application.analyze.common.util.BatchResultConst;
import dev.application.analyze.interf.BatchIF;
import dev.common.logger.ManageLoggerComponent;

/**
 * 論理削除バッチ実行クラス
 * @author shiraishitoshio
 *
 */
@Service
public class LogicBatch implements BatchIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = LogicBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = LogicBatch.class.getSimpleName();

	/** LogicFlgService */
	@Autowired
	private LogicFlgService logicFlgService;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int execute() throws Exception {
		final String METHOD_NAME = "execute";
		// ログ出力
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 論理フラグ
		try {
			this.logicFlgService.execute();
		} catch (Exception e) {
			// エラー
			return BatchResultConst.BATCH_ERR;
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		return BatchResultConst.BATCH_OK;
	}
}
