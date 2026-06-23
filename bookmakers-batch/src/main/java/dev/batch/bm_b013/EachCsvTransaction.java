package dev.batch.bm_b013;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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

		// 1) season終了対象の country-league から csv_id folder prefix を作成
		List<String> folderPrefixes = buildCsvFolderPrefixes(dto);
		if (folderPrefixes.isEmpty()) {
			logInfo(METHOD_NAME, "削除対象の csv folder prefix が空のため処理終了");
			endLog(METHOD_NAME);
			return;
		}

		List<String> folderCategories = buildCsvFolderCategories(dto);
		if (folderCategories.isEmpty()) {
			logInfo(METHOD_NAME, "削除対象の folderCategories folder prefix が空のため処理終了");
			endLog(METHOD_NAME);
			return;
		}

		logInfo(METHOD_NAME, "削除対象 csv_id prefixes=" + folderPrefixes);

		// 2) csv_id prefix で削除対象 csv_detail_manage を取得
		List<CsvDetailManageEntity> targets = this.csvDetailManageBatchRepository
				.findDeleteTargetsByCsvIdAnCategoryPrefixes(folderPrefixes, folderCategories);

		if (targets == null || targets.isEmpty()) {
			logInfo(METHOD_NAME, "削除対象の csv_detail_manage が存在しません");
			endLog(METHOD_NAME);
			return;
		}

		Set<String> dataCagorySet = new LinkedHashSet<>();
		for (CsvDetailManageEntity entity : targets) {
			if (entity == null) {
				continue;
			}
			String dataCategory = safe(entity.getDataCategory()).trim();
			if (!dataCategory.isEmpty()) {
				dataCagorySet.add(dataCategory);
			}
		}

		List<String> dataCategories = new ArrayList<>(dataCagorySet);

		// 3) csv_id 一覧化
		Set<String> csvIdSet = new LinkedHashSet<>();
		for (CsvDetailManageEntity entity : targets) {
			if (entity == null) {
				continue;
			}
			String csvId = safe(entity.getCsvId()).trim();
			if (!csvId.isEmpty()) {
				csvIdSet.add(csvId);
			}
		}

		if (csvIdSet.isEmpty()) {
			logInfo(METHOD_NAME, "削除対象 csv_id が存在しません");
			endLog(METHOD_NAME);
			return;
		}

		List<String> csvIds = new ArrayList<>(csvIdSet);
		logInfo(METHOD_NAME, "削除対象 csv_id 件数=" + csvIds.size());
		for (String csvId : csvIds) {
			logInfo(METHOD_NAME, "削除対象 csvId=" + csvId);
		}

		Path baseDir = Paths.get(config.getCsvFolder()).toAbsolutePath().normalize();
		Files.createDirectories(baseDir);

		String bucket = config.getS3BucketsStats();
		String prefix = normalizePrefix(finalPrefix);
		Path snapshotPath = baseDir.resolve(SNAPSHOT_FILE_NAME);

		// 0) 管理ファイルをローカル同期
		prepareManageFilesLocalCache(bucket, prefix, METHOD_NAME);

		// 1) csvId -> seqList snapshot 構築
		Map<String, List<Integer>> existingSnapshot = readSnapshot(snapshotPath);
		Map<String, List<Integer>> currentCsvInfo = loadCsvInfoSnapshot();
		Map<String, List<Integer>> csvFileSnapshot = loadSeqSnapshotFromDeleteTargetCsvFiles(csvIds);
		Map<String, List<Integer>> deleteSnapshot = buildDeleteSnapshot(csvIds, csvFileSnapshot, currentCsvInfo,
				existingSnapshot);

		writeSnapshot(snapshotPath, deleteSnapshot);
		logInfo(METHOD_NAME, "削除snapshot保存完了 path=" + snapshotPath
				+ ", snapshot.size=" + deleteSnapshot.size());

		for (Map.Entry<String, List<Integer>> e : deleteSnapshot.entrySet()) {
			logInfo(METHOD_NAME, "snapshot csvId=" + e.getKey()
					+ ", seqList=" + e.getValue()
					+ ", groupKey=" + groupKey(e.getValue()));
		}

		for (String csvId : csvIds) {
			if (!deleteSnapshot.containsKey(csvId)) {
				logWarn(METHOD_NAME, "snapshot未取得 csvId=" + csvId);
			}
		}

		// 2) 実CSV削除
		DeleteResult deleteResult = deletePhysicalCsvFiles(csvIds);

		logInfo(METHOD_NAME, "CSV削除結果 success=" + deleteResult.deletedCsvIds.size()
				+ ", failed=" + deleteResult.failedCsvIds.size());

		if (deleteResult.deletedCsvIds.isEmpty()) {
			logWarn(METHOD_NAME, "CSV削除成功件数=0 です");
			retainSnapshotForFailed(snapshotPath, deleteSnapshot, deleteResult.failedCsvIds);
			endLog(METHOD_NAME);
		}

		// 2.5) 空親フォルダ削除（ローカルファイルシステム上）
		if (!deleteResult.deletedPhysicalCsvIds.isEmpty()) {
			cleanupEmptyParentFolders(deleteResult.deletedPhysicalCsvIds);
		}

		// 3) data_team_list.txt 更新
		if (!deleteResult.deletedCsvIds.isEmpty() || !deleteResult.deletedPhysicalCsvIds.isEmpty()) {
			updateDataTeamList(deleteResult.deletedCsvIds, deleteResult.deletedPhysicalCsvIds);
		}

		// 4) seqList.txt 更新
		if (!deleteResult.deletedCsvIds.isEmpty()) {
			updateSeqList(deleteResult.deletedCsvIds, deleteSnapshot);
		}

		logInfo(METHOD_NAME, "csv_detail_manage 削除前=" + deleteResult.deletedCsvIds);

		// 5) csv_detail_manage 更新（成功分のみ）
		int deletedCategory = 0;
		int deleted = this.csvDetailManageBatchRepository.deleteByCsvIds(
				new ArrayList<>(deleteResult.deletedCsvIds));
		if (!dataCategories.isEmpty()) {
			deletedCategory = this.csvDetailManageBatchRepository.deleteByDataCategories(
					new ArrayList<>(dataCategories));
		}

		logInfo(METHOD_NAME, "csv_detail_manage 削除件数=" + (deleted + deletedCategory));

		// 6) failed 分だけ snapshot を残す
		retainSnapshotForFailed(snapshotPath, deleteSnapshot, deleteResult.failedCsvIds);

		if (!deleteResult.failedCsvIds.isEmpty()) {
			for (String failedCsvId : deleteResult.failedCsvIds) {
				logWarn(METHOD_NAME, "CSV削除失敗 csvId=" + failedCsvId);
			}
		}

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
				logWarn(METHOD_NAME, "bean.getCsvInfo() が null");
				return new LinkedHashMap<>();
			}

			Map<String, List<Integer>> result = new LinkedHashMap<>();
			for (Map.Entry<String, List<Integer>> e : csvInfo.entrySet()) {
				String csvId = safe(e.getKey()).trim();
				if (csvId.isEmpty()) {
					continue;
				}
				List<Integer> seqs = normalizeSeqList(e.getValue());
				if (!seqs.isEmpty()) {
					result.put(csvId, seqs);
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

			String physicalCsvId = this.csvFileNameService.toPhysicalCsvId(csvId);
			List<Integer> seqs = null;

			// 優先1: 削除予定CSV実体から読んだ seq
			seqs = findSeqListByAnyKey(csvFileSnapshot, csvId, physicalCsvId);

			// 優先2: 現在の csvInfo
			if (seqs == null || seqs.isEmpty()) {
				seqs = findSeqListByAnyKey(currentCsvInfo, csvId, physicalCsvId);
			}

			// 優先3: 既存 snapshot
			if (seqs == null || seqs.isEmpty()) {
				seqs = findSeqListByAnyKey(existingSnapshot, csvId, physicalCsvId);
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
			String csvId,
			String physicalCsvId) {

		if (source == null || source.isEmpty()) {
			return null;
		}

		List<Integer> seqs = source.get(csvId);
		if (seqs != null && !seqs.isEmpty()) {
			return seqs;
		}

		seqs = source.get(physicalCsvId);
		if (seqs != null && !seqs.isEmpty()) {
			return seqs;
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
				resolved = resolveCsvSourceForSeqRead(baseDir, bucket, prefix, physicalCsvId);

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
			String physicalCsvId) throws IOException {

		final String METHOD_NAME = "resolveCsvSourceForSeqRead";

		Path localPath = baseDir.resolve(physicalCsvId).normalize();
		if (Files.exists(localPath) && Files.isRegularFile(localPath)) {
			logInfo(METHOD_NAME, "ローカルCSVを使用 physicalCsvId=" + physicalCsvId
					+ ", path=" + localPath);
			return new ResolvedCsvSource(localPath, false, "local");
		}

		if (localOnly) {
			logInfo(METHOD_NAME, "localOnly=true のため S3 読込スキップ physicalCsvId=" + physicalCsvId);
			return null;
		}

		String s3Key = normalizeS3Key(joinS3Key(prefix, physicalCsvId));
		Path downloaded = downloadCsvFromS3ToTemp(bucket, s3Key, physicalCsvId);

		if (downloaded != null && Files.exists(downloaded) && Files.isRegularFile(downloaded)) {
			logInfo(METHOD_NAME, "S3 CSVを一時取得 physicalCsvId=" + physicalCsvId
					+ ", bucket=" + bucket
					+ ", key=" + s3Key
					+ ", tempPath=" + downloaded);
			return new ResolvedCsvSource(downloaded, true, "s3");
		}

		logWarn(METHOD_NAME, "ローカル/S3 いずれにもCSVが見つかりません physicalCsvId=" + physicalCsvId
				+ ", bucket=" + bucket
				+ ", key=" + s3Key);

		return null;
	}

	/**
	 * snapshot 読込
	 */
	private Map<String, List<Integer>> readSnapshot(Path snapshotPath) {
		final String METHOD_NAME = "readSnapshot";

		try {
			if (snapshotPath == null || !Files.exists(snapshotPath)) {
				logInfo(METHOD_NAME, "snapshot 不存在 path=" + snapshotPath);
				return new LinkedHashMap<>();
			}

			String json = Files.readString(snapshotPath, StandardCharsets.UTF_8).trim();
			if (json.isEmpty()) {
				logInfo(METHOD_NAME, "snapshot 空 path=" + snapshotPath);
				return new LinkedHashMap<>();
			}

			Map<String, List<Integer>> map = JSON.readValue(
					json,
					new TypeReference<LinkedHashMap<String, List<Integer>>>() {
					});

			Map<String, List<Integer>> normalized = new LinkedHashMap<>();
			for (Map.Entry<String, List<Integer>> e : map.entrySet()) {
				String csvId = safe(e.getKey()).trim();
				if (csvId.isEmpty()) {
					continue;
				}
				List<Integer> seqs = normalizeSeqList(e.getValue());
				if (!seqs.isEmpty()) {
					normalized.put(csvId, seqs);
				}
			}

			logInfo(METHOD_NAME, "既存snapshot読込完了 path=" + snapshotPath
					+ ", size=" + normalized.size());

			return normalized;

		} catch (Exception e) {
			logWarn(METHOD_NAME, "snapshot読込失敗 path=" + snapshotPath
					+ ", reason=" + e.getMessage());
			return new LinkedHashMap<>();
		}
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
		List<String> prefixes = new ArrayList<>();

		if (dto == null || dto.getCountryLeague() == null) {
			return prefixes;
		}

		for (String value : dto.getCountryLeague()) {
			prefixes.add(value);
		}

		return prefixes.stream()
				.distinct()
				.collect(Collectors.toList());
	}

	/**
	 * DTO の countryLeague から data_category 用 folder prefix を作成
	 * 例: 日本-J1リーグ -> 日本: J1リーグ
	 */
	private List<String> buildCsvFolderCategories(TransactionDTO dto) {
	    List<String> prefixes = new ArrayList<>();

	    if (dto == null || dto.getCountryLeague() == null) {
	        return prefixes;
	    }

	    for (String value : dto.getCountryLeague()) {
	        if (value == null || value.isBlank()) {
	            continue;
	        }

	        String trimmed = value.trim();
	        int separatorIndex = trimmed.indexOf('-');

	        if (separatorIndex >= 0) {
	            String country = trimmed.substring(0, separatorIndex).trim();
	            String league = trimmed.substring(separatorIndex + 1).trim();
	            prefixes.add(country + ": " + league);
	        } else {
	            prefixes.add(trimmed);
	        }
	    }

	    return prefixes.stream()
	            .distinct()
	            .collect(Collectors.toList());
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

		for (String csvId : csvIds) {
			if (csvId == null || csvId.isBlank()) {
				continue;
			}

			String physicalCsvId = this.csvFileNameService.toPhysicalCsvId(csvId);
			Path localPath = baseDir.resolve(physicalCsvId).normalize();
			Path parent = localPath.getParent();
			boolean parentExists = parent != null && Files.exists(parent);

			logInfo(METHOD_NAME, "削除前確認 csvId=" + csvId
					+ ", physicalCsvId=" + physicalCsvId
					+ ", localPath=" + localPath
					+ ", exists=" + Files.exists(localPath)
					+ ", isRegularFile=" + Files.isRegularFile(localPath)
					+ ", parent=" + parent
					+ ", parentExists=" + parentExists);

			boolean s3DeleteOk = false;

			try {
				boolean deletedLocal = Files.deleteIfExists(localPath);

				if (deletedLocal) {
					logInfo(METHOD_NAME, "ローカルCSV削除 csvId=" + csvId
							+ ", physicalCsvId=" + physicalCsvId
							+ ", path=" + localPath
							+ ", deleted=" + deletedLocal);
				} else {
					logWarn(METHOD_NAME, "ローカルCSV未削除(ファイル不存在) csvId=" + csvId
							+ ", physicalCsvId=" + physicalCsvId
							+ ", path=" + localPath);
				}

				if (!localOnly) {
					// S3 key は csvId を使う
					String s3Key = normalizeS3Key(joinS3Key(prefix, csvId));

					logInfo(METHOD_NAME, "S3削除実行 csvId=" + csvId
							+ ", physicalCsvId=" + physicalCsvId
							+ ", bucket=" + bucket
							+ ", key=" + s3Key);

					s3Operator.delete(bucket, s3Key);
					s3DeleteOk = true;

					logInfo(METHOD_NAME, "S3 CSV削除完了 csvId=" + csvId
							+ ", physicalCsvId=" + physicalCsvId
							+ ", bucket=" + bucket
							+ ", key=" + s3Key);
				} else {
					s3DeleteOk = true;
				}

				if (s3DeleteOk) {
					result.deletedCsvIds.add(csvId);
					result.deletedPhysicalCsvIds.add(physicalCsvId);
				} else {
					result.failedCsvIds.add(csvId);
				}

			} catch (Exception e) {
				result.failedCsvIds.add(csvId);

				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
						"CSV削除失敗 csvId=" + csvId
								+ ", physicalCsvId=" + physicalCsvId
								+ ", path=" + localPath);
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
			Set<String> deletedCsvIds,
			Set<String> deletedPhysicalCsvIds) throws IOException {

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
		if (deletedCsvIds != null) {
			deleteKeys.addAll(deletedCsvIds);
			for (String csvId : deletedCsvIds) {
				if (csvId != null && !csvId.isBlank()) {
					deleteKeys.add(this.csvFileNameService.toPhysicalCsvId(csvId));
				}
			}
		}
		if (deletedPhysicalCsvIds != null) {
			deleteKeys.addAll(deletedPhysicalCsvIds);
		}

		List<String> lines = Files.readAllLines(localTeamPath, StandardCharsets.UTF_8);
		List<String> newLines = new ArrayList<>();
		List<String> removedLines = new ArrayList<>();

		for (String line : lines) {
			if (line == null || line.isBlank()) {
				continue;
			}

			String[] parts = line.split("\t", 2);
			String csvKey = safe(parts[0]).trim();

			if (deleteKeys.contains(csvKey)) {
				removedLines.add(line);
				logInfo(METHOD_NAME, "data_team_list 削除 csvId=" + csvKey + ", line=" + line);
				continue;
			}

			newLines.add(line);
		}

		// 更新後に1行も残らない場合はファイル自体を削除
		if (newLines.isEmpty()) {
			boolean deletedLocal = Files.deleteIfExists(localTeamPath);
			logInfo(METHOD_NAME, "data_team_list.txt 削除完了. path=" + localTeamPath
					+ ", deleted=" + deletedLocal
					+ ", removed=" + removedLines.size()
					+ ", remaining=0");

			if (!localOnly) {
				String s3Key = normalizeS3Key(joinS3Key(prefix, FileExistsService.TEAM_FILE_NAME));
				s3Operator.delete(bucket, s3Key);
				logInfo(METHOD_NAME, "data_team_list.txt S3削除完了 bucket=" + bucket + ", key=" + s3Key);
			}

			return;
		}

		Files.write(
				localTeamPath,
				newLines,
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);

		logInfo(METHOD_NAME, "data_team_list.txt 更新完了. path=" + localTeamPath
				+ ", removed=" + removedLines.size()
				+ ", remaining=" + newLines.size());

		for (String key : deleteKeys) {
			boolean found = removedLines.stream().anyMatch(line -> line.startsWith(key + "\t") || line.equals(key));
			if (!found) {
				logWarn(METHOD_NAME, "data_team_list 未検出 csvId=" + key);
			}
		}

		if (!localOnly) {
			boolean uploaded = fileExistsService.uploadDataTeamListIfExists(bucket, prefix);
			logInfo(METHOD_NAME, "data_team_list.txt S3反映 result=" + uploaded);
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
		private final Set<String> deletedCsvIds = new LinkedHashSet<>();
		private final Set<String> failedCsvIds = new LinkedHashSet<>();
		private final Set<String> deletedPhysicalCsvIds = new LinkedHashSet<>();
	}
}
