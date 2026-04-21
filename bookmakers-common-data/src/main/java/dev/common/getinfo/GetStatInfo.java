package dev.common.getinfo;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.entity.BookDataEntity;
import dev.common.readfile.ReadStat;
import dev.common.readfile.StatCsvIndexDTO;
import dev.common.s3.S3Operator;

@Component
public class GetStatInfo {

	private static final Logger log = LoggerFactory.getLogger(GetStatInfo.class);

	/**
	 * 直下/サブフォルダ配下を問わず、末尾が数値CSVのキーを対象にする。
	 * 例:
	 * 1.csv
	 * Japan-J1-ラウンド5/9.csv
	 * stats/Japan-J1-ラウンド5/9.csv
	 */
	private static final Pattern SEQ_CSV_KEY = Pattern.compile("^(?:.+/)?\\d+\\.csv$");

	/** 末尾ファイル名のCSV番号抽出 */
	private static final Pattern CSV_NO_PATTERN = Pattern.compile("(^|.*/)(\\d+)\\.csv$");

	@Autowired
	private S3Operator s3Operator;

	@Autowired
	private PathConfig config;

	@Autowired
	private ReadStat readStat;

	@Autowired
	private ThreadPoolTaskExecutor csvTaskExecutor;

	/**
	 * ★ CoreStat 用：S3 CSVを読み込んで従来型で返す
	 * key: category, value: (home-away -> entities)
	 */
	public Map<String, Map<String, List<BookDataEntity>>> getStatMap(String csvNumber, String csvBackNumber) {

		String bucket = config.getS3BucketsStats();

		List<String> keys = listAllSeqCsvKeys(bucket);
		keys = filterKeysBySeqRange(keys, csvNumber, csvBackNumber);

		log.info("[GetStatMap] bucket={} keys.size={}", bucket, (keys == null ? -1 : keys.size()));

		Map<String, Map<String, List<BookDataEntity>>> result = new HashMap<>();
		if (keys == null || keys.isEmpty()) {
			return result;
		}

		final int concurrency = 2;
		final Semaphore gate = new Semaphore(concurrency);

		List<CompletableFuture<Void>> futures = new ArrayList<>(keys.size());

		for (String key : keys) {
			try {
				gate.acquire();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}

			futures.add(CompletableFuture.runAsync(() -> {
				try (InputStream is = s3Operator.download(bucket, key)) {

					List<BookDataEntity> list = readStat.readEntities(is, key);
					if (list == null || list.isEmpty()) {
						return;
					}

					BookDataEntity first = list.get(0);
					String category = first.getGameTeamCategory();
					String home = first.getHomeTeamName();
					String away = first.getAwayTeamName();
					if (category == null || home == null || away == null) {
						return;
					}

					String versus = home + "-" + away;

					synchronized (result) {
						result.computeIfAbsent(category, k -> new HashMap<>())
								.computeIfAbsent(versus, k -> new ArrayList<>())
								.addAll(list);
					}

				} catch (Exception ignore) {
					// 読めないCSVはスキップ
				}
			}, csvTaskExecutor).whenComplete((r, t) -> gate.release()));
		}

		for (CompletableFuture<Void> f : futures) {
			f.join();
		}
		return result;
	}

	/**
	 * ★ ExportCsv / ReaderCurrentCsvInfoBean 用：
	 * 既存CSVの「組み合わせ復元」専用
	 *
	 * @return key: S3相対キー（例: Japan-J1-ラウンド5/9.csv）, value: seq(昇順・重複なし)
	 */
	public Map<String, List<Integer>> getCsvInfo(String csvNumber, String csvBackNumber) {

		String bucket = config.getS3BucketsStats();

		List<String> keys = listAllSeqCsvKeys(bucket);
		keys = filterKeysBySeqRange(keys, csvNumber, csvBackNumber);

		log.info("[GetCsvInfo] bucket={} keys.size={}", bucket, (keys == null ? -1 : keys.size()));

		if (keys == null || keys.isEmpty()) {
			return new LinkedHashMap<>();
		}

		Map<String, Set<Integer>> acc = new LinkedHashMap<>();

		final int concurrency = 2;
		final Semaphore gate = new Semaphore(concurrency);

		List<CompletableFuture<Void>> futures = new ArrayList<>(keys.size());
		for (String key : keys) {
			try {
				gate.acquire();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}

			futures.add(CompletableFuture.runAsync(() -> {
				try (InputStream is = s3Operator.download(bucket, key)) {

					StatCsvIndexDTO idx = readStat.readIndex(is, key);
					if (idx == null) {
						return;
					}

					// key 自体を一意な識別子として使う
					synchronized (acc) {
						acc.computeIfAbsent(key, k -> new TreeSet<>()).addAll(idx.getSeqs());
					}

				} catch (Exception ignore) {
					// 読めないCSVはスキップ
				}
			}, csvTaskExecutor).whenComplete((r, t) -> gate.release()));
		}

		for (CompletableFuture<Void> f : futures) {
			f.join();
		}

		return acc.entrySet().stream()
				.sorted((a, b) -> compareCsvKey(a.getKey(), b.getKey()))
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						e -> new ArrayList<>(e.getValue()),
						(x, y) -> x,
						LinkedHashMap::new));
	}

	/**
	 * BookDataEntity一覧から CoreStat 用Mapを作成
	 * key: category, value: (home-away -> entities)
	 */
	public Map<String, Map<String, List<BookDataEntity>>> buildStatMapFromEntities(List<BookDataEntity> list) {

		Map<String, Map<String, List<BookDataEntity>>> result = new HashMap<>();
		if (list == null || list.isEmpty()) {
			return result;
		}

		for (BookDataEntity entity : list) {
			if (entity == null) {
				continue;
			}

			String category = entity.getGameTeamCategory();
			String home = entity.getHomeTeamName();
			String away = entity.getAwayTeamName();

			if (category == null || home == null || away == null) {
				continue;
			}

			String versus = home + "-" + away;

			result.computeIfAbsent(category, k -> new HashMap<>())
				  .computeIfAbsent(versus, k -> new ArrayList<>())
				  .add(entity);
		}

		return result;
	}

	/** キー一覧を返す */
	public List<String> listCsvKeysInRange(String csvNumber, String csvBackNumber) {
		String bucket = config.getS3BucketsStats();
		List<String> keys = listAllSeqCsvKeys(bucket);
		keys = filterKeysBySeqRange(keys, csvNumber, csvBackNumber);
		keys.sort(GetStatInfo::compareCsvKey);
		return keys;
	}

	/** 1ファイル分だけ Map を作る */
	public Map<String, Map<String, List<BookDataEntity>>> getStatMapForSingleKey(String key) {
		String bucket = config.getS3BucketsStats();

		Map<String, Map<String, List<BookDataEntity>>> result = new HashMap<>();

		try (InputStream is = s3Operator.download(bucket, key)) {
			List<BookDataEntity> list = readStat.readEntities(is, key);
			if (list == null || list.isEmpty()) {
				return result;
			}

			BookDataEntity first = list.get(0);
			String category = first.getGameTeamCategory();
			String home = first.getHomeTeamName();
			String away = first.getAwayTeamName();
			if (category == null || home == null || away == null) {
				return result;
			}

			String versus = home + "-" + away;

			result.computeIfAbsent(category, k -> new HashMap<>())
				  .computeIfAbsent(versus, k -> new ArrayList<>())
				  .addAll(list);

		} catch (Exception e) {
			log.warn("[getStatMapForSingleKey] failed key={}", key, e);
		}

		return result;
	}

	/**
	 * 末尾CSV番号ベースで最大番号を返す。
	 *
	 * <p>
	 * 新構成では番号は「フォルダ単位」で採番されるため、
	 * このメソッドの戻り値は全体最大値としての参考値であり、
	 * 新規採番には使用しないこと。
	 * </p>
	 */
	public int getMaxCsvNo(String csvNumber, String csvBackNumber) {

		String bucket = config.getS3BucketsStats();

		List<String> keys = listAllSeqCsvKeys(bucket);
		keys = filterKeysBySeqRange(keys, csvNumber, csvBackNumber);

		int max = 0;
		if (keys == null) {
			return 0;
		}

		for (String key : keys) {
			Integer n = extractSeqFromKey(key);
			if (n != null && n > max) {
				max = n;
			}
		}
		return max;
	}

	/**
	 * バケット配下から対象CSVキーをすべて取得する。
	 */
	private List<String> listAllSeqCsvKeys(String bucket) {
		List<String> keys = s3Operator.listKeys(bucket, "");
		if (keys == null || keys.isEmpty()) {
			return Collections.emptyList();
		}

		return keys.stream()
				.filter(k -> k != null && SEQ_CSV_KEY.matcher(k).matches())
				.collect(Collectors.toList());
	}

	public static List<String> filterKeysBySeqRange(List<String> keys, String csvNumber, String csvBackNumber) {
		Integer from = null;
		Integer to = null;

		try {
			if (csvNumber != null && !csvNumber.isBlank()) {
				from = Integer.parseInt(csvNumber.trim());
			}
		} catch (NumberFormatException ignore) {
		}
		try {
			if (csvBackNumber != null && !csvBackNumber.isBlank()) {
				to = Integer.parseInt(csvBackNumber.trim());
			}
		} catch (NumberFormatException ignore) {
		}

		if (from == null && to == null) {
			return keys;
		}

		final Integer fFrom = from;
		final Integer fTo = to;

		return keys.stream()
				.filter(k -> {
					Integer seq = extractSeqFromKey(k);
					if (seq == null) {
						return false;
					}
					if (fFrom != null && seq < fFrom) {
						return false;
					}
					if (fTo != null && seq > fTo) {
						return false;
					}
					return true;
				})
				.collect(Collectors.toList());
	}

	private static Integer extractSeqFromKey(String key) {
		if (key == null) {
			return null;
		}
		Matcher m = CSV_NO_PATTERN.matcher(key);
		if (!m.find()) {
			return null;
		}
		try {
			return Integer.parseInt(m.group(2));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static int compareCsvKey(String a, String b) {
		String pa = parentPath(a);
		String pb = parentPath(b);

		int folderCompare = pa.compareTo(pb);
		if (folderCompare != 0) {
			return folderCompare;
		}

		Integer na = extractSeqFromKey(a);
		Integer nb = extractSeqFromKey(b);

		if (na == null && nb == null) {
			return a.compareTo(b);
		}
		if (na == null) {
			return 1;
		}
		if (nb == null) {
			return -1;
		}
		return Integer.compare(na, nb);
	}

	private static String parentPath(String key) {
		if (key == null) {
			return "";
		}
		int idx = key.lastIndexOf('/');
		return (idx >= 0) ? key.substring(0, idx) : "";
	}
}
