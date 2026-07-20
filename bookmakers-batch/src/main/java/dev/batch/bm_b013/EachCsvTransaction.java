package dev.batch.bm_b013;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.batch.bm_b011.ReaderCurrentCsvInfoBean;
import dev.batch.repository.bm.CsvDetailManageBatchRepository;
import dev.batch.service.CsvFileNameService;
import dev.batch.service.FileExistsService;
import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.entity.CsvDetailManageEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.upload.RecordFileOperationOutputDTO;
import dev.common.upload.RecordFileOperationService;

/**
 * CSV関係の削除および txt ファイル更新
 *
 * 方針:
 * - 物理CSV削除の成功分だけ txt / DB に反映
 * - 削除前に csvId -> seqList の snapshot を保存
 * - 途中失敗時は failed 分だけ snapshot を残す
 * - data_team_list.txt / seqList.txt の削除内容を詳細ログ出力
 * - 削除対象は country-league 単位の folder prefix（例: 日本-J1）で判定
 */
@Component
@Transactional(rollbackFor = Exception.class)
public class EachCsvTransaction {

	private static final String PROJECT_NAME = EachCsvTransaction.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	private static final String CLASS_NAME = EachCsvTransaction.class.getName();

	private static final String SEASON_FIN_CSV_ZIP_FOLDER = "EachCsvTransaction";

	private static final DateTimeFormatter DELETE_BACKUP_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private static final ObjectMapper JSON = new ObjectMapper();

	/**
	 * 削除途中失敗時に、どの csvId -> seqList を後続再処理すべきか保持する snapshot
	 */
	private static final String SNAPSHOT_FILE_NAME = "season_delete_seq_snapshot.json";

	@Value("${exportcsv.local-only:false}")
	private boolean localOnly;

	@Value("${exportcsv.final-prefix:}")
	private String finalPrefix;

	@Autowired
	private CsvDetailManageBatchRepository csvDetailManageBatchRepository;

	@Autowired
	private PathConfig config;

	@Autowired
	private S3Operator s3Operator;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	@Autowired
	private RecordFileOperationService recordFileOperationService;

	@Autowired
	private CsvFileNameService csvFileNameService;

	@Autowired
	private FileExistsService fileExistsService;

	@Autowired
	private ReaderCurrentCsvInfoBean bean;

	/**
	 * 実行メソッド
	 */
	public void execute(TransactionDTO dto) throws Exception {
		final String METHOD_NAME = "execute";

		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, "start");

		List<String> folderPrefixes = buildCsvFolderPrefixes(dto);
		if (folderPrefixes.isEmpty()) {
			logInfo(METHOD_NAME, "削除対象の csv folder prefix が空のため処理終了");
			endLog(METHOD_NAME);
			return;
		}

		List<String> folderCategories = buildCsvFolderCategories(dto);
		if (folderCategories.isEmpty()) {
			logInfo(METHOD_NAME, "削除対象の folderCategories が空のため処理終了");
			endLog(METHOD_NAME);
			return;
		}

		List<CsvDetailManageEntity> targets = this.csvDetailManageBatchRepository
				.findDeleteTargetsByCsvIdAnCategoryPrefixes(folderPrefixes, folderCategories);

		if (targets == null || targets.isEmpty()) {
			logInfo(METHOD_NAME, "削除対象の csv_detail_manage が存在しません");
			endLog(METHOD_NAME);
			return;
		}

		Set<String> originalCsvIdSet = new LinkedHashSet<>();
		for (CsvDetailManageEntity entity : targets) {
			if (entity == null) {
				continue;
			}
			String csvId = safe(entity.getCsvId()).trim();
			if (!csvId.isEmpty()) {
				originalCsvIdSet.add(csvId);
			}
		}

		if (originalCsvIdSet.isEmpty()) {
			logInfo(METHOD_NAME, "削除対象 csv_id が存在しません");
			endLog(METHOD_NAME);
			return;
		}

		List<String> originalCsvIds = new ArrayList<>(originalCsvIdSet);

		Path baseDir = Paths.get(config.getCsvFolder()).toAbsolutePath().normalize();
		Files.createDirectories(baseDir);

		String bucket = config.getS3BucketsStats();
		String prefix = normalizePrefix(finalPrefix);
		Path snapshotPath = baseDir.resolve(SNAPSHOT_FILE_NAME);

		prepareManageFilesLocalCache(bucket, prefix, METHOD_NAME);

		Map<String, List<Integer>> existingSnapshot = readSnapshot(snapshotPath);
		Map<String, List<Integer>> currentCsvInfo = loadCsvInfoSnapshot();
		Map<String, List<Integer>> csvFileSnapshot = loadSeqSnapshotFromDeleteTargetCsvFiles(originalCsvIds);
		Map<String, List<Integer>> deleteSnapshot = buildDeleteSnapshot(
				originalCsvIds, csvFileSnapshot, currentCsvInfo, existingSnapshot);

		writeSnapshot(snapshotPath, deleteSnapshot);

		String backupZipKey = archiveDeleteTargetCsvFilesToRecordBucket(originalCsvIds);
		logInfo(METHOD_NAME, "削除前CSVバックアップ完了 key=" + backupZipKey);

		DeleteResult deleteResult = deletePhysicalCsvFiles(originalCsvIds);

		if (deleteResult.deletedOriginalCsvIds.isEmpty()) {
			logWarn(METHOD_NAME, "CSV削除成功件数=0 です");
			retainSnapshotForFailed(snapshotPath, deleteSnapshot, deleteResult.failedOriginalCsvIds);
			endLog(METHOD_NAME);
			return;
		}

		if (!deleteResult.deletedLocalRelativePaths.isEmpty()) {
			cleanupEmptyParentFolders(deleteResult.deletedLocalRelativePaths);
		}

		updateDataTeamList(
				deleteResult.deletedOriginalCsvIds,
				deleteResult.deletedCanonicalCsvIds,
				deleteResult.deletedLocalRelativePaths);

		updateSeqList(deleteResult.deletedOriginalCsvIds, deleteSnapshot);

		// 物理削除成功分だけ DB 削除
		int deleted = this.csvDetailManageBatchRepository.deleteByCsvIds(
				new ArrayList<>(deleteResult.deletedOriginalCsvIds));

		logInfo(METHOD_NAME, "csv_detail_manage 削除件数=" + deleted);

		retainSnapshotForFailed(snapshotPath, deleteSnapshot, deleteResult.failedOriginalCsvIds);

		endLog(METHOD_NAME);
	}

	/**
	 * 管理ファイルをローカルへ同期
	 */
	private void prepareManageFilesLocalCache(String bucket, String prefix, String parentMethod) {
		final String METHOD_NAME = "prepareManageFilesLocalCache";

		if (localOnly) {
			logInfo(METHOD_NAME, "localOnly=true のため管理ファイルダウンロードをスキップ");
			return;
		}

		boolean seqDownloaded = fileExistsService.downloadSeqListIfExists(bucket, prefix);
		boolean teamDownloaded = fileExistsService.downloadDataTeamListIfExists(bucket, prefix);

		logInfo(METHOD_NAME, "管理ファイル同期結果 seqDownloaded=" + seqDownloaded
				+ ", teamDownloaded=" + teamDownloaded);
	}

	/**
	 * ReaderCurrentCsvInfoBean から csvId -> seqList を取得
	 */
	private Map<String, List<Integer>> loadCsvInfoSnapshot() {
		final String METHOD_NAME = "loadCsvInfoSnapshot";

		if (localOnly) {
			logInfo(METHOD_NAME, "localOnly=true のため csvInfo snapshot 読込をスキップ");
			return new LinkedHashMap<>();
		}

		try {
			bean.init();
			Map<String, List<Integer>> csvInfo = bean.getCsvInfo();
			if (csvInfo == null) {
				return new LinkedHashMap<>();
			}

			Map<String, List<Integer>> result = new LinkedHashMap<>();
			for (Map.Entry<String, List<Integer>> e : csvInfo.entrySet()) {
				String originalKey = safe(e.getKey()).trim();
				String canonicalKey = canonicalizeCsvId(originalKey);
				List<Integer> seqs = normalizeSeqList(e.getValue());

				if (seqs.isEmpty()) {
					continue;
				}
				if (!originalKey.isEmpty()) {
					result.put(originalKey, seqs);
				}
				if (!canonicalKey.isEmpty()) {
					result.put(canonicalKey, seqs);
				}
			}

			logInfo(METHOD_NAME, "csvInfo snapshot 読込完了 size=" + result.size());
			return result;

		} catch (Exception e) {
			logWarn(METHOD_NAME, "csvInfo snapshot 取得失敗 reason=" + e.getMessage());
			return new LinkedHashMap<>();
		}
	}

	/**
	 * 削除対象 csvId について snapshot を作る
	 * 優先:
	 * 1. 現在の csvInfo
	 * 2. 既存 snapshot
	 */
	private Map<String, List<Integer>> buildDeleteSnapshot(
			List<String> csvIds,
			Map<String, List<Integer>> csvFileSnapshot,
			Map<String, List<Integer>> currentCsvInfo,
			Map<String, List<Integer>> existingSnapshot) {

		Map<String, List<Integer>> result = new LinkedHashMap<>();

		for (String csvId : csvIds) {
			if (csvId == null || csvId.isBlank()) {
				continue;
			}

			Set<String> lookupKeys = buildCsvIdLookupKeys(csvId);

			List<Integer> seqs = findSeqListByAnyKey(csvFileSnapshot, lookupKeys);
			if (seqs == null || seqs.isEmpty()) {
				seqs = findSeqListByAnyKey(currentCsvInfo, lookupKeys);
			}
			if (seqs == null || seqs.isEmpty()) {
				seqs = findSeqListByAnyKey(existingSnapshot, lookupKeys);
			}

			seqs = normalizeSeqList(seqs);
			if (!seqs.isEmpty()) {
				result.put(csvId, seqs);
			}
		}

		return result;
	}

	private List<Integer> findSeqListByAnyKey(
			Map<String, List<Integer>> source,
			Set<String> keys) {

		if (source == null || source.isEmpty() || keys == null || keys.isEmpty()) {
			return null;
		}

		for (String key : keys) {
			List<Integer> seqs = source.get(key);
			if (seqs != null && !seqs.isEmpty()) {
				return seqs;
			}
		}
		return null;
	}

	/**
	 * 削除対象CSV実体から seq を読むメソッド
	 * @param csvIds
	 * @return
	 */
	private Map<String, List<Integer>> loadSeqSnapshotFromDeleteTargetCsvFiles(List<String> csvIds) {
		final String METHOD_NAME = "loadSeqSnapshotFromDeleteTargetCsvFiles";

		Map<String, List<Integer>> result = new LinkedHashMap<>();
		Path baseDir = Paths.get(config.getCsvFolder()).toAbsolutePath().normalize();
		String bucket = config.getS3BucketsStats();
		String prefix = normalizePrefix(finalPrefix);

		for (String csvId : csvIds) {
			if (csvId == null || csvId.isBlank()) {
				continue;
			}

			String physicalCsvId = this.csvFileNameService.toPhysicalCsvId(csvId);
			ResolvedCsvSource resolved = null;

			try {
				resolved = resolveCsvSourceForSeqRead(baseDir, bucket, prefix, csvId, physicalCsvId);

				if (resolved == null || resolved.path == null || !Files.exists(resolved.path)) {
					logWarn(METHOD_NAME, "削除対象CSVが存在しないため seq 読込スキップ csvId="
							+ csvId + ", physicalCsvId=" + physicalCsvId);
					continue;
				}

				List<Integer> seqs = extractSeqListFromCsv(resolved.path);
				seqs = normalizeSeqList(seqs);

				if (!seqs.isEmpty()) {
					result.put(csvId, seqs);
					logInfo(METHOD_NAME, "CSVから seq 読込完了 csvId=" + csvId
							+ ", physicalCsvId=" + physicalCsvId
							+ ", source=" + resolved.sourceType
							+ ", path=" + resolved.path
							+ ", seqList=" + seqs
							+ ", groupKey=" + groupKey(seqs));
				} else {
					logWarn(METHOD_NAME, "CSVから seq を取得できませんでした csvId=" + csvId
							+ ", physicalCsvId=" + physicalCsvId
							+ ", source=" + resolved.sourceType
							+ ", path=" + resolved.path);
				}

			} catch (Exception e) {
				logWarn(METHOD_NAME, "CSVから seq 読込失敗 csvId=" + csvId
						+ ", physicalCsvId=" + physicalCsvId
						+ ", reason=" + e.getMessage());
			} finally {
				if (resolved != null && resolved.temporary && resolved.path != null) {
					try {
						Files.deleteIfExists(resolved.path);
					} catch (IOException ignore) {
					}
				}
			}
		}

		return result;
	}

	private ResolvedCsvSource resolveCsvSourceForSeqRead(
			Path baseDir,
			String bucket,
			String prefix,
			String csvId,
			String physicalCsvId) throws IOException {

		final String METHOD_NAME = "resolveCsvSourceForSeqRead";

		// 1. ローカル(physicalCsvId)
		Path localPhysicalPath = baseDir.resolve(physicalCsvId).normalize();
		if (Files.exists(localPhysicalPath) && Files.isRegularFile(localPhysicalPath)) {
			logInfo(METHOD_NAME, "ローカルCSVを使用(physicalCsvId) csvId=" + csvId
					+ ", physicalCsvId=" + physicalCsvId
					+ ", path=" + localPhysicalPath);
			return new ResolvedCsvSource(localPhysicalPath, false, "local-physical");
		}

		// 2. ローカル(csvId)
		Path localCsvIdPath = baseDir.resolve(csvId).normalize();
		if (Files.exists(localCsvIdPath) && Files.isRegularFile(localCsvIdPath)) {
			logInfo(METHOD_NAME, "ローカルCSVを使用(csvId) csvId=" + csvId
					+ ", physicalCsvId=" + physicalCsvId
					+ ", path=" + localCsvIdPath);
			return new ResolvedCsvSource(localCsvIdPath, false, "local-csvId");
		}

		if (localOnly) {
			logInfo(METHOD_NAME, "localOnly=true のため S3 読込スキップ csvId=" + csvId
					+ ", physicalCsvId=" + physicalCsvId);
			return null;
		}

		// 3. S3 実在キー解決
		String resolvedS3Key = findExistingS3CsvKey(bucket, prefix, csvId, physicalCsvId);
		if (resolvedS3Key == null || resolvedS3Key.isBlank()) {
			logWarn(METHOD_NAME, "ローカル/S3 いずれにもCSVが見つかりません csvId=" + csvId
					+ ", physicalCsvId=" + physicalCsvId
					+ ", bucket=" + bucket);
			return null;
		}

		Path downloaded = downloadCsvFromS3ToTemp(bucket, resolvedS3Key, physicalCsvId);
		if (downloaded != null && Files.exists(downloaded) && Files.isRegularFile(downloaded)) {
			logInfo(METHOD_NAME, "S3 CSVを一時取得 csvId=" + csvId
					+ ", physicalCsvId=" + physicalCsvId
					+ ", bucket=" + bucket
					+ ", key=" + resolvedS3Key
					+ ", tempPath=" + downloaded);
			return new ResolvedCsvSource(downloaded, true, "s3");
		}

		logWarn(METHOD_NAME, "S3実在キー解決後もCSV取得失敗 csvId=" + csvId
				+ ", physicalCsvId=" + physicalCsvId
				+ ", bucket=" + bucket
				+ ", key=" + resolvedS3Key);

		return null;
	}

	/**
	 * snapshot 読込
	 */
	private Map<String, List<Integer>> readSnapshot(Path snapshotPath) {
		final String METHOD_NAME = "readSnapshot";

		try {
			if (snapshotPath == null || !Files.exists(snapshotPath)) {
				return new LinkedHashMap<>();
			}

			String json = Files.readString(snapshotPath, StandardCharsets.UTF_8).trim();
			if (json.isEmpty()) {
				return new LinkedHashMap<>();
			}

			Map<String, List<Integer>> map = JSON.readValue(
					json,
					new TypeReference<LinkedHashMap<String, List<Integer>>>() {
					});

			Map<String, List<Integer>> normalized = new LinkedHashMap<>();
			for (Map.Entry<String, List<Integer>> e : map.entrySet()) {
				String originalKey = safe(e.getKey()).trim();
				String canonicalKey = canonicalizeCsvId(originalKey);
				List<Integer> seqs = normalizeSeqList(e.getValue());

				if (seqs.isEmpty()) {
					continue;
				}
				if (!originalKey.isEmpty()) {
					normalized.put(originalKey, seqs);
				}
				if (!canonicalKey.isEmpty()) {
					normalized.put(canonicalKey, seqs);
				}
			}

			return normalized;

		} catch (Exception e) {
			logWarn(METHOD_NAME, "snapshot読込失敗 path=" + snapshotPath
					+ ", reason=" + e.getMessage());
			return new LinkedHashMap<>();
		}
	}

	private String canonicalizeCsvId(String rawCsvId) {
		if (rawCsvId == null) {
			return "";
		}

		String value = Normalizer.normalize(rawCsvId, Normalizer.Form.NFKC)
				.trim()
				.replace('\\', '/');

		while (value.startsWith("/")) {
			value = value.substring(1);
		}
		value = value.replaceAll("/+", "/");

		if (value.isEmpty()) {
			return "";
		}

		String[] parts = value.split("/");
		List<String> normalizedParts = new ArrayList<>();

		for (int i = 0; i < parts.length; i++) {
			String part = safe(parts[i]).trim();
			if (part.isEmpty()) {
				continue;
			}

			if (i < parts.length - 1) {
				part = part.replaceAll("\\s*:\\s*", "-");
				part = part.replaceAll("\\s*-\\s*", "-");
				part = part.replaceAll("-{2,}", "-");
			}

			normalizedParts.add(part);
		}

		return String.join("/", normalizedParts);
	}

	private String canonicalizeFolderPrefix(String value) {
		String s = Normalizer.normalize(safe(value), Normalizer.Form.NFKC).trim();
		if (s.isEmpty()) {
			return "";
		}
		s = s.replaceAll("\\s*:\\s*", "-");
		s = s.replaceAll("\\s*-\\s*", "-");
		s = s.replaceAll("-{2,}", "-");
		return s;
	}

	private String toLegacyFolderPrefix(String canonical) {
		if (canonical == null || canonical.isBlank()) {
			return "";
		}
		int idx = canonical.indexOf('-');
		if (idx < 0) {
			return canonical;
		}
		String country = canonical.substring(0, idx).trim();
		String league = canonical.substring(idx + 1).trim();
		if (country.isEmpty() || league.isEmpty()) {
			return canonical;
		}
		return country + ": " + league;
	}

	private Set<String> buildCsvIdLookupKeys(String originalCsvId) {
		Set<String> keys = new LinkedHashSet<>();

		String original = safe(originalCsvId).trim();
		String canonical = canonicalizeCsvId(original);

		if (!original.isEmpty()) {
			keys.add(original);
		}
		if (!canonical.isEmpty()) {
			keys.add(canonical);
		}

		String physicalFromOriginal = this.csvFileNameService.toPhysicalCsvId(original);
		String physicalFromCanonical = this.csvFileNameService.toPhysicalCsvId(canonical);

		if (physicalFromOriginal != null && !physicalFromOriginal.isBlank()) {
			keys.add(physicalFromOriginal.trim());
			keys.add(canonicalizeCsvId(physicalFromOriginal.trim()));
		}
		if (physicalFromCanonical != null && !physicalFromCanonical.isBlank()) {
			keys.add(physicalFromCanonical.trim());
			keys.add(canonicalizeCsvId(physicalFromCanonical.trim()));
		}

		return keys;
	}

	private Set<String> buildPhysicalCsvIdCandidates(String originalCsvId) {
		Set<String> keys = new LinkedHashSet<>();

		String original = safe(originalCsvId).trim();
		String canonical = canonicalizeCsvId(original);

		String p1 = this.csvFileNameService.toPhysicalCsvId(original);
		String p2 = this.csvFileNameService.toPhysicalCsvId(canonical);

		if (p1 != null && !p1.isBlank()) {
			keys.add(p1.trim());
			keys.add(canonicalizeCsvId(p1.trim()));
		}
		if (p2 != null && !p2.isBlank()) {
			keys.add(p2.trim());
			keys.add(canonicalizeCsvId(p2.trim()));
		}

		if (!canonical.isBlank()) {
			keys.add(canonical);
		}
		if (!original.isBlank()) {
			keys.add(original);
		}

		return keys;
	}

	private String findExistingS3CsvKey(
			String bucket,
			String prefix,
			String csvId,
			String physicalCsvId) {

		final String METHOD_NAME = "findExistingS3CsvKey";

		try {
			String keyByCsvId = normalizeS3Key(joinS3Key(prefix, safe(csvId).trim()));
			String keyByPhysicalCsvId = normalizeS3Key(joinS3Key(prefix, safe(physicalCsvId).trim()));

			String parentPrefix = extractParentPrefix(!keyByCsvId.isBlank() ? keyByCsvId : keyByPhysicalCsvId);
			if (parentPrefix.isBlank()) {
				logWarn(METHOD_NAME, "親prefixを解決できません csvId=" + csvId
						+ ", physicalCsvId=" + physicalCsvId);
				return null;
			}

			List<String> keys = s3Operator.listKeys(bucket, parentPrefix);
			if (keys == null || keys.isEmpty()) {
				logWarn(METHOD_NAME, "prefix配下にオブジェクトが存在しません bucket=" + bucket
						+ ", parentPrefix=" + parentPrefix
						+ ", csvId=" + csvId
						+ ", physicalCsvId=" + physicalCsvId);
				return null;
			}

			// 1) 完全一致優先
			for (String key : keys) {
				if (safe(key).equals(keyByCsvId) || safe(key).equals(keyByPhysicalCsvId)) {
					logInfo(METHOD_NAME, "S3キー完全一致で解決 bucket=" + bucket + ", key=" + key);
					return key;
				}
			}

			// 2) 正規化一致
			String normalizedCsvId = normalizeKeyForCompare(keyByCsvId);
			String normalizedPhysical = normalizeKeyForCompare(keyByPhysicalCsvId);

			for (String key : keys) {
				String normalizedKey = normalizeKeyForCompare(key);
				if (normalizedKey.equals(normalizedCsvId) || normalizedKey.equals(normalizedPhysical)) {
					logInfo(METHOD_NAME, "S3キー正規化一致で解決 bucket=" + bucket + ", key=" + key);
					return key;
				}
			}

			// 3) ファイル名一致 (例: 1.csv)
			String targetFileName = extractFileName(physicalCsvId);
			List<String> matchedByFileName = keys.stream()
					.filter(k -> extractFileName(k).equals(targetFileName))
					.collect(Collectors.toList());

			if (matchedByFileName.size() == 1) {
				logInfo(METHOD_NAME, "S3キーをファイル名一致で解決 bucket=" + bucket
						+ ", key=" + matchedByFileName.get(0));
				return matchedByFileName.get(0);
			}

			// 4) CSV が1件だけならそれを採用
			List<String> csvKeys = keys.stream()
					.filter(k -> safe(k).toLowerCase().endsWith(".csv"))
					.collect(Collectors.toList());

			if (csvKeys.size() == 1) {
				logInfo(METHOD_NAME, "prefix配下CSV単一件で解決 bucket=" + bucket
						+ ", key=" + csvKeys.get(0));
				return csvKeys.get(0);
			}

			logWarn(METHOD_NAME, "S3キーを解決できません bucket=" + bucket
					+ ", parentPrefix=" + parentPrefix
					+ ", csvId=" + csvId
					+ ", physicalCsvId=" + physicalCsvId
					+ ", keys=" + keys);

			return null;

		} catch (Exception e) {
			logWarn(METHOD_NAME, "S3キー解決失敗 csvId=" + csvId
					+ ", physicalCsvId=" + physicalCsvId
					+ ", reason=" + e.getMessage());
			return null;
		}
	}

	private String extractParentPrefix(String key) {
		String k = safe(key).trim().replace("\\", "/");
		int idx = k.lastIndexOf('/');
		if (idx < 0) {
			return "";
		}
		return k.substring(0, idx + 1);
	}

	private String extractFileName(String key) {
		String k = safe(key).trim().replace("\\", "/");
		int idx = k.lastIndexOf('/');
		if (idx < 0) {
			return k;
		}
		return k.substring(idx + 1);
	}

	private String normalizeKeyForCompare(String value) {
		String s = safe(value).trim().replace("\\", "/");
		s = Normalizer.normalize(s, Normalizer.Form.NFKC);
		s = s.replaceAll("\\s+", " ");
		return s;
	}

	/**
	 * 削除対象CSVをZIP化して record バケットへバックアップする
	 * - RecordFileOperationService#uploadCsvFilesAsZip を使用
	 * - ローカルCSVを優先し、無ければ S3 から一時取得
	 * @param csvIds 削除対象 csvId 一覧
	 * @return S3キー
	 * @throws Exception
	 */
	private String archiveDeleteTargetCsvFilesToRecordBucket(List<String> csvIds) throws Exception {
		final String METHOD_NAME = "archiveDeleteTargetCsvFilesToRecordBucket";

		if (csvIds == null || csvIds.isEmpty()) {
			throw new IllegalArgumentException("バックアップ対象の csvIds が空です。");
		}

		Path baseDir = Paths.get(config.getCsvFolder()).toAbsolutePath().normalize();
		Files.createDirectories(baseDir);

		String bucket = config.getS3BucketsStats();
		String prefix = normalizePrefix(finalPrefix);

		Path workDir = baseDir.resolve(".tmp_delete_backup_zip");
		Files.createDirectories(workDir);

		String zipFileName = "season_delete_backup_"
				+ LocalDateTime.now().format(DELETE_BACKUP_TS_FORMAT)
				+ ".zip";

		String recordKey = joinS3Key(SEASON_FIN_CSV_ZIP_FOLDER, zipFileName);

		List<Path> backupTargetCsvFiles = new ArrayList<>();
		List<Path> tempDownloadedFiles = new ArrayList<>();

		for (String csvId : csvIds) {
			if (csvId == null || csvId.isBlank()) {
				continue;
			}

			String physicalCsvId = this.csvFileNameService.toPhysicalCsvId(csvId);
			ResolvedCsvSource resolved = null;

			try {
				resolved = resolveCsvSourceForSeqRead(baseDir, bucket, prefix, csvId, physicalCsvId);

				if (resolved == null || resolved.path == null || !Files.exists(resolved.path)
						|| !Files.isRegularFile(resolved.path)) {
					logWarn(METHOD_NAME,
							"バックアップ対象CSVを取得できないためスキップ csvId=" + csvId
							+ ", physicalCsvId=" + physicalCsvId);
					continue;
				}

				backupTargetCsvFiles.add(resolved.path);

				logInfo(METHOD_NAME,
						"バックアップ対象追加 csvId=" + csvId
						+ ", physicalCsvId=" + physicalCsvId
						+ ", source=" + resolved.sourceType
						+ ", path=" + resolved.path);

			} catch (Exception e) {
				logWarn(METHOD_NAME,
						"バックアップ対象追加失敗 csvId=" + csvId
						+ ", physicalCsvId=" + physicalCsvId
						+ ", reason=" + e.getMessage());
			} finally {
				if (resolved != null && resolved.temporary && resolved.path != null) {
					tempDownloadedFiles.add(resolved.path);
				}
			}
		}

		if (backupTargetCsvFiles.isEmpty()) {
			throw new IOException("削除前バックアップ対象CSVを1件も取得できませんでした。");
		}

		RecordFileOperationOutputDTO uploadResult =
				recordFileOperationService.uploadCsvFilesAsZip(
						SEASON_FIN_CSV_ZIP_FOLDER,
						zipFileName,
						backupTargetCsvFiles,
						workDir);

		if (uploadResult == null || uploadResult.getInfoCd() != 0) {
			throw new IOException("削除前CSVバックアップZIPアップロードに失敗しました。");
		}

		logInfo(METHOD_NAME,
				"削除前CSVバックアップZIPアップロード成功 recordBucket=" + config.getS3Record()
				+ ", key=" + recordKey
				+ ", zippedCount=" + backupTargetCsvFiles.size());

		for (Path tempFile : tempDownloadedFiles) {
			try {
				Files.deleteIfExists(tempFile);
			} catch (IOException e) {
				logWarn(METHOD_NAME,
						"一時CSV削除失敗 path=" + tempFile + ", reason=" + e.getMessage());
			}
		}

		return recordKey;
	}

	private List<Integer> extractSeqListFromCsv(Path csvPath) throws IOException {
		List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
		List<Integer> result = new ArrayList<>();

		if (lines == null || lines.isEmpty()) {
			return result;
		}

		int seqColumnIndex = -1;
		boolean firstDataChecked = false;

		for (String line : lines) {
			if (line == null || line.isBlank()) {
				continue;
			}

			List<String> columns = parseSimpleCsvLine(line);
			if (columns.isEmpty()) {
				continue;
			}

			// 1行目だけ BOM 除去
			if (!firstDataChecked && !columns.isEmpty()) {
				columns.set(0, removeBom(columns.get(0)));
			}

			if (!firstDataChecked) {
				firstDataChecked = true;

				// ヘッダ行から seq 列を探す
				seqColumnIndex = findSeqColumnIndex(columns);

				// ヘッダ行だった場合は次の行へ
				if (seqColumnIndex >= 0) {
					continue;
				}

				// ヘッダでなければ 1列目を seq とみなす
				seqColumnIndex = 0;
			}

			if (seqColumnIndex < 0 || seqColumnIndex >= columns.size()) {
				continue;
			}

			String raw = stripQuotes(columns.get(seqColumnIndex)).trim();
			if (raw.isEmpty()) {
				continue;
			}

			try {
				result.add(Integer.valueOf(raw));
			} catch (NumberFormatException ignore) {
				// seq列に数値以外が入っている行は無視
			}
		}

		return normalizeSeqList(result);
	}

	private int findSeqColumnIndex(List<String> columns) {
		for (int i = 0; i < columns.size(); i++) {
			String name = stripQuotes(safe(columns.get(i))).trim();
			name = removeBom(name);

			if ("seq".equalsIgnoreCase(name) || "id".equalsIgnoreCase(name)) {
				return i;
			}
		}
		return -1;
	}

	private List<String> parseSimpleCsvLine(String line) {
		List<String> result = new ArrayList<>();
		if (line == null) {
			return result;
		}

		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;

		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);

			if (c == '"') {
				// 連続する "" はエスケープされたダブルクォートとして扱う
				if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					current.append('"');
					i++;
					continue;
				}
				inQuotes = !inQuotes;
				continue;
			}

			if (c == ',' && !inQuotes) {
				result.add(current.toString());
				current.setLength(0);
				continue;
			}

			current.append(c);
		}

		result.add(current.toString());
		return result;
	}

	private String stripQuotes(String value) {
		String s = safe(value).trim();
		if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
			return s.substring(1, s.length() - 1);
		}
		return s;
	}

	private String removeBom(String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}
		if (value.charAt(0) == '\uFEFF') {
			return value.substring(1);
		}
		return value;
	}

	/**
	 * snapshot 保存
	 */
	private void writeSnapshot(
			Path snapshotPath,
			Map<String, List<Integer>> snapshot) throws IOException {

		final String METHOD_NAME = "writeSnapshot";

		if (snapshotPath.getParent() != null) {
			Files.createDirectories(snapshotPath.getParent());
		}

		String json = JSON.writeValueAsString(snapshot == null ? new LinkedHashMap<>() : snapshot);

		Files.writeString(
				snapshotPath,
				json,
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);

		logInfo(METHOD_NAME, "snapshot保存完了 path=" + snapshotPath
				+ ", size=" + (snapshot == null ? 0 : snapshot.size()));
	}

	/**
	 * failed 分だけ snapshot を残す
	 */
	private void retainSnapshotForFailed(
			Path snapshotPath,
			Map<String, List<Integer>> allSnapshot,
			Set<String> failedCsvIds) throws IOException {

		final String METHOD_NAME = "retainSnapshotForFailed";

		Map<String, List<Integer>> remain = new LinkedHashMap<>();

		if (failedCsvIds != null && !failedCsvIds.isEmpty()) {
			for (String csvId : failedCsvIds) {
				List<Integer> seqs = normalizeSeqList(allSnapshot.get(csvId));
				if (!seqs.isEmpty()) {
					remain.put(csvId, seqs);
				}
			}
		}

		if (remain.isEmpty()) {
			Files.deleteIfExists(snapshotPath);
			logInfo(METHOD_NAME, "snapshot削除完了 path=" + snapshotPath);
			return;
		}

		writeSnapshot(snapshotPath, remain);

		for (Map.Entry<String, List<Integer>> e : remain.entrySet()) {
			logWarn(METHOD_NAME, "snapshot残置 csvId=" + e.getKey()
					+ ", seqList=" + e.getValue()
					+ ", groupKey=" + groupKey(e.getValue()));
		}
	}

	private Path downloadCsvFromS3ToTemp(String bucket, String s3Key, String physicalCsvId) throws IOException {
		final String METHOD_NAME = "downloadCsvFromS3ToTemp";

		Path baseDir = Paths.get(config.getCsvFolder()).toAbsolutePath().normalize();
		Path tempDir = baseDir.resolve(".tmp_delete_seq");
		Files.createDirectories(tempDir);

		String safeFileName = physicalCsvId
				.replace("\\", "_")
				.replace("/", "_")
				.replace(":", "_");

		Path tempPath = tempDir.resolve(safeFileName);

		try {
			s3Operator.downloadToFile(bucket, s3Key, tempPath);

			if (Files.exists(tempPath) && Files.isRegularFile(tempPath)) {
				return tempPath;
			}

			logWarn(METHOD_NAME, "S3ダウンロード後もファイル未作成 bucket=" + bucket
					+ ", key=" + s3Key
					+ ", tempPath=" + tempPath);
			return null;

		} catch (Exception e) {
			logWarn(METHOD_NAME, "S3 CSV取得失敗 bucket=" + bucket
					+ ", key=" + s3Key
					+ ", reason=" + e.getMessage());
			return null;
		}
	}

	/**
	 * DTO の countryLeague から csv_id 用 folder prefix を作成
	 * 例: 日本-J1リーグ -> 日本-J1リーグ
	 */
	private List<String> buildCsvFolderPrefixes(TransactionDTO dto) {
		Set<String> prefixes = new LinkedHashSet<>();

		if (dto == null || dto.getCountryLeague() == null) {
			return new ArrayList<>();
		}

		for (String value : dto.getCountryLeague()) {
			String canonical = canonicalizeFolderPrefix(value);
			if (!canonical.isBlank()) {
				prefixes.add(canonical);

				// 旧コロン形式も後方互換で削除対象に含める
				String legacy = toLegacyFolderPrefix(canonical);
				if (!legacy.isBlank()) {
					prefixes.add(legacy);
				}
			}
		}

		return new ArrayList<>(prefixes);
	}

	/**
	 * DTO の countryLeague から data_category 用 folder prefix を作成
	 * 例: 日本-J1リーグ -> 日本: J1リーグ
	 */
	private List<String> buildCsvFolderCategories(TransactionDTO dto) {
		Set<String> prefixes = new LinkedHashSet<>();

		if (dto == null || dto.getCountryLeague() == null) {
			return new ArrayList<>();
		}

		for (String value : dto.getCountryLeague()) {
			String canonical = canonicalizeFolderPrefix(value);
			if (canonical.isBlank()) {
				continue;
			}

			int separatorIndex = canonical.indexOf('-');
			if (separatorIndex >= 0) {
				String country = canonical.substring(0, separatorIndex).trim();
				String league = canonical.substring(separatorIndex + 1).trim();
				if (!country.isEmpty() && !league.isEmpty()) {
					prefixes.add(country + ": " + league);
				}
			}
		}

		return new ArrayList<>(prefixes);
	}

	/**
	 * CSV実体削除
	 * - countryLeagueMap の country / league から folder prefix を生成
	 * - その prefix に一致する csvId のみ削除
	 * - 成功/失敗を分離して返す
	 * - 途中で throw しない
	 * @throws IOException
	 */
	private DeleteResult deletePhysicalCsvFiles(List<String> csvIds) throws IOException {
		final String METHOD_NAME = "deletePhysicalCsvFiles";

		Path baseDir = Paths.get(config.getCsvFolder()).toAbsolutePath().normalize();
		String bucket = config.getS3BucketsStats();
		String prefix = normalizePrefix(finalPrefix);

		DeleteResult result = new DeleteResult();

		for (String originalCsvId : csvIds) {
			if (originalCsvId == null || originalCsvId.isBlank()) {
				continue;
			}

			String canonicalCsvId = canonicalizeCsvId(originalCsvId);
			boolean s3DeleteOk = false;
			try {
				Set<String> localCandidates = buildPhysicalCsvIdCandidates(originalCsvId);

				for (String relative : localCandidates) {
					Path localPath = baseDir.resolve(relative).normalize();
					boolean deletedLocal = Files.deleteIfExists(localPath);
					if (deletedLocal) {
						result.deletedLocalRelativePaths.add(relative);
						logInfo(METHOD_NAME, "ローカルCSV削除 relative=" + relative + ", path=" + localPath);
					}
				}

				if (!localOnly) {
					String resolvedS3Key = findExistingS3CsvKey(bucket, prefix, originalCsvId, canonicalCsvId);
					if (resolvedS3Key != null && !resolvedS3Key.isBlank()) {
						s3Operator.delete(bucket, resolvedS3Key);
						s3DeleteOk = true;
						logInfo(METHOD_NAME, "S3 CSV削除完了 key=" + resolvedS3Key);
					} else {
						logWarn(METHOD_NAME, "S3キー未解決のため削除不可 csvId=" + originalCsvId);
					}
				} else {
					s3DeleteOk = true;
				}

				if (s3DeleteOk) {
					result.deletedOriginalCsvIds.add(originalCsvId);
					result.deletedCanonicalCsvIds.add(canonicalCsvId);
				} else {
					result.failedOriginalCsvIds.add(originalCsvId);
				}

			} catch (Exception e) {
				result.failedOriginalCsvIds.add(originalCsvId);

				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
						"CSV削除失敗 csvId=" + originalCsvId
								+ ", canonicalCsvId=" + canonicalCsvId);
			}
		}

		return result;
	}

	/**
	 * 削除成功した CSV の親フォルダが空なら削除
	 */
	private void cleanupEmptyParentFolders(Set<String> deletedPhysicalCsvIds) {
		final String METHOD_NAME = "cleanupEmptyParentFolders";

		Path baseDir = Paths.get(config.getCsvFolder()).toAbsolutePath().normalize();
		Set<Path> parentDirs = new LinkedHashSet<>();

		for (String physicalCsvId : deletedPhysicalCsvIds) {
			if (physicalCsvId == null || physicalCsvId.isBlank()) {
				continue;
			}

			Path parent = baseDir.resolve(physicalCsvId).normalize().getParent();
			if (parent != null) {
				parentDirs.add(parent);
			}
		}

		for (Path dir : parentDirs) {
			try {
				if (!Files.exists(dir) || !Files.isDirectory(dir)) {
					logInfo(METHOD_NAME, "削除対象フォルダ不存在 path=" + dir);
					continue;
				}

				boolean empty;
				try (var stream = Files.list(dir)) {
					empty = stream.findAny().isEmpty();
				}

				if (!empty) {
					logInfo(METHOD_NAME, "フォルダにファイルが残っているため削除スキップ path=" + dir);
					continue;
				}

				boolean deleted = Files.deleteIfExists(dir);
				logInfo(METHOD_NAME, "空フォルダ削除 path=" + dir + ", deleted=" + deleted);

			} catch (Exception e) {
				logWarn(METHOD_NAME, "空フォルダ削除失敗 path=" + dir + ", reason=" + e.getMessage());
			}
		}
	}

	/**
	 * data_team_list.txt から対象 csv_id を削除
	 * 削除した行をログ出力する
	 */
	private void updateDataTeamList(
			Set<String> deletedOriginalCsvIds,
			Set<String> deletedCanonicalCsvIds,
			Set<String> deletedLocalRelativePaths) throws IOException {

		final String METHOD_NAME = "updateDataTeamList";

		Path baseDir = Paths.get(config.getCsvFolder()).toAbsolutePath().normalize();
		Path localTeamPath = baseDir.resolve(FileExistsService.TEAM_FILE_NAME);

		String bucket = config.getS3BucketsStats();
		String prefix = normalizePrefix(finalPrefix);

		if (!localOnly) {
			fileExistsService.downloadDataTeamListIfExists(bucket, prefix);
		}

		if (!Files.exists(localTeamPath)) {
			logInfo(METHOD_NAME, "data_team_list.txt が存在しないため更新スキップ");
			return;
		}

		Set<String> deleteKeys = new LinkedHashSet<>();
		if (deletedOriginalCsvIds != null) {
			deleteKeys.addAll(deletedOriginalCsvIds);
		}
		if (deletedCanonicalCsvIds != null) {
			deleteKeys.addAll(deletedCanonicalCsvIds);
		}
		if (deletedLocalRelativePaths != null) {
			deleteKeys.addAll(deletedLocalRelativePaths);
		}

		Set<String> normalizedDeleteKeys = deleteKeys.stream()
				.map(this::canonicalizeCsvId)
				.collect(Collectors.toCollection(LinkedHashSet::new));

		List<String> lines = Files.readAllLines(localTeamPath, StandardCharsets.UTF_8);
		List<String> newLines = new ArrayList<>();

		for (String line : lines) {
			if (line == null || line.isBlank()) {
				continue;
			}

			String[] parts = line.split("\t", 2);
			String csvKey = safe(parts[0]).trim();
			String normalizedCsvKey = canonicalizeCsvId(csvKey);

			if (normalizedDeleteKeys.contains(normalizedCsvKey)) {
				logInfo(METHOD_NAME, "data_team_list 削除 csvId=" + csvKey + ", line=" + line);
				continue;
			}

			newLines.add(line);
		}

		if (newLines.isEmpty()) {
			boolean deletedLocal = Files.deleteIfExists(localTeamPath);
			logInfo(METHOD_NAME, "data_team_list.txt 削除完了. deleted=" + deletedLocal);

			if (!localOnly) {
				String s3Key = normalizeS3Key(joinS3Key(prefix, FileExistsService.TEAM_FILE_NAME));
				s3Operator.delete(bucket, s3Key);
			}
			return;
		}

		Files.write(
				localTeamPath,
				newLines,
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);

		if (!localOnly) {
			fileExistsService.uploadDataTeamListIfExists(bucket, prefix);
		}
	}

	/**
	 * seqList.txt から対象 seqGroup を削除
	 * 削除内容を詳細ログ出力する
	 */
	private void updateSeqList(
			Set<String> deletedCsvIds,
			Map<String, List<Integer>> deleteSnapshot) throws IOException {

		final String METHOD_NAME = "updateSeqList";

		Path baseDir = Paths.get(config.getCsvFolder()).toAbsolutePath().normalize();
		Path localSeqPath = baseDir.resolve(FileExistsService.SEQ_FILE_NAME);

		String bucket = config.getS3BucketsStats();
		String prefix = normalizePrefix(finalPrefix);

		if (!localOnly) {
			fileExistsService.downloadSeqListIfExists(bucket, prefix);
		}

		if (!Files.exists(localSeqPath)) {
			logInfo(METHOD_NAME, "seqList.txt が存在しないため更新スキップ");
			return;
		}

		List<List<Integer>> groups = readSeqListJson(localSeqPath);

		Map<String, List<Integer>> deleteGroupMap = new LinkedHashMap<>();
		for (String csvId : deletedCsvIds) {
			List<Integer> seqs = normalizeSeqList(deleteSnapshot.get(csvId));
			if (seqs.isEmpty()) {
				logWarn(METHOD_NAME, "snapshot に seqList が無いため除去スキップ csvId=" + csvId);
				continue;
			}

			String groupKey = groupKey(seqs);
			deleteGroupMap.put(csvId, seqs);

			logInfo(METHOD_NAME, "seqList 削除対象 csvId=" + csvId
					+ ", seqList=" + seqs
					+ ", groupKey=" + groupKey);
		}

		List<List<Integer>> newGroups = new ArrayList<>();
		Set<String> removedGroupKeys = new LinkedHashSet<>();
		int removed = 0;

		for (List<Integer> group : groups) {
			List<Integer> normalized = normalizeSeqList(group);
			String currentGroupKey = groupKey(normalized);

			String matchedCsvId = findMatchedCsvId(deleteGroupMap, normalized);
			if (matchedCsvId != null) {
				removed++;
				removedGroupKeys.add(currentGroupKey);

				logInfo(METHOD_NAME, "seqList 削除 csvId=" + matchedCsvId
						+ ", seqList=" + normalized
						+ ", groupKey=" + currentGroupKey);
				continue;
			}

			if (!normalized.isEmpty()) {
				newGroups.add(normalized);
			}
		}

		// 更新後に [] しか残らない = 実質 0件 ならファイル自体を削除
		if (newGroups.isEmpty()) {
			boolean deletedLocal = Files.deleteIfExists(localSeqPath);
			logInfo(METHOD_NAME, "seqList.txt 削除完了. path=" + localSeqPath
					+ ", deleted=" + deletedLocal
					+ ", removed=" + removed
					+ ", remaining=0");

			for (Map.Entry<String, List<Integer>> e : deleteGroupMap.entrySet()) {
				String csvId = e.getKey();
				String gk = groupKey(e.getValue());
				if (!removedGroupKeys.contains(gk)) {
					logWarn(METHOD_NAME, "seqList 未検出 csvId=" + csvId
							+ ", seqList=" + e.getValue()
							+ ", groupKey=" + gk);
				}
			}

			if (!localOnly) {
				String s3Key = normalizeS3Key(joinS3Key(prefix, FileExistsService.SEQ_FILE_NAME));
				s3Operator.delete(bucket, s3Key);
				logInfo(METHOD_NAME, "seqList.txt S3削除完了 bucket=" + bucket + ", key=" + s3Key);
			}

			return;
		}

		Files.writeString(
				localSeqPath,
				JSON.writeValueAsString(newGroups),
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);

		logInfo(METHOD_NAME, "seqList.txt 更新完了. path=" + localSeqPath
				+ ", removed=" + removed
				+ ", remaining=" + newGroups.size());

		for (Map.Entry<String, List<Integer>> e : deleteGroupMap.entrySet()) {
			String csvId = e.getKey();
			String gk = groupKey(e.getValue());
			if (!removedGroupKeys.contains(gk)) {
				logWarn(METHOD_NAME, "seqList 未検出 csvId=" + csvId
						+ ", seqList=" + e.getValue()
						+ ", groupKey=" + gk);
			}
		}

		if (!localOnly) {
			boolean uploaded = fileExistsService.uploadSeqListIfExists(bucket, prefix);
			logInfo(METHOD_NAME, "seqList.txt S3反映 result=" + uploaded);
		}
	}

	/**
	 * seqList JSON 読込
	 * 旧形式(csv改行区切り)も読めるようにしておく
	 */
	private List<List<Integer>> readSeqListJson(Path path) throws IOException {
		if (!Files.exists(path)) {
			return new ArrayList<>();
		}

		String raw = Files.readString(path, StandardCharsets.UTF_8).trim();
		if (raw.isEmpty()) {
			return new ArrayList<>();
		}

		if (raw.startsWith("[")) {
			List<List<Integer>> result = JSON.readValue(raw, new TypeReference<List<List<Integer>>>() {
			});
			return normalizeGroups(result);
		}

		List<List<Integer>> result = new ArrayList<>();
		for (String line : raw.split("\n")) {
			String t = safe(line).trim();
			if (t.isEmpty()) {
				continue;
			}

			List<Integer> group = new ArrayList<>();
			for (String part : t.split(",")) {
				String s = safe(part).trim();
				if (s.isEmpty()) {
					continue;
				}
				try {
					group.add(Integer.valueOf(s));
				} catch (NumberFormatException ignore) {
				}
			}

			group = normalizeSeqList(group);
			if (!group.isEmpty()) {
				result.add(group);
			}
		}

		return result;
	}

	private List<List<Integer>> normalizeGroups(List<List<Integer>> groups) {
		if (groups == null || groups.isEmpty()) {
			return new ArrayList<>();
		}

		List<List<Integer>> result = new ArrayList<>();
		for (List<Integer> group : groups) {
			List<Integer> normalized = normalizeSeqList(group);
			if (!normalized.isEmpty()) {
				result.add(normalized);
			}
		}
		return result;
	}

	private List<Integer> normalizeSeqList(List<Integer> src) {
		if (src == null || src.isEmpty()) {
			return new ArrayList<>();
		}

		List<Integer> ids = new ArrayList<>();
		for (Integer n : new TreeSet<>(src)) {
			if (n != null) {
				ids.add(n);
			}
		}
		return ids;
	}

	private String findMatchedCsvId(Map<String, List<Integer>> deleteGroupMap, List<Integer> normalizedGroup) {
		String currentGroupKey = groupKey(normalizedGroup);

		for (Map.Entry<String, List<Integer>> e : deleteGroupMap.entrySet()) {
			String targetKey = groupKey(e.getValue());
			if (currentGroupKey.equals(targetKey)) {
				return e.getKey();
			}
		}

		return null;
	}

	private String groupKey(List<Integer> ids) {
		return normalizeSeqList(ids).stream()
				.map(String::valueOf)
				.collect(Collectors.joining("-"));
	}

	private static String safe(String s) {
		return (s == null) ? "" : s;
	}

	private static String normalizePrefix(String prefix) {
		if (prefix == null) {
			return "";
		}
		String p = prefix.trim();
		p = p.replaceAll("^/+", "");
		p = p.replaceAll("/+$", "");
		return p;
	}

	private static String normalizeS3Key(String key) {
		if (key == null) {
			return null;
		}
		String k = key;
		while (k.startsWith("/")) {
			k = k.substring(1);
		}
		return k;
	}

	private static String joinS3Key(String prefix, String fileName) {
		String p = (prefix == null) ? "" : prefix.trim();
		p = p.replaceAll("^/+", "");
		p = p.replaceAll("/+$", "");

		String f = (fileName == null) ? "" : fileName.trim();
		f = f.replaceAll("^/+", "");

		if (p.isBlank()) {
			return f;
		}
		return p + "/" + f;
	}

	private void logInfo(String method, String message) {
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, method,
				MessageCdConst.MCD00099I_LOG, message);
	}

	private void logWarn(String method, String message) {
		this.manageLoggerComponent.debugWarnLog(
				PROJECT_NAME, CLASS_NAME, method,
				MessageCdConst.MCD00099I_LOG, message);
	}

	private void endLog(String method) {
		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, method, "end");
	}

	private static final class ResolvedCsvSource {
		private final Path path;
		private final boolean temporary;
		private final String sourceType;

		private ResolvedCsvSource(Path path, boolean temporary, String sourceType) {
			this.path = path;
			this.temporary = temporary;
			this.sourceType = sourceType;
		}
	}

	private static final class DeleteResult {
		private final Set<String> deletedOriginalCsvIds = new LinkedHashSet<>();
		private final Set<String> deletedCanonicalCsvIds = new LinkedHashSet<>();
		private final Set<String> failedOriginalCsvIds = new LinkedHashSet<>();
		private final Set<String> deletedLocalRelativePaths = new LinkedHashSet<>();
	}

}
