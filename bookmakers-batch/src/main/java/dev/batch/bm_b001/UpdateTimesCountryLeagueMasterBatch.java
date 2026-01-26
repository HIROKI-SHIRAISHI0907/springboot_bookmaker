package dev.batch.bm_b001;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.constant.BatchConstant;
import dev.batch.interf.BatchIF;
import dev.batch.interf.jobExecControlIF;
import dev.batch.util.JobIdUtil;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;

@Service("B001")
public class UpdateTimesCountryLeagueMasterBatch implements BatchIF {

	/** 実行ログに出力するプロジェクト識別子（クラスの配置パス）。 */
	private static final String PROJECT_NAME = UpdateTimesCountryLeagueMasterBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** 実行ログに出力するクラス名。 */
	private static final String CLASS_NAME = UpdateTimesCountryLeagueMasterBatch.class.getSimpleName();

	/** 運用向けのエラーコード。 */
	private static final String ERROR_CODE = "BM_B001_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B001";

	/** ロガー */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/** ジョブ実行制御 */
	@Autowired
	private jobExecControlIF jobExecControl;

	/** 非同期ワーカー */
	@Autowired
	private B001AsyncMasterPythonWorker asyncWorker;

	/**
	 * バッチ処理を実行する。
	 *
	 * @return
	 * <ul>
	 *   <li>{@link BatchConstant#BATCH_SUCCESS}：正常終了</li>
	 *   <li>{@link BatchConstant#BATCH_ERROR}：異常終了</li>
	 * </ul>
	 */
	@Override
	public int execute() {
		final String METHOD_NAME = "execute";
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// jobId採番（B001-xxxxx）
		String jobId = JobIdUtil.generate(BATCH_CODE);
		boolean jobInserted = false;
		try {
			// 0: QUEUED（受付）
			boolean started = jobExecControl.jobStart(jobId, BATCH_CODE);
			if (!started) {
				this.manageLoggerComponent.debugWarnLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE,
						"jobStart failed (duplicate or insert error). jobId=" + jobId);
				return BatchConstant.BATCH_ERROR;
			}
			jobInserted = true;

			// 非同期起動（画面/呼び出し元は待たない）
			asyncWorker.run(jobId);

			String messageCd = MessageCdConst.MCD00015I_BATCH_ACCEPTED;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BATCH_CODE + " accepted. jobId=" + jobId);

			// 受付成功（処理完了はjobテーブルのstatusで判定）
			return BatchConstant.BATCH_SUCCESS;

		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
			// 受付時に落ちたらINSERT成功済みのみFAILEDへ
			if (jobInserted) {
				try {
					jobExecControl.jobException(jobId);
				} catch (Exception ignore) {
				}
			}
			return BatchConstant.BATCH_ERROR;

		} finally {
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		}
	}
}
