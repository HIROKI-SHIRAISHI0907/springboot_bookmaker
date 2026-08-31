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
import java.util.stream.Stream;

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
import dev.common.zip.ZipArchiveHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * バックアップ用バケットからCSVを取得するクラス。
 *
 * <p>
 * バケットにはCSVが個別に置かれているのではなく、mid単位でzip化された
 * {@code mid名.zip}(= {@code bucket/mid名.zip})として格納されている。
 * 本クラスは対象のmidを決定し、その {@code mid名.zip} をダウンロード・解凍したうえで、
 * 中に含まれるCSVをファイル名(zip内相対パス)の昇順で読み込む。
 * </p>
 * <p>
 * zip内のCSVは {@code yyyy-MM-dd/mid=XXXX/seq=000001_yyyyMMddTHHmmss+0900.csv}
 * という形式であることを前提とする。
 * </p>
 */
@Slf4j
@Component
public class GetOriginBackUpInfo {

	private static final String PROJECT_NAME = "BM_B001_BACK_UP";
	private static final String CLASS_NAME = "GetOriginBackUpInfo";

	private static final String ZIP_EXTENSION = ".zip";

	private static final int THREAD_COUNT = 8;
	private static final long INVOKE_ALL_TIMEOUT_MINUTES = 10L;

	/**
	 * 想定キー(zip内CSVエントリの相対パス):
	 * yyyy-MM-dd/mid=XXXX/seq=000001_yyyyMMddTHHmmss+0900.csv
	 */
	private static final Pattern KEY_PATTERN =
			Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})/mid=([^/]+)/seq=([^_/]+)_(.+)\\.csv$");

	@Autowired
	private PathConfig config;
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;
	@Autowired
	private S3Operator s3Operator;
	@Autowired
	private ReadOrigin readOrigin;
	@Autowired
	private ZipArchiveHandler zipArchiveHandler;

	/**
	 * S3上の {@code bucket/mid名.zip} を取得・解凍し、中のCSVを読み込んで
	 * zip内相対パス(CSVキー相当) -> DataEntity一覧 で返す。
	 */
	public LinkedHashMap<String, List<DataEntity>> getData(List<?> items) {
		final String METHOD_NAME = "getData";

		String bucket = config.getS3BucketsOutputsBackUp();
		String outputFolder = safeOutputFolder();

		// 1) 対象mid一覧を決定(items未指定時はバケット直下の*.zipを全走査)
		List<String> targetMids = resolveTargetMids(bucket, items);
		log.info("[B001] S3 bucket={} targetMids.size={} targetMids(sample)={}",
				bucket,
				(targetMids == null ? -1 : targetMids.size()),
				(targetMids == null ? null : targetMids.stream().limit(5).collect(Collectors.toList())));

		if (targetMids == null || targetMids.isEmpty()) {
			manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP, "データなし(S3)");
			return new LinkedHashMap<>();
		}

		try {
			Files.createDirectories(Paths.get(outputFolder));
		} catch (Exception e) {
			manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00003E_EXECUTION_SKIP,
					e, "outputFolder create failed: " + outputFolder);
			return new LinkedHashMap<>();
		}

		// 2) mid毎にzip(bucket/mid名.zip)をDL・解凍し、中のCSVエントリを集める
		List<CsvEntry> csvEntries = new ArrayList<>();
		for (String mid : targetMids) {
			csvEntries.addAll(downloadAndExtractMidZip(bucket, mid, outputFolder));
		}

		if (csvEntries.isEmpty()) {
			manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP, "データなし(zip展開後)");
			return new LinkedHashMap<>();
		}

		// 3) items指定があればさらに絞り込み
		List<CsvEntry> matchedEntries = csvEntries.stream()
				.filter(e -> matchesAnyItem(e.entryKey, items))
				.collect(Collectors.toList());

		// 4) ファイル名(zip内相対パス)昇順にソート
		matchedEntries.sort(Comparator.comparing(e -> e.entryKey));

		// 5) 並列でCSVを読み込み
		LinkedHashMap<String, List<DataEntity>> result = new LinkedHashMap<>();
		ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
		try {
			List<Callable<ReadOneResult>> tasks = new ArrayList<>();
			for (CsvEntry entry : matchedEntries) {
				tasks.add(() -> readOne(entry));
			}
			List<Future<ReadOneResult>> futures =
					executor.invokeAll(tasks, INVOKE_ALL_TIMEOUT_MINUTES, TimeUnit.MINUTES);
			for (int i = 0; i < futures.size(); i++) {
				Future<ReadOneResult> f = futures.get(i);
				String targetKey = matchedEntries.get(i).entryKey;
				if (f.isCancelled()) {
					manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00003E_EXECUTION_SKIP,
							null,
							"ReadOrigin timeout/cancel: entryKey=" + targetKey);
					continue;
				}
				try {
					ReadOneResult r = f.get();
					if (r == null) {
						manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME,
								MessageCdConst.MCD00003E_EXECUTION_SKIP,
								null,
								"ReadOrigin returned null: entryKey=" + targetKey);
						continue;
					}
					if (!r.ok) {
						log.error("[THROWABLE ERROR] ReadOrigin detail error: {}", r.thrown);
						manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME,
								MessageCdConst.MCD00003E_EXECUTION_SKIP,
								null,
								"ReadOrigin failed: phase=" + nvl(r.phase)
										+ ", resultCd=" + nvl(r.resultCd)
										+ ", entryKey=" + nvl(r.entryKey)
										+ ", localPath=" + nvl(r.localPath));
						continue;
					}
					result.put(r.entryKey, r.entities == null ? Collections.emptyList() : r.entities);
				} catch (Exception e) {
					manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00003E_EXECUTION_SKIP,
							e,
							"ReadOrigin future.get failed: entryKey=" + targetKey);
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00003E_EXECUTION_SKIP,
					e,
					"ReadOrigin interrupted");
			return result;
		} catch (Exception e) {
			manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00003E_EXECUTION_SKIP,
					e,
					"ReadOrigin unexpected error");
			return result;
		} finally {
			shutdownExecutor(executor);
		}

		return result;
	}

	/**
	 * {@code bucket/mid名.zip} をダウンロードし、outputFolder配下に解凍する。
	 * 解凍後、対象パターンに一致するCSVエントリ(zip内相対パス)一覧を返す。
	 */
	private List<CsvEntry> downloadAndExtractMidZip(String bucket, String mid, String outputFolder) {
		final String METHOD_NAME = "downloadAndExtractMidZip";

		String zipKey = mid + ZIP_EXTENSION;
		Path localZip = Paths.get(outputFolder, "_zip", zipKey);
		Path extractDir = Paths.get(outputFolder, "mid=" + mid);

		try {
			Files.createDirectories(localZip.getParent());
			s3Operator.downloadToFile(bucket, zipKey, localZip);
		} catch (Exception e) {
			manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00003E_EXECUTION_SKIP,
					e,
					"zip download failed: bucket=" + bucket + ", zipKey=" + zipKey);
			return Collections.emptyList();
		}

		try {
			zipArchiveHandler.decompress(localZip, extractDir);
		} catch (Exception e) {
			manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00003E_EXECUTION_SKIP,
					e,
					"zip decompress failed: zipKey=" + zipKey + ", localZip=" + localZip);
			return Collections.emptyList();
		}

		List<CsvEntry> entries = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(extractDir)) {
			List<Path> files = walk
					.filter(Files::isRegularFile)
					.collect(Collectors.toList());
			for (Path csvFile : files) {
				String relative = extractDir.relativize(csvFile).toString().replace('\\', '/');
				if (!isTargetCsvKey(relative)) {
					log.debug("[B001] zip内対象外エントリをスキップ: mid={}, entry={}", mid, relative);
					continue;
				}
				entries.add(new CsvEntry(relative, csvFile));
			}
		} catch (Exception e) {
			manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00003E_EXECUTION_SKIP,
					e,
					"zip展開後の走査に失敗: extractDir=" + extractDir);
			return Collections.emptyList();
		}
		return entries;
	}

	/**
	 * 解凍済みローカルCSVを読み込み、DataEntity一覧に変換する。
	 */
	private ReadOneResult readOne(CsvEntry entry) {
		final String METHOD_NAME = "readOne";
		try (InputStream is = Files.newInputStream(entry.localPath)) {
			ReadFileOutputDTO dto = readOrigin.getFileBodyFromStream(is, entry.entryKey);
			if (dto == null) {
				return ReadOneResult.fail(
						entry.entryKey, entry.localPath.toString(), null, ReadPhase.PARSE.name(), "DTO_NULL");
			}
			if (!BookMakersCommonConst.NORMAL_CD.equals(dto.getResultCd())) {
				return ReadOneResult.fail(
						entry.entryKey, entry.localPath.toString(), dto.getThrowAble(), ReadPhase.PARSE.name(), dto.getResultCd());
			}
			List<DataEntity> list = dto.getDataList();
			if (list == null) {
				list = Collections.emptyList();
			}
			for (DataEntity de : list) {
				if (de == null) {
					continue;
				}
				try {
					de.setFile(entry.entryKey);
				} catch (Exception ignore) {
					log.debug("[B001] setFile failed. entryKey={}", entry.entryKey, ignore);
				}
			}
			return ReadOneResult.ok(entry.entryKey, entry.localPath.toString(), list);
		} catch (Exception e) {
			manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00003E_EXECUTION_SKIP,
					e,
					"local read/parse failed: entryKey=" + entry.entryKey + ", localPath=" + entry.localPath);
			return ReadOneResult.fail(
					entry.entryKey, entry.localPath.toString(), e, ReadPhase.OPEN_STREAM_OR_PARSE.name(), null);
		}
	}

	/**
	 * itemsから対象mid一覧を決定する。
	 * <p>
	 * items内の各要素からgetMid()等で値が取得できればそれを対象midとする。
	 * items未指定(null/空)の場合は、バケット直下に存在する {@code *.zip} を全走査し、
	 * ファイル名(拡張子除く)をmidとして扱う。
	 * </p>
	 */
	private List<String> resolveTargetMids(String bucket, List<?> items) {
		final String METHOD_NAME = "resolveTargetMids";

		if (items == null || items.isEmpty()) {
			try {
				List<String> allKeys = s3Operator.listKeys(bucket, "");
				if (allKeys == null || allKeys.isEmpty()) {
					return Collections.emptyList();
				}
				LinkedHashSet<String> mids = new LinkedHashSet<>();
				for (String key : allKeys) {
					if (key == null) {
						continue;
					}
					String lower = key.toLowerCase(Locale.ROOT);
					if (!lower.endsWith(ZIP_EXTENSION)) {
						continue;
					}
					String fileName = key.substring(key.lastIndexOf('/') + 1);
					String mid = fileName.substring(0, fileName.length() - ZIP_EXTENSION.length());
					if (!mid.isEmpty()) {
						mids.add(mid);
					}
				}
				return new ArrayList<>(mids);
			} catch (Exception e) {
				manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00003E_EXECUTION_SKIP,
						e,
						"S3 list failed(zip一覧取得): bucket=" + bucket);
				return Collections.emptyList();
			}
		}

		LinkedHashSet<String> mids = new LinkedHashSet<>();
		for (Object item : items) {
			for (String token : extractMatchTokens(item)) {
				if (token == null || token.trim().isEmpty()) {
					continue;
				}
				mids.add(token.trim());
			}
		}
		return new ArrayList<>(mids);
	}

	private boolean isTargetCsvKey(String relativeKey) {
		if (relativeKey == null || relativeKey.isEmpty()) {
			return false;
		}
		String lower = relativeKey.toLowerCase(Locale.ROOT);
		return lower.endsWith(".csv") && KEY_PATTERN.matcher(relativeKey).matches();
	}

	/**
	 * items の実型が不明なので、文字列化＋代表getter群で雑に吸う。
	 */
	private boolean matchesAnyItem(String entryKey, List<?> items) {
		if (items == null || items.isEmpty()) {
			return true;
		}
		Set<String> candidates = new LinkedHashSet<>();
		candidates.add(entryKey);
		KeyParts p = parseKeyParts(entryKey);
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
				if (entryKey.contains(normalized)) {
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

	private String safeOutputFolder() {
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
			return new KeyParts("", "", "");
		}
		return new KeyParts(nvl(m.group(1)), nvl(m.group(2)), nvl(m.group(3)));
	}

	private String nvl(String s) {
		return s == null ? "" : s;
	}

	private enum ReadPhase {
		PREPARE_LOCAL_DIR,
		DOWNLOAD,
		PARSE,
		OPEN_STREAM_OR_PARSE
	}

	private static class KeyParts {
		static final KeyParts EMPTY = new KeyParts("", "", "");
		final String date;
		final String mid;
		final String seq;

		KeyParts(String date, String mid, String seq) {
			this.date = date;
			this.mid = mid;
			this.seq = seq;
		}
	}

	private static class CsvEntry {
		final String entryKey;
		final Path localPath;

		CsvEntry(String entryKey, Path localPath) {
			this.entryKey = entryKey;
			this.localPath = localPath;
		}
	}

	private static class ReadOneResult {
		final boolean ok;
		final String entryKey;
		final String localPath;
		final List<DataEntity> entities;
		final Throwable thrown;
		final String phase;
		final String resultCd;

		private ReadOneResult(
				boolean ok,
				String entryKey,
				String localPath,
				List<DataEntity> entities,
				Throwable thrown,
				String phase,
				String resultCd) {
			this.ok = ok;
			this.entryKey = entryKey;
			this.localPath = localPath;
			this.entities = entities;
			this.thrown = thrown;
			this.phase = phase;
			this.resultCd = resultCd;
		}

		static ReadOneResult ok(String entryKey, String localPath, List<DataEntity> entities) {
			return new ReadOneResult(true, entryKey, localPath, entities, null, null, BookMakersCommonConst.NORMAL_CD);
		}

		static ReadOneResult fail(String entryKey, String localPath, Throwable e, String phase, String resultCd) {
			return new ReadOneResult(false, entryKey, localPath, null, e, phase, resultCd);
		}
	}
}