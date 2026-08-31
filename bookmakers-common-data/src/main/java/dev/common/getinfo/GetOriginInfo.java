package dev.common.getinfo;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadOrigin;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.common.s3.S3Operator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class GetOriginInfo {

	private static final String PROJECT_NAME = "BM_B001";

	private static final String CLASS_NAME = "GetOriginInfo";

	/** 必要に応じて既存値へ合わせてください */
	private static final String OUTPUTS_CSV_KEY = "";

	/** 必要に応じて既存値へ合わせてください */
	private static final int THREAD_COUNT = 8;

	private static final long INVOKE_ALL_TIMEOUT_MINUTES = 10L;

	/**
	 * 想定キー:
	 * yyyy-MM-dd/mid=XXXX/seq=000001_yyyyMMddTHHmmss+0900.csv
	 */
	private static final Pattern KEY_PATTERN = Pattern
			.compile("^(\\d{4}-\\d{2}-\\d{2})/mid=([^/]+)/seq=([^_/]+)_(.+)\\.csv$");
	@Autowired
	private PathConfig config;
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;
	@Autowired
	private S3Operator s3Operator;
	@Autowired
	private ReadOrigin readOrigin;

	/**
	 * S3上の対象CSVを読み込み、S3 key -> DataEntity一覧 で返す。
	 * 戻り値のMapのキーは「ローカルファイルパス」ではなく「S3 key」であることに注意。
	 * ダウンロード先のローカル実ファイルパスが必要な場合は {@link #resolveLocalPath(String)} を使うこと。
	 */
	public LinkedHashMap<String, List<DataEntity>> getData(List<?> items) {
		final String METHOD_NAME = "getData";
		String bucket = config.getS3BucketsOutputs();
		String outputFolder = safeOutputFolder();
		// 1) 全走査して matcher に合うkeyだけ抽出
		List<String> matchedKeys = listAllMatchedKeys(bucket, OUTPUTS_CSV_KEY, items);
		log.info("[B001] S3 bucket={} prefix={} keys.size={} keys(sample)={}",
				bucket, OUTPUTS_CSV_KEY,
				(matchedKeys == null ? -1 : matchedKeys.size()),
				(matchedKeys == null ? null : matchedKeys.stream().limit(5).collect(Collectors.toList())));
		if (matchedKeys.isEmpty()) {
			manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP, "データなし(S3)");
			return new LinkedHashMap<>();
		}

		// 2) 要件どおりに並べ替え
		List<String> orderedKeys = orderKeysByDateThenMidEncounterThenSeqString(matchedKeys);
		try {
			Files.createDirectories(Paths.get(outputFolder));
		} catch (Exception e) {
			manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00003E_EXECUTION_SKIP,
					e, "outputFolder create failed: " + outputFolder);
			return new LinkedHashMap<>();
		}

		LinkedHashMap<String, List<DataEntity>> result = new LinkedHashMap<>();
		ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
		try {
			List<Callable<ReadOneResult>> tasks = new ArrayList<>();
			for (String s3Key : orderedKeys) {
				tasks.add(() -> readOne(bucket, s3Key, outputFolder));
			}
			List<Future<ReadOneResult>> futures = executor.invokeAll(tasks, INVOKE_ALL_TIMEOUT_MINUTES,
					TimeUnit.MINUTES);
			for (int i = 0; i < futures.size(); i++) {
				Future<ReadOneResult> f = futures.get(i);
				String targetKey = orderedKeys.get(i);
				if (f.isCancelled()) {
					manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00003E_EXECUTION_SKIP,
							null,
							"ReadOriginS3 timeout/cancel: s3Key=" + targetKey);
					continue;
				}
				try {
					ReadOneResult r = f.get();
					if (r == null) {
						manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME,
								MessageCdConst.MCD00003E_EXECUTION_SKIP,
								null,
								"ReadOriginS3 returned null: s3Key=" + targetKey);
						continue;
					}
					if (!r.ok) {
						log.error("[THROWABLE ERROR] ReadOriginS3 detail error: {}", r.thrown);
						manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME,
								MessageCdConst.MCD00003E_EXECUTION_SKIP,
								null,
								"ReadOriginS3 failed: phase=" + nvl(r.phase)
										+ ", resultCd=" + nvl(r.resultCd)
										+ ", s3Key=" + nvl(r.s3Key)
										+ ", localPath=" + nvl(r.localPath));
						continue;
					}
					result.put(r.s3Key, r.entities == null ? Collections.emptyList() : r.entities);
				} catch (Exception e) {
					manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00003E_EXECUTION_SKIP,
							e,
							"ReadOriginS3 future.get failed: s3Key=" + targetKey);
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00003E_EXECUTION_SKIP,
					e,
					"ReadOriginS3 interrupted");
			return result;
		} catch (Exception e) {
			manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00003E_EXECUTION_SKIP,
					e,
					"ReadOriginS3 unexpected error");
			return result;
		} finally {
			shutdownExecutor(executor);
		}
		return result;
	}

	/**
	 * getData()が返すMapのキー(S3 key)から、ダウンロード済みローカルCSVの実ファイルパスを逆算する。
	 * ローカル一時フォルダの命名規則は本クラスの{@link #safeOutputFolder()}に依存するため、
	 * OriginStatやPutOriginBackUpInfoなど他クラスからローカルCSVの実体を参照する場合は
	 * 必ずこのメソッド経由でパスを求めること(直接Mapのキーをローカルパスとして扱わないこと)。
	 */
	public static Path resolveLocalPath(String s3Key) {
		return buildSafeLocalPath(safeOutputFolder(), s3Key);
	}

	/**
	 * S3の1CSVをローカル保存し、readOriginで解析する。
	 */
	private ReadOneResult readOne(String bucket, String s3Key, String outputFolder) {
		final String METHOD_NAME = "readOne";
		Path local = buildSafeLocalPath(outputFolder, s3Key);
		try {
			Files.createDirectories(local.getParent());
		} catch (Exception e) {
			return ReadOneResult.fail(
					s3Key,
					local.toString(),
					e,
					ReadPhase.PREPARE_LOCAL_DIR.name(),
					null);
		}

		try {
			s3Operator.downloadToFile(bucket, s3Key, local);
		} catch (Exception e) {
			return ReadOneResult.fail(
					s3Key,
					local.toString(),
					e,
					ReadPhase.DOWNLOAD.name(),
					null);
		}

		try (InputStream is = Files.newInputStream(local)) {
			ReadFileOutputDTO dto = readOrigin.getFileBodyFromStream(is, s3Key);
			if (dto == null) {
				return ReadOneResult.fail(
						s3Key,
						local.toString(),
						null,
						ReadPhase.PARSE.name(),
						"DTO_NULL");
			}

			if (!BookMakersCommonConst.NORMAL_CD.equals(dto.getResultCd())) {
				return ReadOneResult.fail(
						s3Key,
						local.toString(),
						dto.getThrowAble(),
						ReadPhase.PARSE.name(),
						dto.getResultCd());
			}

			List<DataEntity> list = dto.getDataList();
			if (list == null) {
				list = Collections.emptyList();
			}

			for (DataEntity entity : list) {
				if (entity == null) {
					continue;
				}
				try {
					entity.setFile(s3Key);
				} catch (Exception ignore) {
					log.debug("[B001] setFile failed. s3Key={}", s3Key, ignore);
				}
			}
			return ReadOneResult.ok(s3Key, local.toString(), list);
		} catch (Exception e) {
			manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00003E_EXECUTION_SKIP,
					e,
					"ReadOrigin local read/parse failed: s3Key=" + s3Key + ", localPath=" + local);
			return ReadOneResult.fail(
					s3Key,
					local.toString(),
					e,
					ReadPhase.OPEN_STREAM_OR_PARSE.name(),
					null);
		}
	}

	/**
	 * prefix配下を全件見て、対象CSVかつ items に一致するものを抽出。
	 * 今の S3Operator の listKeys(bucket, prefix) を使う版。
	 */
	private List<String> listAllMatchedKeys(String bucket, String prefix, List<?> items) {
		final String METHOD_NAME = "listAllMatchedKeys";
		try {
			List<String> allKeys = s3Operator.listKeys(bucket, prefix == null ? "" : prefix);
			if (allKeys == null || allKeys.isEmpty()) {
				return Collections.emptyList();
			}
			List<String> matched = new ArrayList<>();
			for (String key : allKeys) {
				if (!isTargetCsvKey(key)) {
					continue;
				}
				if (!matchesAnyItem(key, items)) {
					continue;
				}
				matched.add(key);
			}
			return matched;
		} catch (Exception e) {
			manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00003E_EXECUTION_SKIP,
					e,
					"S3 list failed: bucket=" + bucket + ", prefix=" + prefix);
			return Collections.emptyList();
		}
	}

	/**
	 * 並び順:
	 * 1. date 昇順
	 * 2. 同一 date 内は mid 初登場順
	 * 3. 同一 mid 内は seq 文字列順
	 */
	private List<String> orderKeysByDateThenMidEncounterThenSeqString(List<String> matchedKeys) {
		if (matchedKeys == null || matchedKeys.isEmpty()) {
			return Collections.emptyList();
		}
		Map<String, Map<String, Integer>> dateMidEncounterOrder = new LinkedHashMap<>();
		for (String key : matchedKeys) {
			KeyParts p = parseKeyParts(key);
			String date = p.date;
			String mid = p.mid;
			dateMidEncounterOrder
					.computeIfAbsent(date, d -> new LinkedHashMap<>())
					.computeIfAbsent(mid, m -> dateMidEncounterOrder.get(date).size());
		}
		List<String> ordered = new ArrayList<>(matchedKeys);
		ordered.sort(
				Comparator.comparing((String key) -> parseKeyParts(key).date, Comparator.nullsLast(String::compareTo))
						.thenComparingInt(key -> {
							KeyParts p = parseKeyParts(key);
							return dateMidEncounterOrder
									.getOrDefault(p.date, Collections.emptyMap())
									.getOrDefault(p.mid, Integer.MAX_VALUE);
						})
						.thenComparing(key -> parseKeyParts(key).seq, Comparator.nullsLast(String::compareTo))
						.thenComparing(String::compareTo));
		return ordered;
	}

	private boolean isTargetCsvKey(String key) {
		if (key == null || key.isEmpty()) {
			return false;
		}
		String lower = key.toLowerCase(Locale.ROOT);
		return lower.endsWith(".csv") && KEY_PATTERN.matcher(key).matches();
	}

	/**
	 * items の実型が不明なので、文字列化＋代表getter群で雑に吸う。
	 */
	private boolean matchesAnyItem(String s3Key, List<?> items) {
		if (items == null || items.isEmpty()) {
			return true;
		}
		Set<String> candidates = new LinkedHashSet<>();
		candidates.add(s3Key);
		KeyParts p = parseKeyParts(s3Key);
		if (p.mid != null && !p.mid.isEmpty()) {
			candidates.add(p.mid);
		}
		if (p.date != null && !p.date.isEmpty()) {
			candidates.add(p.date);
		}
		if (p.seq != null && !p.seq.isEmpty()) {
			candidates.add(p.seq);
		}
		for (Object item : items) {
			for (String token : extractMatchTokens(item)) {
				if (token == null || token.trim().isEmpty()) {
					continue;
				}
				String normalized = token.trim();
				for (String c : candidates) {
					if (normalized.equals(c)) {
						return true;
					}
				}
				if (s3Key.contains(normalized)) {
					return true;
				}
			}
		}
		return false;
	}

	private List<String> extractMatchTokens(Object item) {
		if (item == null) {
			return Collections.emptyList();
		}
		Set<String> tokens = new LinkedHashSet<>();
		if (item instanceof CharSequence) {
			tokens.add(item.toString());
			return new ArrayList<>(tokens);
		}
		tokens.add(Objects.toString(item, ""));
		addTokenByMethod(item, "getMatchKey", tokens);
		addTokenByMethod(item, "getMid", tokens);
		addTokenByMethod(item, "getS3Key", tokens);
		addTokenByMethod(item, "getKey", tokens);
		addTokenByMethod(item, "getId", tokens);
		return new ArrayList<>(tokens);
	}

	private void addTokenByMethod(Object item, String methodName, Set<String> tokens) {
		try {
			Method m = item.getClass().getMethod(methodName);
			Object v = m.invoke(item);
			if (v == null) {
				return;
			}
			if (v instanceof Collection<?>) {
				for (Object x : (Collection<?>) v) {
					if (x != null) {
						tokens.add(Objects.toString(x, ""));
					}
				}
			} else {
				tokens.add(Objects.toString(v, ""));
			}
		} catch (Exception ignore) {
			// 対象getterが無いのは正常
		}
	}

	private static Path buildSafeLocalPath(String outputFolder, String s3Key) {
		String normalized = trimLeadingSlash(s3Key);
		return Paths.get(outputFolder, normalized);
	}

	private static String trimLeadingSlash(String s) {
		if (s == null || s.isEmpty()) {
			return "";
		}
		int i = 0;
		while (i < s.length() && s.charAt(i) == '/') {
			i++;
		}
		return s.substring(i);
	}

	private static String safeOutputFolder() {
		try {
			String tmp = System.getProperty("java.io.tmpdir");
			if (tmp == null || tmp.trim().isEmpty()) {
				return "/tmp/get-origin-info";
			}
			return Paths.get(tmp, "get-origin-info").toString();
		} catch (Exception e) {
			return "/tmp/get-origin-info";
		}
	}

	private void shutdownExecutor(ExecutorService executor) {
		if (executor == null) {
			return;
		}
		executor.shutdown();
		try {
			if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
				executor.shutdownNow();
				if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
					log.warn("[B001] executor shutdown not completed");
				}
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
			log.warn("[B001] executor shutdown interrupted", e);
		}
	}

	private KeyParts parseKeyParts(String key) {
		if (key == null) {
			return KeyParts.EMPTY;
		}
		Matcher m = KEY_PATTERN.matcher(key);
		if (!m.matches()) {
			return new KeyParts("", "", "", "");
		}
		return new KeyParts(
				nvl(m.group(1)),
				nvl(m.group(2)),
				nvl(m.group(3)),
				nvl(m.group(4)));
	}

	private String nvl(String s) {
		return s == null ? "" : s;
	}

	private enum ReadPhase {
		PREPARE_LOCAL_DIR, DOWNLOAD, PARSE, OPEN_STREAM_OR_PARSE
	}

	private static class KeyParts {
		static final KeyParts EMPTY = new KeyParts("", "", "", "");
		final String date;
		final String mid;
		final String seq;

		KeyParts(String date, String mid, String seq, String timestamp) {
			this.date = date;
			this.mid = mid;
			this.seq = seq;
		}
	}

	private static class ReadOneResult {
		final boolean ok;
		final String s3Key;
		final String localPath;
		final List<DataEntity> entities;
		final Throwable thrown;
		final String phase;
		final String resultCd;

		private ReadOneResult(
				boolean ok,
				String s3Key,
				String localPath,
				List<DataEntity> entities,
				Throwable thrown,
				String phase,
				String resultCd) {
			this.ok = ok;
			this.s3Key = s3Key;
			this.localPath = localPath;
			this.entities = entities;
			this.thrown = thrown;
			this.phase = phase;
			this.resultCd = resultCd;
		}

		static ReadOneResult ok(String s3Key, String localPath, List<DataEntity> entities) {
			return new ReadOneResult(
					true,
					s3Key,
					localPath,
					entities,
					null,
					null,
					BookMakersCommonConst.NORMAL_CD);
		}

		static ReadOneResult fail(
				String s3Key,
				String localPath,
				Throwable e,
				String phase,
				String resultCd) {
			return new ReadOneResult(
					false,
					s3Key,
					localPath,
					null,
					e,
					phase,
					resultCd);
		}
	}
}