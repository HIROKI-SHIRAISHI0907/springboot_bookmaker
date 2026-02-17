package dev.batch.builder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import dev.batch.interf.BatchIF;
import dev.common.constant.BatchConstant;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;

/**
 * バッチランナー
 * @author shiraishitoshio
 *
 */
@Component
@ConditionalOnProperty(name="batch.mode", havingValue="worker")
public class BatchJobRunner implements CommandLineRunner {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = BatchJobRunner.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = BatchJobRunner.class.getName();

	/** コンテキスト */
    private final ApplicationContext ctx;

    /** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

    // 例: --bm.job=B006
	@Value("${bm.job:${BM_JOB:}}")
    private String jobCode;

    // SpringBoot の通常起動（web/server用途）と区別したいなら profile でもOK
    public BatchJobRunner(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void run(String... args) throws Exception {
    	final String METHOD_NAME = "run";
        if (jobCode == null || jobCode.isBlank()) {
            // 何も指定されなければ何もしない（タスク起動だけして終了させたくないなら例外でもOK）
        	String messageCd = MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP;
        	this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					"[BATCH] bm.job is empty. exit.");
            return;
        }

        // @Service("B006") のように “Bean名 = jobCode” で取得
        BatchIF batch = (BatchIF) ctx.getBean(jobCode);

        int result = batch.execute();
        SpringApplication.exit(ctx, () -> result == BatchConstant.BATCH_SUCCESS ? 0 : 1);
        if (result != BatchConstant.BATCH_SUCCESS) {
        	String messageCd = MessageCdConst.MCD00002E_BATCH_EXECUTION_SKIP;
        	this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null,
					"[BATCH FAILED] code=" + jobCode + " result=" + result);
        	this.manageLoggerComponent.createSystemException(
        			PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
        			null, null);
        }

        String messageCd = MessageCdConst.MCD00001I_BATCH_EXECUTION_GREEN_FIN;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				"[BATCH SUCCESS] code=" + jobCode);
    }
}
