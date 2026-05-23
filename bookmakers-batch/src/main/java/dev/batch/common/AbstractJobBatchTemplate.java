package dev.batch.common;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import dev.batch.interf.BatchIF;
import dev.batch.interf.jobExecControlIF;
import dev.batch.util.JobIdUtil;
import dev.common.constant.BatchConstant;
import dev.common.logger.ManageLoggerComponent;

/**
 * ジョブ実行制御（jobStart/jobRunning/jobEnd/jobException/jobHeartbeat）と
 * 共通ログ・例外ハンドリングを提供するテンプレート。
 *
 * <p>想定ステータス遷移：</p>
 * <ol>
 *   <li>jobStart：QUEUED（受付）</li>
 *   <li>jobRunning：RUNNING（実処理開始）</li>
 *   <li>jobHeartbeat：生存通知（任意）</li>
 *   <li>jobEnd：SUCCESS（成功）</li>
 *   <li>jobException：FAILED（失敗）</li>
 * </ol>
 */
public abstract class AbstractJobBatchTemplate implements BatchIF {

	@Autowired
	@Qualifier("batchJobExecControl")
	protected jobExecControlIF jobExecControl;

	@Autowired
	protected BatchExecutionHistoryService executionHistoryService;

	@Autowired
	protected ManageLoggerComponent manageLoggerComponent;

	/** バッチコード（例: B002） */
	protected abstract String batchCode();

	/** エラーコード（例: BM_B002_ERROR） */
	protected abstract String errorCode();

	/**
	 * バッチ本体（差分）。
	 * <p>
	 * データ取得・登録処理など、実処理を実装する。
	 * 長時間処理の場合は {@link JobContext#heartbeat()} を適宜呼ぶこと。
	 * </p>
	 */
	protected abstract void doExecute(JobContext ctx) throws Exception;

	/** プロジェクト名（既存ログ仕様に合わせる） */
	protected String projectName() {
		return this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
	}

	/** クラス名（既存ログ仕様に合わせる） */
	protected String className() {
		return this.getClass().getName();
	}

	/**
	 * 履歴表示用の実行名。
	 *
	 * @return 実行名
	 */
	protected String executionName() {
		return this.getClass().getSimpleName();
	}

	/**
	 * バッチ名。
	 *
	 * <p>
	 * 履歴テーブル上での表示名として使用する。
	 * 必要に応じてサブクラスで override してよい。
	 * </p>
	 *
	 * @return バッチ名
	 */
	protected String batchName() {
		return this.getClass().getSimpleName();
	}

	/**
	 * 実行前にスキップするかどうかを判定する。
	 *
	 * <p>
	 * デフォルトではスキップしない。
	 * 必要なバッチのみ override する。
	 * </p>
	 *
	 * @return true: スキップする / false: 実行する
	 */
	protected boolean shouldSkipExecution() {
		return false;
	}

	/**
	 * スキップ時のログメッセージを返す。
	 *
	 * @return スキップ理由
	 */
	protected String skipReason() {
		return "実行条件によりスキップしました。";
	}

	@Override
	public final int execute() {
		final String METHOD_NAME = "execute";

		if (manageLoggerComponent == null) {
			throw new IllegalStateException("manageLoggerComponent is null. "
					+ "This batch instance is not autowired by Spring. class=" + this.getClass());
		}

		manageLoggerComponent.debugStartInfoLog(projectName(), className(), METHOD_NAME);

		// 実行前チェック
		if (shouldSkipExecution()) {
			manageLoggerComponent.debugInfoLog(
					projectName(),
					className(),
					METHOD_NAME,
					null,
					skipReason());
			manageLoggerComponent.debugEndInfoLog(projectName(), className(), METHOD_NAME);
			return BatchConstant.BATCH_SUCCESS;
		}

		final String code = batchCode();
		final String jobId = JobIdUtil.generate(code);
		final LocalDateTime startTime = LocalDateTime.now();

		boolean jobInserted = false;

		try {
			// 0: QUEUED（受付）
			boolean started = jobExecControl.jobStart(jobId, code);
			if (!started) {
				manageLoggerComponent.debugWarnLog(
						projectName(), className(), METHOD_NAME, errorCode(),
						"jobStart failed (duplicate or insert error). jobId=" + jobId);
				return BatchConstant.BATCH_ERROR;
			}
			jobInserted = true;

			// 1: RUNNING（実処理開始）
			jobExecControl.jobRunning(jobId);

			JobContext ctx = new JobContext(jobId, code);

			// 実行履歴テーブルにも QUEUED を同期登録
			executionHistoryService.registerBatchStart(
					jobId,
					executionName(),
					code,
					className(),
					METHOD_NAME,
					startTime);

			// 必要なら受付直後に1回生存通知（任意）
			// ctx.heartbeat();

			doExecute(ctx);

			// 2: SUCCESS（成功）
			jobExecControl.jobEnd(jobId);

			// 成功結果
			executionHistoryService.markBatchSuccess(
					jobId,
					startTime,
					LocalDateTime.now());

			manageLoggerComponent.debugInfoLog(
					projectName(), className(), METHOD_NAME, null,
					code + " finished. jobId=" + jobId);

			return BatchConstant.BATCH_SUCCESS;

		} catch (Exception e) {
			manageLoggerComponent.debugErrorLog(
					projectName(), className(), METHOD_NAME, errorCode(), e,
					"jobId=" + jobId);

			// 3: FAILED（失敗）
			if (jobInserted) {
				try {
					jobExecControl.jobException(jobId);
				} catch (Exception ignore) {
				}

				// 失敗結果
				executionHistoryService.markBatchFailure(
						jobId,
						startTime,
						LocalDateTime.now(),
						e);
			}
			return BatchConstant.BATCH_ERROR;

		} finally {
			manageLoggerComponent.debugEndInfoLog(projectName(), className(), METHOD_NAME);
		}
	}

	/**
	 * サブクラスから jobId / batchCode / heartbeat を扱うためのコンテキスト。
	 */
	public final class JobContext {
		private final String jobId;
		private final String batchCode;

		private JobContext(String jobId, String batchCode) {
			this.jobId = jobId;
			this.batchCode = batchCode;
		}

		public String jobId() {
			return jobId;
		}

		public String batchCode() {
			return batchCode;
		}

		/** 生存通知（長時間処理の途中で適宜呼ぶ） */
		public void heartbeat() {
			jobExecControl.jobHeartbeat(jobId);
		}
	}
}
