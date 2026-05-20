package dev.application.main.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

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
 * @author shiraishitoshio
 *
 */
@Service
@Slf4j
public class MainStat implements ServiceIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MainStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = MainStat.class.getName();

	/** 接続断系の再試行回数 */
	private static final int DB_RETRY_MAX = 3;

	/** 再試行待機(ms) */
	private static final long DB_RETRY_WAIT_MILLIS = 3000L;

	/**
	 * 統計情報取得管理クラス
	 */
	@Autowired
	private GetStatInfo getStatInfo;

	/**
	 * CSV管理クラス
	 */
	@Autowired
	private CsvSeqManageService csvSeqManageService;

	/** StatService */
	@Autowired
	private CoreStat statService;

	/** RankingService */
	@Autowired
	private RankingService rankingService;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int execute() throws Exception {
		final String METHOD_NAME = "execute";
		manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		try {
			CsvSeqManageService.CsvSeqRange range = runWithRetry(
					"csvSeqManageService.decideRangeOrNull",
					() -> csvSeqManageService.decideRangeOrNull());

			if (range == null) {
				log.info("[CsvSeqManageService END] range == null");
				return BatchResultConst.BATCH_OK;
			}

			log.info("[CsvSeqManageService INFO] range = {}:{}", range.getFrom(), range.getTo());

			String from = String.valueOf(range.getFrom());
			String to = String.valueOf(range.getTo());

			List<String> keys = runWithRetry(
					"getStatInfo.listCsvKeysInRange:" + from + "-" + to,
					() -> getStatInfo.listCsvKeysInRange(from, to));

			if (keys == null || keys.isEmpty()) {
				log.info("[getStatInfo.listCsvKeysInRange END] keys is empty");
				return BatchResultConst.BATCH_OK;
			}

			log.info("[getStatInfo.listCsvKeysInRange size info] keys.size = {}", keys.size());

			int lastProcessed = range.getFrom() - 1;
			int process = 1;

			for (String key : keys) {
				Map<String, Map<String, List<BookDataEntity>>> loadedMap = null;

				try {
					loadedMap = runWithRetry(
							"getStatInfo.getStatMapForSingleKey:" + key,
							() -> getStatInfo.getStatMapForSingleKey(key));

					if (loadedMap == null || loadedMap.isEmpty()) {
						lastProcessed = Math.max(lastProcessed, extractSeq(key));
						log.info("[MainStat] oneMap empty. skip key={}", key);
						process++;
						continue;
					}

					final Map<String, Map<String, List<BookDataEntity>>> oneMap = loadedMap;

					log.info("[String key : keys info] key = {}", key);
					log.info("[String key : keys info] country-league = {}", oneMap.keySet());

					runWithRetry("statService.execute:" + key, () -> {
						log.info("[MainStat] statService start key={}", key);
						statService.execute(oneMap, false);
						log.info("[MainStat] statService end key={}", key);
						return null;
					});

					runWithRetry("rankingService.execute:" + key, () -> {
						log.info("[MainStat] rankingService start key={}", key);
						rankingService.execute(oneMap, false);
						log.info("[MainStat] rankingService end key={}", key);
						return null;
					});

					int seq = extractSeq(key);
					lastProcessed = Math.max(lastProcessed, seq);

					final int markSeq = lastProcessed;
					runWithRetry("csvSeqManageService.markSuccess:" + markSeq, () -> {
						csvSeqManageService.markSuccess(markSeq);
						return null;
					});

					log.info("[stat calc fin info] csv situation: {}/{}", process, range.getLastOnDb());

				} finally {
					if (loadedMap != null) {
						loadedMap.clear();
					}
				}

				process++;
			}

			final int finalMarkSeq = range.getTo();
			runWithRetry("csvSeqManageService.markSuccess:" + finalMarkSeq, () -> {
				csvSeqManageService.markSuccess(finalMarkSeq);
				return null;
			});

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
		// "6819.csv" -> 6819
		int dot = key.indexOf('.');
		if (dot <= 0) {
			return -1;
		}
		try {
			return Integer.parseInt(key.substring(0, dot));
		} catch (Exception e) {
			return -1;
		}
	}

	private <T> T runWithRetry(String processName, CheckedSupplier<T> supplier) throws Exception {
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
			if (current instanceof CannotGetJdbcConnectionException) {
				return true;
			}
			if (current instanceof CannotCreateTransactionException) {
				return true;
			}
			if (current instanceof TransientDataAccessException) {
				return true;
			}
			if (current instanceof RecoverableDataAccessException) {
				return true;
			}
			if (current instanceof SQLException) {
				String state = ((SQLException) current).getSQLState();
				if (state != null && state.startsWith("08")) {
					return true;
				}
			}

			String className = safe(current.getClass().getName());
			String message = safe(current.getMessage()).toLowerCase();

			if (className.contains("SQLTransientConnectionException")
					|| className.contains("SQLRecoverableException")) {
				return true;
			}

			if (message.contains("connection is closed")
					|| message.contains("connection has been closed")
					|| message.contains("broken pipe")
					|| message.contains("connection reset")
					|| message.contains("communications link failure")
					|| message.contains("could not open jdbc connection")
					|| message.contains("failed to obtain jdbc connection")
					|| message.contains("the connection attempt failed")
					|| message.contains("socket closed")
					|| message.contains("connection refused")
					|| message.contains("i/o error occurred while sending to the backend")) {
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
		return (s == null) ? "" : s;
	}
}
