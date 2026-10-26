package dev.batch.bm_b098;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.master.PgConnectionCleanupMasterRepository;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;

/**
 * Masterスキーマコネクションクリア
 *
 * <p>idle in transaction のまま放置された接続・長時間 active の接続を pg_terminate_backend で切る。
 * これを止めると、切れずに残った接続が DB の上限に達し、web で「Failed to obtain JDBC Connection」になる。</p>
 *
 * <p>2026-10 修正</p>
 * <ul>
 *   <li>常駐プロセス（batch.mode=control）でだけ動かす。Lambda・web から起動するバッチのタスク（BATCH_MODE=worker）では
 *       Bean を作らない。以前は動いているタスクの数だけ同時に実行され、ログが毎分のように出ていた。</li>
 *   <li>切る対象が無いときはログを出さない（開始・終了ログは対象があるときだけ）。</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@Component
@ConditionalOnProperty(name = "batch.mode", havingValue = "control", matchIfMissing = true)
public class PgConnectionCleanupMasterBatch {

    private static final String PROJECT_NAME = PgConnectionCleanupMasterBatch.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();

    private static final String CLASS_NAME = PgConnectionCleanupMasterBatch.class.getName();

    @Value("${cleanup.db-name-master:soccer_bm_master}")
    private String dbName;

    @Value("${cleanup.user-name:bmadmin}")
    private String userName;

    @Value("${cleanup.application-name:PostgreSQL JDBC Driver}")
    private String applicationName;

    @Value("${cleanup.idle-in-tx-minutes:10}")
    private int idleInTxMinutes;

    @Value("${cleanup.long-running-minutes:30}")
    private int longRunningMinutes;

    @Value("${cleanup.enabled:false}")
    private boolean enabled;

    @Autowired
    private PgConnectionCleanupMasterRepository cleanupRepository;

    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

    @Scheduled(cron = "${cleanup.cron:0 */5 * * * *}")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void execute() {
        final String METHOD_NAME = "execute";

        if (!enabled) {
            return;
        }

        Set<Integer> targets = new LinkedHashSet<>();

        List<Integer> idleInTx = cleanupRepository.findIdleInTransactionPids(
                userName, dbName, applicationName, idleInTxMinutes);
        if (idleInTx != null) {
            targets.addAll(idleInTx);
        }

        List<Integer> activeTooLong = cleanupRepository.findLongRunningActivePids(
                userName, dbName, applicationName, longRunningMinutes);
        if (activeTooLong != null) {
            targets.addAll(activeTooLong);
        }

        // 切る対象が無ければ何も出さずに終わる
        if (targets.isEmpty()) {
            return;
        }

        manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                MessageCdConst.MCD00099I_LOG,
                "cleanup start db=" + dbName + ", user=" + userName + ", targets=" + targets);

        int success = 0;
        int failed = 0;

        for (Integer pid : targets) {
            if (pid == null) {
                continue;
            }
            try {
                boolean result = cleanupRepository.terminate(pid);
                if (result) {
                    success++;
                    manageLoggerComponent.debugWarnLog(
                            PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                            MessageCdConst.MCD00099I_LOG,
                            "terminated pid=" + pid);
                } else {
                    failed++;
                }
            } catch (Exception e) {
                failed++;
                manageLoggerComponent.debugErrorLog(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                        MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
                        "terminate failed pid=" + pid);
            }
        }

        manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                MessageCdConst.MCD00099I_LOG,
                "cleanup end target=" + targets.size()
                        + ", success=" + success
                        + ", failed=" + failed);
    }
}
