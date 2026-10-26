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

	/** 終了処理がこの時間で終わらなければ強制終了する（ミリ秒） */
	private static final long EXIT_WATCHDOG_MILLIS = 60_000L;

	/** コンテキスト */
    private final ApplicationContext ctx;

    /** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

    // 例: --bm.job=B006
	@Value("${bm.job:${BM_JOB:}}")
    private String jobCode;

	/**
	 * 事前準備フラグ
	 * 例:
	 * --bm.readyFlg=true
	 * or BM_READY_FLG=true
	 */
	@Value("${bm.readyFlg:${BM_READY_FLG:false}}")
	private boolean readyFlg;

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
            // worker なのに BM_JOB が無い: 何もせずに終了する（return だけだとタスクが残り続ける）
            exitJvm(0);
            return;
        }

        // @Service("B006") のように “Bean名 = jobCode” で取得
        BatchIF batch = (BatchIF) ctx.getBean(jobCode);

        System.out.println("jobCode=" + jobCode);
        System.out.println("readyFlg=" + readyFlg);
        System.out.println("batch bean class=" + batch.getClass().getName());
        System.out.println("batch bean toString=" + batch);

        int result = batch.execute(readyFlg);

        int exit = (result == BatchConstant.BATCH_SUCCESS) ? 0 : 1;
        exitJvm(exit);

    }

    /**
     * JVM を確実に終了させる（2026-10 追加）。
     *
     * <p>SpringApplication.exit はコンテキストを閉じるだけで JVM は止めない。終わらないスレッドが残ると
     * ECS のタスクが RUNNING のまま残り、DB の接続（プール）も持ち続ける
     * （B016 のタスクが 18 時間残り、2 つで 40 本の接続を持ったまま DB の上限に達した）。</p>
     * <ul>
     *   <li>コンテキストを閉じてから System.exit で終了する。</li>
     *   <li>閉じる処理や終了時の処理が止まったままでも、{@link #EXIT_WATCHDOG_MILLIS} 後に Runtime.halt で強制終了する。</li>
     * </ul>
     *
     * @param code 終了コード（0: 正常 / 1: 異常）
     */
    private void exitJvm(int code) {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(EXIT_WATCHDOG_MILLIS);
            } catch (InterruptedException ignored) {
                // 終了処理が先に終われば何もしない
            }
            System.err.println("[BATCH] exit timed out. halt(" + code + ")");
            Runtime.getRuntime().halt(code);
        }, "batch-exit-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        System.exit(SpringApplication.exit(ctx, () -> code));
    }
}
