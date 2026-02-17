package dev.common.getinfo;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
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

	private static final Pattern SEQ_CSV_KEY = Pattern.compile("^\\d+\\.csv$"); // 例: 1.csv, 12.csv

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

		List<String> keys = s3Operator.listSeqCsvKeysInRoot(bucket, SEQ_CSV_KEY);
		keys = filterKeysBySeqRange(keys, csvNumber, csvBackNumber);

		log.info("[GetStatMap] bucket={} keys.size={}", bucket, (keys == null ? -1 : keys.size()));

		Map<String, Map<String, List<BookDataEntity>>> result = new HashMap<>();
		if (keys == null || keys.isEmpty())
			return result;

		// OOM対策：同時実行を小さく（まず2推奨）
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
					if (list == null || list.isEmpty())
						return;

					BookDataEntity first = list.get(0);
					String category = first.getGameTeamCategory();
					String home = first.getHomeTeamName();
					String away = first.getAwayTeamName();
					if (category == null || home == null || away == null)
						return;

					String versus = home + "-" + away;

					synchronized (result) {
						result.computeIfAbsent(category, k -> new HashMap<>())
								.computeIfAbsent(versus, k -> new ArrayList<>())
								.addAll(list);
					}

				} catch (Exception ignore) {
					// 読めないCSVはスキップ（必要ならwarn）
				}
			}, csvTaskExecutor).whenComplete((r, t) -> gate.release()));
		}

		for (CompletableFuture<Void> f : futures)
			f.join();
		return result;
	}

	/**
	 * ★ ExportCsv / ReaderCurrentCsvInfoBean 用：
	 * 既存CSVの「組み合わせ復元」専用
	 * @return key: category_home-away_fileNo, value: seq(昇順・重複なし)
	 */
	public Map<String, List<Integer>> getCsvInfo(String csvNumber, String csvBackNumber) {

		String bucket = config.getS3BucketsStats();

		List<String> keys = s3Operator.listSeqCsvKeysInRoot(bucket, SEQ_CSV_KEY);
		keys = filterKeysBySeqRange(keys, csvNumber, csvBackNumber);

		log.info("[GetCsvInfo] bucket={} keys.size={}", bucket, (keys == null ? -1 : keys.size()));

		if (keys == null || keys.isEmpty())
			return new LinkedHashMap<>();

		// 集約：key -> seqSet（TreeSetで重複除外＋昇順）
		Map<String, Set<Integer>> acc = new LinkedHashMap<>();

		// OOM対策：同時実行を絞る（2〜4推奨）
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
					if (idx == null)
						return;

					if (idx.getFileNo() == null
							|| idx.getCategory() == null
							|| idx.getHome() == null
							|| idx.getAway() == null) {
						return;
					}

					String versus = idx.getHome() + "-" + idx.getAway();
					String mapKey = idx.getCategory() + "_" + versus + "_" + idx.getFileNo();

					synchronized (acc) {
						acc.computeIfAbsent(mapKey, k -> new TreeSet<>()).addAll(idx.getSeqs());
					}

				} catch (Exception ignore) {
					// 読めないCSVはスキップ
				}
			}, csvTaskExecutor).whenComplete((r, t) -> gate.release()));
		}

		for (CompletableFuture<Void> f : futures)
			f.join();

		// TreeSet -> List へ確定（fileNoの昇順でLinkedHashMapに）
		return acc.entrySet().stream()
				.sorted(Comparator.comparingInt(e -> fileNoFromKey(e.getKey())))
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						e -> new ArrayList<>(e.getValue()),
						(a, b) -> a,
						LinkedHashMap::new));
	}

	// GetStatInfo に追加（S3のキー一覧だけ見て最大番号を返す）
	public int getMaxCsvNo(String csvNumber, String csvBackNumber) {

		String bucket = config.getS3BucketsStats();

		List<String> keys = s3Operator.listSeqCsvKeysInRoot(bucket, SEQ_CSV_KEY);
		keys = filterKeysBySeqRange(keys, csvNumber, csvBackNumber);

		int max = 0;
		if (keys == null)
			return 0;

		for (String key : keys) {
			Integer n = extractSeqFromKey(key); // 既存 private static をそのまま使う
			if (n != null && n > max)
				max = n;
		}
		return max;
	}

	private static int fileNoFromKey(String key) {
		int idx = key.lastIndexOf('_');
		if (idx < 0)
			return Integer.MAX_VALUE;
		try {
			return Integer.parseInt(key.substring(idx + 1));
		} catch (NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
	}

	// ===== 既存の filter はそのまま =====

	public static List<String> filterKeysBySeqRange(List<String> keys, String csvNumber, String csvBackNumber) {
		Integer from = null;
		Integer to = null;

		try {
			if (csvNumber != null && !csvNumber.isBlank())
				from = Integer.parseInt(csvNumber.trim());
		} catch (NumberFormatException ignore) {
		}
		try {
			if (csvBackNumber != null && !csvBackNumber.isBlank())
				to = Integer.parseInt(csvBackNumber.trim());
		} catch (NumberFormatException ignore) {
		}

		if (from == null && to == null)
			return keys;

		final Integer fFrom = from;
		final Integer fTo = to;

		return keys.stream()
				.filter(k -> {
					Integer seq = extractSeqFromKey(k);
					if (seq == null)
						return false;
					if (fFrom != null && seq < fFrom)
						return false;
					if (fTo != null && seq > fTo)
						return false;
					return true;
				})
				.collect(Collectors.toList());
	}

	private static Integer extractSeqFromKey(String key) {
		if (key == null)
			return null;
		var m = java.util.regex.Pattern.compile("^(\\d+)\\.csv$").matcher(key);
		if (!m.find())
			return null;
		try {
			return Integer.parseInt(m.group(1));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
