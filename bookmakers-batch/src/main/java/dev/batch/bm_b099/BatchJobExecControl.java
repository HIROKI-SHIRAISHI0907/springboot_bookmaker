package dev.batch.bm_b099;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.constant.BatchStatusConstant;
import dev.batch.interf.jobExecControlIF;
import dev.batch.repository.master.BatchJobExecRepository;
import dev.common.logger.ManageLoggerComponent;

/**
 * ジョブ実行管理クラス
 * @author shiraishitoshio
 *
 */
@Service("batchJobExecControl")
public class BatchJobExecControl implements jobExecControlIF {

	 /** 実行ログに出力するプロジェクト識別子（クラスの配置パス）。 */
    private static final String PROJECT_NAME = BatchJobExecControl.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();

    /** 実行ログに出力するクラス名。 */
    private static final String CLASS_NAME = BatchJobExecControl.class.getSimpleName();

    /** 運用向けのエラーコード。 */
    private static final String ERROR_CODE = "JOB_CONTROL";

    /** ジョブ実行管理リポジトリ。 */
    @Autowired
    private BatchJobExecRepository batchJobExecRepository;

    /** バッチ共通ログ出力を行う。 */
    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

	/**
     * ジョブ開始
     * ・batch_job_exec に INSERT（status=0）
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean jobStart(String jobId, String batchCd) {
    	final String METHOD_NAME = "jobStart";
    	try {
    		// 2時間以上更新がないステータス:0/1をFAILEDへ落とす
            int cleaned = batchJobExecRepository.failStaleJobs(batchCd);
            if (cleaned > 0) {
                this.manageLoggerComponent.debugWarnLog(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE,
                        "stale jobs cleaned=" + cleaned + " batchCd=" + batchCd);
            }

            // バッチがすでに実行されているか
            int running = batchJobExecRepository.jobCountExec(batchCd);
            if (running > 0) {
            	this.manageLoggerComponent.debugWarnLog(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE,
                        "already running running=" + running + " batchCd=" + batchCd);
            	return false;
            }

            BatchJobExecEntity entity = new BatchJobExecEntity();
            entity.setJobId(jobId);
            entity.setBatchCd(batchCd);
            entity.setStatus(BatchStatusConstant.STATUS_QUEUED);
            int result = batchJobExecRepository.jobStartExec(entity);
            return result == 1;
        } catch (DuplicateKeyException e) {
        	this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, "DuplicateKeyException: "
							+ "( jobId: " + jobId + " )");
            return false;
        } catch (Exception e) {
        	this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
            return false;
        }
    }

    /**
     * ジョブ実行中
     * ・batch_job_exec に UPDATE（status=1）
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean jobRunning(String jobId) {
    	final String METHOD_NAME = "jobRunAsync";
    	try {
            int result = batchJobExecRepository.jobUpdateExc(
            		jobId, BatchStatusConstant.STATUS_RUNNING);
            return result == 1;
        } catch (Exception e) {
        	this.manageLoggerComponent.debugWarnLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE,
                    "failed to update RUNNING. jobId=" + jobId + " msg=" + e.getMessage());
            return false; // 失敗したが本処理は止めない
        }
	}

    /**
     * ジョブ終了
     * ・batch_job_exec に UPDATE（status=10or99）
     */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean jobEnd(String jobId) {
		final String METHOD_NAME = "jobEnd";
    	try {
            int result = batchJobExecRepository.jobUpdateExc(
            		jobId, BatchStatusConstant.STATUS_SUCCESS);
            return result == 1;
        } catch (Exception e) {
        	this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
            return false;
        }
	}

	/**
     * ジョブ異常終了
     * ・batch_job_exec に UPDATE（status=99）
     */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean jobException(String jobId) {
		final String METHOD_NAME = "jobException";
    	try {
            int result = batchJobExecRepository.jobUpdateExc(
            		jobId, BatchStatusConstant.STATUS_FAILED);
            return result == 1;
        } catch (Exception e) {
        	this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
            return false;
        }
	}

	/**
     * スクレイピング継続用（スクレイピング中に2時間を超えると強制終了するため）
     * ・batch_job_exec に 更新日時をUPDATE
     */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean jobHeartbeat(String jobId) {
		final String METHOD_NAME = "jobException";
	    try {
	        return batchJobExecRepository.heartbeat(jobId) == 1;
	    } catch (Exception e) {
	    	this.manageLoggerComponent.debugWarnLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE,
                    "scrape continue RUNNING. jobId=" + jobId + " msg=" + e.getMessage());
	        return false;
	    }
	}

}
