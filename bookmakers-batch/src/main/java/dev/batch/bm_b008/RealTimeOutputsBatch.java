package dev.batch.bm_b008;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.application.analyze.bm_m001.OriginService;
import dev.batch.constant.BatchConstant;
import dev.batch.interf.BatchIF;
import dev.batch.interf.jobExecControlIF;
import dev.batch.util.JobIdUtil;
import dev.common.logger.ManageLoggerComponent;

/**
 * リアルタイムデータバッチ実行クラス。
 * <p>
 * 3分間隔で取得したリアルタイムデータを取得し、登録ロジック（Transactional想定）を実行する。
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
@Service("B008")
public class RealTimeOutputsBatch implements BatchIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = RealTimeOutputsBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = RealTimeOutputsBatch.class.getSimpleName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B008_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B008";

	/** OriginService部品 */
	@Autowired
	private OriginService originService;

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

		// jobId採番（B009-xxxxx）
		String jobId = JobIdUtil.generate(BATCH_CODE);
		boolean jobInserted = false;
		try {
			// リアルタイムデータサービス登録(Transactional)
			this.originService.execute();

			return BatchConstant.BATCH_SUCCESS;

		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
			return BatchConstant.BATCH_ERROR;

		} finally {
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		}
	}
}
