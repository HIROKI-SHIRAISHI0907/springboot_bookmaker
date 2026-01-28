package dev.batch.bm_b007;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.application.main.service.CoreHistoryStat;
import dev.batch.interf.BatchIF;
import dev.batch.interf.jobExecControlIF;
import dev.batch.util.JobIdUtil;
import dev.common.constant.BatchConstant;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;

/**
 * 履歴登録バッチ実行クラス。
 * <p>
 * シーズンが終了したデータを取得し、登録ロジック（Transactional想定）を実行する。
 * </p>
 *
 * <p><b>実行方式</b></p>
 * <ul>
 *   <li>開始/終了ログを必ず出力する</li>
 *   <li>例外は内部で捕捉し、debugErrorLog に例外を付与して出力する</li>
 *   <li>戻り値で成功/失敗を返却する</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Service("B007")
public class StatHistoryBatch implements BatchIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = StatHistoryBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = StatHistoryBatch.class.getSimpleName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B007_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B007";

	/** CoreHistoryStat部品 */
	@Autowired
	private CoreHistoryStat coreHistoryStat;

	/** ジョブ実行制御 */
	@Autowired
	private jobExecControlIF jobExecControl;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

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

		// jobId採番（B008-xxxxx）
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

			// 履歴登録(Transactional)
			this.coreHistoryStat.execute();

			String messageCd = MessageCdConst.MCD00015I_BATCH_ACCEPTED;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BATCH_CODE + " accepted. jobId=" + jobId);

			return BatchConstant.BATCH_SUCCESS;

		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
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
