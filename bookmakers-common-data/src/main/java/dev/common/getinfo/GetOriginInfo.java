package dev.common.getinfo;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadOrigin;
import dev.common.readfile.dto.MatchKeyItem;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.common.s3.S3Operator;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * 起源データ取得管理クラス
 *
 * 要件:
 * 1) yyyy-MM-dd/ で昇順
 * 2) 同一日付内の mid=... は「S3で見えた順」（＝出現順）を維持
 * 3) 同一 mid 内の seq=...csv は文字列順
 *
 * 返すMap:
 * - Key: ローカルファイルパス（OriginStat が Files.deleteIfExists できるように）
 * - Value: DataEntity一覧（DataEntity.file には S3 key をセット）
 */
@Component
public class GetOriginInfo {

	private static final String PROJECT_NAME = GetOriginInfo.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	private static final String CLASS_NAME = GetOriginInfo.class.getName();

	/** LoggerFactory */
	private static final Logger log = LoggerFactory.getLogger(GetOriginInfo.class);

	// 例: 2026-02-05/mid=d2thPpKD/seq=000035_20260205T000138Z.csv
	private static final Pattern OUTPUTS_CSV_KEY = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}/mid=[^/]+/seq=.*\\.csv$");

	@Autowired
	private S3Operator s3Operator;

	@Autowired
	private PathConfig config;

	@Autowired
	private ReadOrigin readOrigin;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	public Map<String, List<DataEntity>> getData(List<MatchKeyItem> items) {
		final String METHOD_NAME = "getData";

		String bucket = config.getS3BucketsOutputs();
		String outputFolder = safeOutputFolder();

		// 1) 全走査して matcher に合うkeyだけ抽出（S3OperatorにlistAllKeysが無いのでここでやる）
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

		// 3) orderedKeys の順を崩さずに読み込む（invokeAllはtasks順を保持）
		Map<String, List<DataEntity>> resultMap = new LinkedHashMap<>();

		int poolSize = Math.min(8, orderedKeys.size());
		ExecutorService executor = Executors.newFixedThreadPool(poolSize);

		try {
			List<Callable<ReadOneResult>> tasks = orderedKeys.stream()
					.map(k -> (Callable<ReadOneResult>) () -> readOne(bucket, k, outputFolder))
					.collect(Collectors.toList());

			List<Future<ReadOneResult>> futures = executor.invokeAll(tasks, 10, TimeUnit.MINUTES);

			for (Future<ReadOneResult> f : futures) {
				if (f.isCancelled()) {
					manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00003E_EXECUTION_SKIP,
							null, "ReadOriginS3 timeout/cancel");
					continue;
				}

				ReadOneResult r = f.get();

				if (!r.ok) {
					manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00003E_EXECUTION_SKIP,
							null, "ReadOriginS3 failed: " + r.s3Key);
					continue;
				}

				if (r.entities == null || r.entities.isEmpty()) {
					manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP,
							"dataList is empty: " + r.s3Key);
					continue;
				}

				// ★Mapキーはローカルパス（OriginStatが削除できる）
				resultMap.put(r.localPath, r.entities);
			}

		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			manageLoggerComponent.createBusinessException(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00004E_THREAD_INTERRUPTION, null, ie);

		} catch (Exception e) {
			manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN,
					e, "S3 Origin読み込みエラー");
			manageLoggerComponent.createBusinessException(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN, null, e);

		} finally {
			executor.shutdownNow();
		}

		return resultMap;
	}

	// =========================================================
	// S3全走査して matcher に合う key を集める
	// さらに items が指定されている場合は、items内キーを含むものだけに絞る
	// =========================================================
	private List<String> listAllMatchedKeys(String bucket, Pattern matcher, List<MatchKeyItem> items) {

		List<String> keys = new ArrayList<>();

		// matcher に合うものだけは S3Operator 側で拾ってもらう
		List<S3Object> objs = s3Operator.listAllDateCsvObjectsSortedByLastModifiedAsc(bucket, matcher);

		// items が null or 空なら「全取得対象」
		if (items == null || items.isEmpty()) {
			for (S3Object o : objs) {
				keys.add(o.key());
			}
			return keys;
		}

		// items 内の「含まれているキー」を抽出（※ここは MatchKeyItem の実装に合わせて getter を調整）
		List<String> includes = new ArrayList<>();
		for (MatchKeyItem it : items) {
			if (it == null)
				continue;

			// 例：MatchKeyItem に getMatchKey() がある想定
			String k = it.getMatchKey();

			if (k == null)
				continue;
			k = k.trim();
			if (k.isEmpty())
				continue;

			includes.add(k);
		}

		// 抽出結果が空なら全件扱いにする（必要に応じて「0件返す」に変えてもOK）
		if (includes.isEmpty()) {
			for (S3Object o : objs) {
				keys.add(o.key());
			}
			return keys;
		}

		// items のどれかのキーが S3キー文字列に含まれていれば採用
		for (S3Object o : objs) {
			String s3Key = o.key();
			if (s3Key == null || s3Key.isBlank())
				continue;

			boolean hit = false;
			for (String inc : includes) {
				if (s3Key.contains(inc)) {
					hit = true;
					break;
				}
			}

			if (hit) {
				keys.add(s3Key);
			}
		}

		return keys;
	}

	// =========================================================
	// 並び順: date昇順 -> midは出現順維持 -> seqは文字列順
	// =========================================================
	private List<String> orderKeysByDateThenMidEncounterThenSeqString(List<String> keys) {

		// date -> (mid -> list(keys))  ※midはLinkedHashMapで出現順維持
		Map<String, LinkedHashMap<String, List<String>>> grouped = new HashMap<>();

		for (String key : keys) {
			// yyyy-MM-dd/mid=xxx/seq=...
			String[] parts = key.split("/", 3);
			if (parts.length < 3)
				continue;

			String date = parts[0];
			String mid = parts[1];

			grouped.computeIfAbsent(date, d -> new LinkedHashMap<>())
					.computeIfAbsent(mid, m -> new ArrayList<>())
					.add(key);
		}

		// date を昇順
		List<String> dates = new ArrayList<>(grouped.keySet());
		Collections.sort(dates);

		List<String> ordered = new ArrayList<>();

		for (String date : dates) {
			LinkedHashMap<String, List<String>> midMap = grouped.get(date);
			if (midMap == null)
				continue;

			// midは「出現順」を維持するため、midMap.entrySet()の順のまま
			for (Map.Entry<String, List<String>> midEntry : midMap.entrySet()) {
				List<String> seqKeys = midEntry.getValue();
				if (seqKeys == null)
					continue;

				// seq=... を文字列順（key全体で自然順でOK。少なくとも seq 部分はこの順で揃う）
				seqKeys.sort(Comparator.naturalOrder());

				ordered.addAll(seqKeys);
			}
		}

		return ordered;
	}

	// =========================================================
	// 1ファイル処理: ローカルへ保存 -> readOriginでパース -> DataEntity.file に S3 key
	// =========================================================
	private ReadOneResult readOne(String bucket, String s3Key, String outputFolder) {
		try {
			String fileName = Paths.get(s3Key).getFileName().toString();
			Path local = Paths.get(outputFolder, fileName);

			// S3Operatorに既にある
			s3Operator.downloadToFile(bucket, s3Key, local);

			try (InputStream is = java.nio.file.Files.newInputStream(local)) {
				ReadFileOutputDTO dto = readOrigin.getFileBodyFromStream(is, s3Key);

				if (!BookMakersCommonConst.NORMAL_CD.equals(dto.getResultCd())) {
					return ReadOneResult.fail(s3Key, local.toString(), dto.getThrowAble());
				}

				List<DataEntity> list = dto.getDataList();

				// 追跡用にS3 keyを入れる
				if (list != null) {
					for (DataEntity e : list) {
						try {
							e.setFile(s3Key);
						} catch (Exception ignore) {
						}
					}
				}

				return ReadOneResult.ok(s3Key, local.toString(), list);
			}

		} catch (Exception e) {
			return ReadOneResult.fail(s3Key, null, e);
		}
	}

	private String safeOutputFolder() {
		try {
			String p = config.getOutputCsvFolder();
			if (p == null || p.isBlank())
				return "/tmp/outputs/";
			return p;
		} catch (Exception ignore) {
			return "/tmp/outputs/";
		}
	}

	private static class ReadOneResult {
		final boolean ok;
		final String s3Key;
		final String localPath;
		final List<DataEntity> entities;

		private ReadOneResult(boolean ok, String s3Key, String localPath, List<DataEntity> entities, Throwable thrown) {
			this.ok = ok;
			this.s3Key = s3Key;
			this.localPath = localPath;
			this.entities = entities;
		}

		static ReadOneResult ok(String s3Key, String localPath, List<DataEntity> entities) {
			return new ReadOneResult(true, s3Key, localPath, entities, null);
		}

		static ReadOneResult fail(String s3Key, String localPath, Throwable e) {
			return new ReadOneResult(false, s3Key, localPath, null, e);
		}
	}
}
