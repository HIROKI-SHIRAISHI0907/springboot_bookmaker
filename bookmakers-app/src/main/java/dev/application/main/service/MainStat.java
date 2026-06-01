package dev.application.main.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;

import dev.application.analyze.bm_m027.RankingService;
import dev.application.analyze.bm_m098.CsvSeqManageService;
import dev.application.analyze.interf.ServiceIF;
import dev.common.constant.BatchResultConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.getinfo.GetStatInfo;
import dev.common.logger.ManageLoggerComponent;
import lombok.extern.slf4j.Slf4j;

/**
 * 統計バッチ実行クラス
 */
@Service
@Slf4j
public class MainStat implements ServiceIF {

    private static final String PROJECT_NAME = MainStat.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();

    private static final String CLASS_NAME = MainStat.class.getName();

    private static final int DB_RETRY_MAX = 3;

    private static final long DB_RETRY_WAIT_MILLIS = 3000L;

    private static final Pattern SEQ_FROM_KEY =
            Pattern.compile("(?:^|.*/)([0-9]+)\\.csv$");

    @Autowired
    private GetStatInfo getStatInfo;

    @Autowired
    private CsvSeqManageService csvSeqManageService;

    @Autowired
    private CoreStat statService;

    @Autowired
    private RankingService rankingService;

    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

    @Override
    public int execute() throws Exception {
        final String METHOD_NAME = "execute";
        manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

        try {
            String job = safe(System.getenv("BM_JOB")).trim();
            String country = safe(System.getenv("BM_COUNTRY")).trim();
            String league = safe(System.getenv("BM_LEAGUE")).trim();

            if (country.isEmpty() && !league.isEmpty()) {
                throw new IllegalArgumentException(
                        "BM_LEAGUE を指定する場合は BM_COUNTRY も指定してください。");
            }

            boolean countryLeagueMode = !country.isEmpty() && !league.isEmpty();
            boolean ignoreCsvSeqManage = "B014".equals(job) || countryLeagueMode;

            String tmpFrom = null;
            String tmpTo = null;
            CsvSeqManageService.CsvSeqRange tmpRange = null;

            if (!ignoreCsvSeqManage) {
                tmpRange = runWithRetry(
                        "csvSeqManageService.decideRangeOrNull",
                        () -> csvSeqManageService.decideRangeOrNull());

                if (tmpRange == null) {
                    log.info("[CsvSeqManageService END] range == null");
                    return BatchResultConst.BATCH_OK;
                }

                log.info("[CsvSeqManageService INFO] range = {}:{}",
                        tmpRange.getFrom(), tmpRange.getTo());

                tmpFrom = String.valueOf(tmpRange.getFrom());
                tmpTo = String.valueOf(tmpRange.getTo());

            } else {
                if ("B014".equals(job)) {
                    log.info("[MainStat] BM_JOB=B014 のため CsvSeqManage はスキップします");
                }
                if (countryLeagueMode) {
                    log.info("[MainStat] BM_COUNTRY/BM_LEAGUE 指定モードのため CsvSeqManage はスキップします. BM_COUNTRY={}, BM_LEAGUE={}",
                            country, league);
                }
            }

            final String from = tmpFrom;
            final String to = tmpTo;
            final CsvSeqManageService.CsvSeqRange range = tmpRange;

            List<String> keys;

            if (countryLeagueMode) {
                keys = runWithRetry(
                        "getStatInfo.listCsvKeysByCountryLeague:" + country + ":" + league,
                        () -> getStatInfo.listCsvKeysByCountryLeague(country, league));

                log.info("[MainStat filter info] countryLeagueMode=true, BM_COUNTRY={}, BM_LEAGUE={}, keys.size={}",
                        country, league, keys == null ? 0 : keys.size());

            } else {
                keys = runWithRetry(
                        "getStatInfo.listCsvKeysInRange",
                        () -> getStatInfo.listCsvKeysInRange(from, to));

                log.info("[MainStat filter info] countryLeagueMode=false, from={}, to={}, keys.size={}",
                        from, to, keys == null ? 0 : keys.size());
            }

            if (keys == null || keys.isEmpty()) {
                log.info("[MainStat] target keys is empty");
                return BatchResultConst.BATCH_OK;
            }

            log.info("[MainStat] target keys.size = {}", keys.size());

            int lastProcessed = (!ignoreCsvSeqManage && range != null)
                    ? range.getFrom() - 1
                    : -1;

            int process = 1;
            int total = keys.size();

            for (String key : keys) {
                Map<String, Map<String, List<BookDataEntity>>> loadedMap = null;

                try {
                    loadedMap = runWithRetry(
                            "getStatInfo.getStatMapForSingleKey:" + key,
                            () -> getStatInfo.getStatMapForSingleKey(key));

                    if (loadedMap == null || loadedMap.isEmpty()) {
                        if (!ignoreCsvSeqManage) {
                            lastProcessed = Math.max(lastProcessed, extractSeq(key));
                        }

                        log.info("[MainStat] oneMap empty. skip key={}", key);
                        process++;
                        continue;
                    }

                    final Map<String, Map<String, List<BookDataEntity>>> oneMap = loadedMap;

                    log.info("[MainStat] process start key={}", key);
                    log.info("[MainStat] process category={}", oneMap.keySet());

                    Integer statResult = runWithRetry("statService.execute:" + key, () -> {
                        log.info("[MainStat] statService start key={}", key);
                        int result = statService.execute(oneMap, false);
                        log.info("[MainStat] statService end key={}, result={}", key, result);
                        return result;
                    });

                    runWithRetry("rankingService.execute:" + key, () -> {
                        log.info("[MainStat] rankingService start key={}", key);
                        rankingService.execute(oneMap, false);
                        log.info("[MainStat] rankingService end key={}", key);
                        return null;
                    });

                    if (!ignoreCsvSeqManage && range != null) {
                        int seq = extractSeq(key);
                        lastProcessed = Math.max(lastProcessed, seq);

                        final int markSeq = lastProcessed;
                        runWithRetry("csvSeqManageService.markSuccess:" + markSeq, () -> {
                            csvSeqManageService.markSuccess(markSeq);
                            return null;
                        });

                        log.info("[MainStat] progress {}/{}, key={}, statResult={}, markSeq={}",
                                process, range.getLastOnDb(), key, statResult, markSeq);
                    } else {
                        log.info("[MainStat] progress {}/{}, key={}, statResult={}",
                                process, total, key, statResult);
                    }

                } finally {
                    if (loadedMap != null) {
                        loadedMap.clear();
                    }
                }

                process++;
            }

            if (!ignoreCsvSeqManage && range != null) {
                final int finalMarkSeq = range.getTo();
                runWithRetry("csvSeqManageService.markSuccess:" + finalMarkSeq, () -> {
                    csvSeqManageService.markSuccess(finalMarkSeq);
                    return null;
                });

                log.info("[MainStat] final mark success. seq={}", finalMarkSeq);
            }

            return BatchResultConst.BATCH_OK;

        } catch (Exception e) {
            log.error("[MainStat] failed", e);
            manageLoggerComponent.debugErrorLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00099I_LOG, null,
                    "MainStat execute failed. message=" + safe(e.getMessage()));
            return BatchResultConst.BATCH_ERR;

        } finally {
            manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
        }
    }

    private static int extractSeq(String key) {
        if (key == null) {
            return -1;
        }
        Matcher m = SEQ_FROM_KEY.matcher(key);
        if (!m.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private <T> T runWithRetry(String processName, CheckedSupplier<T> supplier)
            throws Exception {
        final String METHOD_NAME = "runWithRetry";

        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return supplier.get();

            } catch (Exception e) {
                boolean retryable = isRetryableDbException(e);

                if (!retryable || attempt >= DB_RETRY_MAX) {
                    manageLoggerComponent.debugErrorLog(
                            PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                            MessageCdConst.MCD00099I_LOG, null,
                            "retry give up. process=" + processName
                                    + ", attempt=" + attempt
                                    + ", retryable=" + retryable
                                    + ", message=" + safe(e.getMessage()));
                    throw e;
                }

                manageLoggerComponent.debugWarnLog(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                        MessageCdConst.MCD00099I_LOG,
                        "retry execute. process=" + processName
                                + ", attempt=" + attempt
                                + "/" + DB_RETRY_MAX
                                + ", waitMillis=" + DB_RETRY_WAIT_MILLIS
                                + ", message=" + safe(e.getMessage()));

                sleepQuietly(DB_RETRY_WAIT_MILLIS);
            }
        }
    }

    private boolean isRetryableDbException(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof CannotGetJdbcConnectionException) return true;
            if (current instanceof CannotCreateTransactionException) return true;
            if (current instanceof TransientDataAccessException) return true;
            if (current instanceof RecoverableDataAccessException) return true;

            if (current instanceof SQLException) {
                String state = ((SQLException) current).getSQLState();
                if (state != null && state.startsWith("08")) return true;
            }

            String cn = safe(current.getClass().getName());
            String msg = safe(current.getMessage()).toLowerCase();

            if (cn.contains("SQLTransientConnectionException")
                    || cn.contains("SQLRecoverableException")) {
                return true;
            }

            if (msg.contains("connection is closed")
                    || msg.contains("connection has been closed")
                    || msg.contains("broken pipe")
                    || msg.contains("connection reset")
                    || msg.contains("communications link failure")
                    || msg.contains("could not open jdbc connection")
                    || msg.contains("failed to obtain jdbc connection")
                    || msg.contains("the connection attempt failed")
                    || msg.contains("socket closed")
                    || msg.contains("connection refused")
                    || msg.contains("i/o error occurred while sending to the backend")) {
                return true;
            }

            current = current.getCause();
        }
        return false;
    }

    private void sleepQuietly(long millis) throws InterruptedException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
