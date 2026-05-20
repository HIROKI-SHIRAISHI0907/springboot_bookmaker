package dev.batch.bm_b098;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.master.PgConnectionCleanupMasterRepository;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;

/**
 * Masterスキーマコネクションクリア
 * @author shiraishitoshio
 *
 */
@Component
public class PgConnectionCleanupMasterBatch {

    private static final String PROJECT_NAME = PgConnectionCleanupMasterBatch.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();

    private static final String CLASS_NAME = PgConnectionCleanupMasterBatch.class.getName();

    @Value("${cleanup.db-name:soccer_bm}")
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

        manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                MessageCdConst.MCD00099I_LOG,
                "cleanup start db=" + dbName + ", user=" + userName);

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
