package dev.application.batchjob.interf;

// 2026-11-20 bookmakers-batch（dev.batch.interf.JobExecControlIF）から移動（B011 を統計用の bookmakers-app で動かすため）

/**
 * ジョブ実行管理IF
 * @author shiraishitoshio
 *
 */
public interface JobExecControlIF {

	/**
	 * ジョブ実行開始
	 * @return
	 */
	public boolean jobStart(String jobId, String batchCd);

	/**
	 * ジョブ実行中
	 * @return
	 */
	public boolean jobRunning(String jobId);

	/**
	 * ジョブ実行終了
	 * @return
	 */
	public boolean jobEnd(String jobId);

	/**
	 * ジョブ異常終了
	 * @return
	 */
	public boolean jobException(String jobId);

	/**
	 * ジョブ継続
	 * @return
	 */
	public boolean jobHeartbeat(String jobId);

}
