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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 *
 * 削除前バックアップ:
 * - execute() 内で物理削除の直前に archiveDeleteTargetCsvFilesToRecordBucket(...) を必ず呼び出し、
 *   削除対象CSV一式をZIP化して record バケット（RecordFileOperationService 経由）へアップロードしている。
 *   アップロードが失敗した場合は例外を throw し、その回の物理削除自体を行わない（バックアップ必須化）。
 *
 * 修正:
 * - localOnly=true のとき、ローカルCSVが実際に削除できたかを確認せずに
 *   常に成功扱いにしていたバグを修正（実際に削除できた場合のみ成功扱い）
 * - ローカル/S3いずれにも実体が無いが、snapshotに正当な seqList が残っている
 *   （＝前回実行で物理削除は成功したが txt/DB 反映前にクラッシュした）ケースを
 *   「削除済みとみなして後続処理を続行できる」ように救済
 * - findExistingS3CsvKey の「フォルダ内CSVが1件だけなら採用」フォールバックを、
 *   削除対象キー解決（deletePhysicalCsvFiles）では無効化し、無関係な別CSVを
 *   誤って削除しないようにピンポイント性を担保
 * - normalizeSeqList を seqKey の連番部分に基づく数値ソートに統一（ExportCsvService と同一ロジック）
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
	 *
	 * 処理の流れ:
	 * 1. countryLeague から削除対象 folder prefix / data_category prefix を作成
	 * 2. 該当する csv_detail_manage を検索し、削除対象 csvId 一覧を確定
	 * 3. 削除前の csvId -> seqList snapshot を作成・保存（クラッシュ時の再処理用）
	 * 4. 削除対象CSVを record バケットへZIPバックアップ
	 * 5. 物理CSVファイル（ローカル/S3）を削除
	 * 6. 物理削除に成功した分だけ data_team_list.txt / seqList.txt / csv_detail_manage を更新
	 *
	 * @param dto 削除対象の country / league 情報を保持する DTO
	 * @throws Exception 途中処理で回復不能な例外が発生した場合
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
		Map<String, List<String>> existingSnapshot = readSnapshot(snapshotPath);
		Map<String, List<String>> currentCsvInfo = loadCsvInfoSnapshot();
		Map<String, List<String>> csvFileSnapshot = loadSeqSnapshotFromDeleteTargetCsvFiles(originalCsvIds);
		Map<String, List<String>> deleteSnapshot = buildDeleteSnapshot(
				originalCsvIds, csvFileSnapshot, currentCsvInfo, existingSnapshot);
		writeSnapshot(snapshotPath, deleteSnapshot);
		String backupZipKey = archiveDeleteTargetCsvFilesToRecordBucket(originalCsvIds);
		logInfo(METHOD_NAME, "削除前CSVバックアップ完了 key=" + backupZipKey);
		DeleteResult deleteResult = deletePhysicalCsvFiles(originalCsvIds, deleteSnapshot);
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
	 * 管理ファイル（seqList.txt / data_team_list.txt）を S3 からローカルへ同期する。
	 * localOnly=true の場合はダウンロードをスキップする。
	 *
	 * @param bucket       S3 バケット名
	 * @param prefix       S3 キー prefix
	 * @param parentMethod 呼び出し元メソッド名（ログ用、未使用）
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
	 * ReaderCurrentCsvInfoBean から csvId -> seqList を取得する。
	 * 取得したキーはオリジナル表記・正規化表記の両方で登録し、後続の lookup を容易にする。
	 * localOnly=true の場合は空の Map を返す。
	 *
	 * @return csvId（オリジナル/正規化）をキーとした seqList の Map
	 */
	private Map<String, List<String>> loadCsvInfoSnapshot() {
		final String METHOD_NAME = "loadCsvInfoSnapshot";
		if (localOnly) {
			logInfo(METHOD_NAME, "localOnly=true のため csvInfo snapshot 読込をスキップ");
			return new LinkedHashMap<>();
		}
		try {
			bean.init();
			Map<String, List<String>> csvInfo = bean.getCsvInfo();
			if (csvInfo == null) {
				return new LinkedHashMap<>();
			}
			Map<String, List<String>> result = new LinkedHashMap<>();
			for (Map.Entry<String, List<String>> e : csvInfo.entrySet()) {
				String originalKey = safe(e.getKey()).trim();
				String canonicalKey = canonicalizeCsvId(originalKey);
				List<String> seqs = normalizeSeqList(e.getValue());
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
	 * 削除対象 csvId ごとに、後続処理で使う seqList snapshot を組み立てる。
	 * 優先順位:
	 * 1. 削除対象CSV実体から直接読み取った seqList（csvFileSnapshot）
	 * 2. 現在の csvInfo（currentCsvInfo）
	 * 3. 既存の snapshot ファイル（existingSnapshot、前回実行の残骸）
	 *
	 * @param csvIds          削除対象 csvId 一覧
	 * @param csvFileSnapshot CSV実体から読み取った csvId -> seqList
	 * @param currentCsvInfo  ReaderCurrentCsvInfoBean 由来の csvId -> seqList
	 * @param existingSnapshot 前回実行で残った snapshot ファイルの内容
	 * @return csvId -> seqList の Map（今回実行分の確定 snapshot）
	 */
	private Map<String, List<String>> buildDeleteSnapshot(
			List<String> csvIds,
			Map<String, List<String>> csvFileSnapshot,
			Map<String, List<String>> currentCsvInfo,
			Map<String, List<String>> existingSnapshot) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		for (String csvId : csvIds) {
			if (csvId == null || csvId.isBlank()) {
				continue;
			}
			Set<String> lookupKeys = buildCsvIdLookupKeys(csvId);
			List<String> seqs = findSeqListByAnyKey(csvFileSnapshot, lookupKeys);
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

	/**
	 * 複数の lookup キー候補のいずれかで source から seqList を検索する。
	 *
	 * @param source 検索対象の Map（csvId -> seqList）
	 * @param keys   lookup キー候補
	 * @return 最初に一致した seqList。見つからない場合は null
	 */
	private List<String> findSeqListByAnyKey(
			Map<String, List<String>> source,
			Set<String> keys) {
		if (source == null || source.isEmpty() || keys == null || keys.isEmpty()) {
			return null;
		}
		for (String key : keys) {
			List<String> seqs = source.get(key);
			if (seqs != null && !seqs.isEmpty()) {
				return seqs;
			}
		}
		return null;
	}

	/**
	 * 削除対象CSV実体（ローカル優先、無ければS3から一時取得）から直接 seq を読み込む。
	 *
	 * @param csvIds 削除対象 csvId 一覧
	 * @return csvId -> seqList の Map（実体を読めなかった csvId はキーに含まれない）
	 */
	private Map<String, List<String>> loadSeqSnapshotFromDeleteTargetCsvFiles(List<String> csvIds) {
		final String METHOD_NAME = "loadSeqSnapshotFromDeleteTargetCsvFiles";
		Map<String, List<String>> result = new LinkedHashMap<>();
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
				List<String> seqs = extractSeqListFromCsv(resolved.path);
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

	/**
	 * 単一 csvId について、seq 読み取り用のCSV実体を解決する。
	 * 解決順序: ローカル(physicalCsvId) -> ローカル(csvId) -> S3（読み取り専用なので単一ファイルフォールバック許可）。
	 * S3 から取得した場合は一時ファイルとしてダウンロードする（呼び出し元で削除が必要）。
	 *
	 * @param baseDir       ローカルCSVのベースディレクトリ
	 * @param bucket        S3 バケット名
	 * @param prefix        S3 キー prefix
	 * @param csvId         オリジナル csvId
	 * @param physicalCsvId 物理ファイル名としての csvId
	 * @return 解決結果。どこにも実体が無い場合は null
	 * @throws IOException S3ダウンロード等でI/Oエラーが発生した場合
	 */
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
		// 3. S3 実在キー解決（読み取り専用: 単一ファイルフォールバックを許可）
		String resolvedS3Key = findExistingS3CsvKey(bucket, prefix, csvId, physicalCsvId, true);
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
	 * snapshot ファイル（JSON）を読み込む。
	 * 読み込んだキーはオリジナル表記・正規化表記の両方で登録し、後続の lookup を容易にする。
	 *
	 * @param snapshotPath snapshot ファイルパス
	 * @return csvId -> seqList の Map。ファイルが無い/空/壊れている場合は空の Map
	 */
	private Map<String, List<String>> readSnapshot(Path snapshotPath) {
		final String METHOD_NAME = "readSnapshot";
		try {
			if (snapshotPath == null || !Files.exists(snapshotPath)) {
				return new LinkedHashMap<>();
			}
			String json = Files.readString(snapshotPath, StandardCharsets.UTF_8).trim();
			if (json.isEmpty()) {
				return new LinkedHashMap<>();
			}
			Map<String, List<String>> map = JSON.readValue(
					json,
					new TypeReference<LinkedHashMap<String, List<String>>>() {
					});
			Map<String, List<String>> normalized = new LinkedHashMap<>();
			for (Map.Entry<String, List<String>> e : map.entrySet()) {
				String originalKey = safe(e.getKey()).trim();
				String canonicalKey = canonicalizeCsvId(originalKey);
				List<String> seqs = normalizeSeqList(e.getValue());
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

	/**
	 * csvId（フォルダ/ファイルパス）を正規化する。
	 * NFKC正規化・区切り文字統一を行い、最後の要素（ファイル名）以外の各セグメントを
	 * canonicalizeFolderSegment で正規化する。
	 *
	 * @param rawCsvId 正規化前の csvId
	 * @return 正規化後の csvId（空の場合は空文字）
	 */
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
			// 最後の要素はファイル名なのでそのまま
			if (i < parts.length - 1) {
				normalizedParts.add(canonicalizeFolderSegment(part));
			} else {
				normalizedParts.add(part);
			}
		}
		return String.join("/", normalizedParts);
	}

	/**
	 * folder prefix を正規化する（canonicalizeFolderSegment のエイリアス）。
	 *
	 * @param value 正規化前の値
	 * @return 正規化後の folder prefix
	 */
	private String canonicalizeFolderPrefix(String value) {
		return canonicalizeFolderSegment(value);
	}

	/**
	 * ハイフン統一形式（例: 日本-J1）を旧コロン形式（例: 日本: J1）へ変換する。
	 * 後方互換のため、削除対象 prefix に旧形式も含めるために使用する。
	 *
	 * @param canonical ハイフン統一形式の値
	 * @return 旧コロン形式の値。変換できない場合は canonical をそのまま返す
	 */
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

	/**
	 * csvId から、各種 Map の lookup に使うキー候補一式（オリジナル/正規化/物理ファイル名）を作成する。
	 *
	 * @param originalCsvId オリジナル csvId
	 * @return lookup キー候補の集合
	 */
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

	/**
	 * csvId から、ローカル実体削除の際に試すべき相対パス候補一式を作成する。
	 *
	 * @param originalCsvId オリジナル csvId
	 * @return 物理ファイルパス候補の集合
	 */
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

	/**
	 * S3上に実在するCSVキーを解決する。
	 * 解決順序: 完全一致 -> 正規化一致 -> ファイル名一致 -> （allowSingleFileFallback=true の場合のみ）
	 * フォルダ内CSVが1件だけならそれを採用。
	 *
	 * @param bucket                  S3 バケット名
	 * @param prefix                  S3 キー prefix
	 * @param csvId                   オリジナル csvId
	 * @param physicalCsvId           物理ファイル名としての csvId
	 * @param allowSingleFileFallback 「フォルダ内CSVが1件だけなら採用」フォールバックを許可するか。
	 *                                削除対象キー解決（destructive）では false を渡し、
	 *                                無関係な別CSVの誤削除を防ぐ。読み取り専用用途では true を渡す。
	 * @return 解決できたS3キー。解決できない場合は null
	 */
	private String findExistingS3CsvKey(
			String bucket,
			String prefix,
			String csvId,
			String physicalCsvId,
			boolean allowSingleFileFallback) {
		final String METHOD_NAME = "findExistingS3CsvKey";
		try {
			Set<String> candidateKeys = new LinkedHashSet<>();
			String originalCsvId = safe(csvId).trim();
			String canonicalCsvId = canonicalizeCsvId(originalCsvId);
			String originalPhysicalCsvId = safe(physicalCsvId).trim();
			String canonicalPhysicalCsvId = canonicalizeCsvId(originalPhysicalCsvId);
			if (!originalCsvId.isBlank()) {
				candidateKeys.add(normalizeS3Key(joinS3Key(prefix, originalCsvId)));
			}
			if (!canonicalCsvId.isBlank()) {
				candidateKeys.add(normalizeS3Key(joinS3Key(prefix, canonicalCsvId)));
			}
			if (!originalPhysicalCsvId.isBlank()) {
				candidateKeys.add(normalizeS3Key(joinS3Key(prefix, originalPhysicalCsvId)));
			}
			if (!canonicalPhysicalCsvId.isBlank()) {
				candidateKeys.add(normalizeS3Key(joinS3Key(prefix, canonicalPhysicalCsvId)));
			}
			if (candidateKeys.isEmpty()) {
				logWarn(METHOD_NAME, "S3キー候補が空です csvId=" + csvId + ", physicalCsvId=" + physicalCsvId);
				return null;
			}
			Set<String> parentPrefixes = candidateKeys.stream()
					.map(this::extractParentPrefix)
					.filter(s -> s != null && !s.isBlank())
					.collect(Collectors.toCollection(LinkedHashSet::new));
			if (parentPrefixes.isEmpty()) {
				logWarn(METHOD_NAME, "親prefixを解決できません csvId=" + csvId
						+ ", physicalCsvId=" + physicalCsvId
						+ ", candidateKeys=" + candidateKeys);
				return null;
			}
			for (String parentPrefix : parentPrefixes) {
				List<String> keys = s3Operator.listKeys(bucket, parentPrefix);
				if (keys == null || keys.isEmpty()) {
					logInfo(METHOD_NAME, "prefix配下にオブジェクトなし bucket=" + bucket
							+ ", parentPrefix=" + parentPrefix);
					continue;
				}
				// 1) 完全一致優先
				for (String key : keys) {
					for (String candidate : candidateKeys) {
						if (safe(key).equals(candidate)) {
							logInfo(METHOD_NAME, "S3キー完全一致で解決 bucket=" + bucket + ", key=" + key);
							return key;
						}
					}
				}
				// 2) 正規化一致
				Set<String> normalizedCandidates = candidateKeys.stream()
						.map(this::normalizeKeyForCompare)
						.collect(Collectors.toCollection(LinkedHashSet::new));
				for (String key : keys) {
					String normalizedKey = normalizeKeyForCompare(key);
					if (normalizedCandidates.contains(normalizedKey)) {
						logInfo(METHOD_NAME, "S3キー正規化一致で解決 bucket=" + bucket + ", key=" + key);
						return key;
					}
				}
				// 3) ファイル名一致
				Set<String> candidateFileNames = new LinkedHashSet<>();
				for (String candidate : candidateKeys) {
					String fileName = extractFileName(candidate);
					if (!fileName.isBlank()) {
						candidateFileNames.add(fileName);
					}
				}
				List<String> matchedByFileName = keys.stream()
						.filter(k -> candidateFileNames.contains(extractFileName(k)))
						.collect(Collectors.toList());
				if (matchedByFileName.size() == 1) {
					logInfo(METHOD_NAME, "S3キーをファイル名一致で解決 bucket=" + bucket
							+ ", key=" + matchedByFileName.get(0));
					return matchedByFileName.get(0);
				}
				// 4) CSV が1件だけなら採用
				//    ※ 削除処理（deletePhysicalCsvFiles）からは allowSingleFileFallback=false で
				//      呼び出し、このフォールバックを無効化する。候補と無関係な別のCSVが
				//      たまたま1件だけそのフォルダに残っていた場合、それを誤って削除対象と
				//      みなしてしまう危険があるため（＝物理削除の「ピンポイント性」を損なう）。
				//      seq読込・バックアップ（読み取り専用の用途）では従来通り許可する。
				if (allowSingleFileFallback) {
					List<String> csvKeys = keys.stream()
							.filter(k -> safe(k).toLowerCase().endsWith(".csv"))
							.collect(Collectors.toList());
					if (csvKeys.size() == 1) {
						logInfo(METHOD_NAME, "prefix配下CSV単一件で解決 bucket=" + bucket
								+ ", key=" + csvKeys.get(0));
						return csvKeys.get(0);
					}
				}
			}
			logWarn(METHOD_NAME, "S3キーを解決できません bucket=" + bucket
					+ ", csvId=" + csvId
					+ ", physicalCsvId=" + physicalCsvId
					+ ", candidateKeys=" + candidateKeys
					+ ", parentPrefixes=" + parentPrefixes);
			return null;
		} catch (Exception e) {
			logWarn(METHOD_NAME, "S3キー解決失敗 csvId=" + csvId
					+ ", physicalCsvId=" + physicalCsvId
					+ ", reason=" + e.getMessage());
			return null;
		}
	}

	/**
	 * S3キーから親prefix（末尾スラッシュ含む）を抽出する。
	 *
	 * @param key S3キー
	 * @return 親prefix。スラッシュが無い場合は空文字
	 */
	private String extractParentPrefix(String key) {
		String k = safe(key).trim().replace("\\", "/");
		int idx = k.lastIndexOf('/');
		if (idx < 0) {
			return "";
		}
		return k.substring(0, idx + 1);
	}

	/**
	 * S3キーからファイル名部分のみを抽出する。
	 *
	 * @param key S3キー
	 * @return ファイル名部分
	 */
	private String extractFileName(String key) {
		String k = safe(key).trim().replace("\\", "/");
		int idx = k.lastIndexOf('/');
		if (idx < 0) {
			return k;
		}
		return k.substring(idx + 1);
	}

	/**
	 * キー同士の表記揺れ（コロン/ハイフン、ラウンド表記、全角/半角数字、空白）を吸収した
	 * 比較用文字列を作成する。
	 *
	 * @param value 正規化前の値
	 * @return 比較用に正規化された値
	 */
	private String normalizeKeyForCompare(String value) {
		String s = safe(value).trim().replace("\\", "/");
		s = Normalizer.normalize(s, Normalizer.Form.NFKC);
		// 区切り揺れ吸収
		s = s.replaceAll("\\s*:\\s*", "-");
		// ラウンド表記揺れ吸収
		s = s.replaceAll("[ 　]*-[ 　]*ラウンド[ 　]*([0-9０-９]+)", "-ラウンド$1");
		// 全角数字を半角化
		s = toHalfWidthDigits(s);
		// ハイフン前後空白除去
		s = s.replaceAll("\\s*-\\s*", "-");
		// スラッシュ圧縮
		s = s.replaceAll("/+", "/");
		// 連続空白圧縮
		s = s.replaceAll("\\s+", " ").trim();
		return s;
	}

	/**
	 * 削除対象CSVをZIP化して record バケットへバックアップする
	 * - RecordFileOperationService#uploadCsvFilesAsZip を使用
	 * - ローカルCSVを優先し、無ければ S3 から一時取得
	 * - アップロード結果が null または getInfoCd() != 0 の場合は例外を throw する
	 *   （＝バックアップに失敗した場合、呼び出し元 execute() は物理削除へ進まない）
	 *
	 * @param csvIds 削除対象 csvId 一覧
	 * @return アップロードした ZIP の S3キー
	 * @throws Exception バックアップ対象CSVを1件も取得できない場合、またはアップロードに失敗した場合
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

	/**
	 * CSVファイルから seq 列（またはヘッダが無い場合は1列目）の値一覧を抽出する。
	 *
	 * @param csvPath 読み込むCSVファイルのパス
	 * @return 抽出した seq 値の一覧（数値順に正規化済み）
	 * @throws IOException ファイル読み込みに失敗した場合
	 */
	private List<String> extractSeqListFromCsv(Path csvPath) throws IOException {
		List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
		List<String> result = new ArrayList<>();
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
				result.add(raw);
			} catch (NumberFormatException ignore) {
				// seq列に数値以外が入っている行は無視
			}
		}
		return normalizeSeqList(result);
	}

	/**
	 * ヘッダ行の中から "seq" または "id" というカラム名のインデックスを探す。
	 *
	 * @param columns ヘッダ行のカラム一覧
	 * @return 見つかったインデックス。見つからない場合は -1
	 */
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

	/**
	 * 簡易CSVパーサ。ダブルクォート囲み・エスケープ（""）に対応する。
	 *
	 * @param line 1行分のCSV文字列
	 * @return カンマ区切りで分割したカラム一覧
	 */
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

	/**
	 * 値の前後を囲むダブルクォートを除去する。
	 *
	 * @param value 対象の値
	 * @return クォート除去後の値
	 */
	private String stripQuotes(String value) {
		String s = safe(value).trim();
		if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
			return s.substring(1, s.length() - 1);
		}
		return s;
	}

	/**
	 * 文字列先頭のBOM（U+FEFF）を除去する。
	 *
	 * @param value 対象の値
	 * @return BOM除去後の値
	 */
	private String removeBom(String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}
		if (value.charAt(0) == '﻿') {
			return value.substring(1);
		}
		return value;
	}

	/**
	 * snapshot をJSONファイルとして保存する。
	 *
	 * @param snapshotPath 保存先パス
	 * @param snapshot     保存する csvId -> seqList の Map
	 * @throws IOException ファイル書き込みに失敗した場合
	 */
	private void writeSnapshot(
			Path snapshotPath,
			Map<String, List<String>> snapshot) throws IOException {
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
	 * failed 分（物理削除に失敗した csvId）だけ snapshot ファイルに残す。
	 * failed が空であれば snapshot ファイル自体を削除する。
	 *
	 * @param snapshotPath snapshot ファイルパス
	 * @param allSnapshot  今回実行分の全 csvId -> seqList
	 * @param failedCsvIds 物理削除に失敗した csvId 一覧
	 * @throws IOException ファイル操作に失敗した場合
	 */
	private void retainSnapshotForFailed(
			Path snapshotPath,
			Map<String, List<String>> allSnapshot,
			Set<String> failedCsvIds) throws IOException {
		final String METHOD_NAME = "retainSnapshotForFailed";
		Map<String, List<String>> remain = new LinkedHashMap<>();
		if (failedCsvIds != null && !failedCsvIds.isEmpty()) {
			for (String csvId : failedCsvIds) {
				List<String> seqs = normalizeSeqList(allSnapshot.get(csvId));
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
		for (Map.Entry<String, List<String>> e : remain.entrySet()) {
			logWarn(METHOD_NAME, "snapshot残置 csvId=" + e.getKey()
					+ ", seqList=" + e.getValue()
					+ ", groupKey=" + groupKey(e.getValue()));
		}
	}

	/**
	 * S3上のCSVを一時ディレクトリへダウンロードする。
	 *
	 * @param bucket        S3 バケット名
	 * @param s3Key         ダウンロード対象の S3 キー
	 * @param physicalCsvId 一時ファイル名の生成に使う物理CSV名
	 * @return ダウンロードした一時ファイルのパス。失敗時は null
	 * @throws IOException 一時ディレクトリ作成等に失敗した場合
	 */
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
	 * 後方互換のため、旧コロン形式（例: 日本: J1リーグ）も併せて含める。
	 *
	 * @param dto 削除対象の country / league 情報を保持する DTO
	 * @return folder prefix の一覧（重複なし）
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
	 *
	 * @param dto 削除対象の country / league 情報を保持する DTO
	 * @return data_category prefix の一覧（重複なし）
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
	 *
	 * 修正:
	 * - localOnly=true のとき、ローカルCSVが1件も実際に削除できなかった場合は
	 *   成功扱いにしない（従来は無条件で成功扱いにしていた）
	 * - ローカル/S3のどちらにも実体が無いが、deleteSnapshotに正当なseqListが
	 *   残っている場合は「前回実行で既に物理削除済み」とみなして成功扱いにし、
	 *   txt/DBのクリーンアップが進むようにする
	 *
	 * @param csvIds         削除対象 csvId 一覧
	 * @param deleteSnapshot 削除前に確定させた csvId -> seqList snapshot（既に削除済みかの判定に使用）
	 * @return 削除成功/失敗の内訳を保持する DeleteResult
	 * @throws IOException ローカルファイル削除等でI/Oエラーが発生した場合
	 */
	private DeleteResult deletePhysicalCsvFiles(
			List<String> csvIds,
			Map<String, List<String>> deleteSnapshot) throws IOException {
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
			boolean anyLocalDeletedThisId = false;
			boolean anyLocalCandidateExisted = false;
			try {
				Set<String> localCandidates = buildPhysicalCsvIdCandidates(originalCsvId);
				for (String relative : localCandidates) {
					Path localPath = baseDir.resolve(relative).normalize();
					if (Files.exists(localPath)) {
						anyLocalCandidateExisted = true;
					}
					boolean deletedLocal = Files.deleteIfExists(localPath);
					if (deletedLocal) {
						anyLocalDeletedThisId = true;
						result.deletedLocalRelativePaths.add(relative);
						logInfo(METHOD_NAME, "ローカルCSV削除 relative=" + relative + ", path=" + localPath);
					}
				}
				boolean alreadyKnownTarget = deleteSnapshot != null && deleteSnapshot.containsKey(originalCsvId);
				if (!localOnly) {
					// 削除対象キーの解決: allowSingleFileFallback=false
					// (「フォルダにCSVが1件だけ残っていたから対象とみなす」を許すと、
					//  無関係な別のCSVを誤って削除してしまう危険があるため無効化する)
					String resolvedS3Key = findExistingS3CsvKey(
							bucket, prefix, originalCsvId, canonicalCsvId, false);
					if (resolvedS3Key != null && !resolvedS3Key.isBlank()) {
						s3Operator.delete(bucket, resolvedS3Key);
						s3DeleteOk = true;
						logInfo(METHOD_NAME, "S3 CSV削除完了 key=" + resolvedS3Key);
					} else if (!anyLocalCandidateExisted && !anyLocalDeletedThisId && alreadyKnownTarget) {
						// ローカル/S3いずれにも実体が無いが、snapshotに正当なseqListがある
						// = 前回実行で物理削除だけは既に成功していたとみなし、
						//   txt/DBの整理まで進められるようにする
						s3DeleteOk = true;
						logInfo(METHOD_NAME, "CSV実体が既に存在しないため削除済みとみなす csvId=" + originalCsvId);
					} else {
						logWarn(METHOD_NAME, "S3キー未解決のため削除不可 csvId=" + originalCsvId);
					}
				} else {
					// localOnly=true では、実際にローカルファイルを削除できた場合のみ成功とする。
					// 何も削除できなかった場合でも、snapshotに正当なseqListが残っていて
					// かつ削除対象候補がそもそも存在しなかった場合のみ「削除済み」とみなす。
					if (anyLocalDeletedThisId) {
						s3DeleteOk = true;
					} else if (!anyLocalCandidateExisted && alreadyKnownTarget) {
						s3DeleteOk = true;
						logInfo(METHOD_NAME, "localOnly=true: CSV実体が既に存在しないため削除済みとみなす csvId="
								+ originalCsvId);
					} else {
						logWarn(METHOD_NAME, "localOnly=true だが削除対象ローカルCSVが見つかりません csvId="
								+ originalCsvId);
					}
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
	 *
	 * @param deletedPhysicalCsvIds 削除に成功したローカル相対パス一覧
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
	 *
	 * @param deletedOriginalCsvIds     物理削除に成功したオリジナル csvId 一覧
	 * @param deletedCanonicalCsvIds    物理削除に成功した正規化 csvId 一覧
	 * @param deletedLocalRelativePaths 物理削除に成功したローカル相対パス一覧
	 * @throws IOException ファイル読み書きに失敗した場合
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
	 *
	 * @param deletedCsvIds  物理削除に成功した csvId 一覧
	 * @param deleteSnapshot csvId -> seqList の snapshot（削除対象グループの特定に使用）
	 * @throws IOException ファイル読み書きに失敗した場合
	 */
	private void updateSeqList(
			Set<String> deletedCsvIds,
			Map<String, List<String>> deleteSnapshot) throws IOException {
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
		List<List<String>> groups = readSeqListJson(localSeqPath);
		Map<String, List<String>> deleteGroupMap = new LinkedHashMap<>();
		for (String csvId : deletedCsvIds) {
			List<String> seqs = normalizeSeqList(deleteSnapshot.get(csvId));
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
		List<List<String>> newGroups = new ArrayList<>();
		Set<String> removedGroupKeys = new LinkedHashSet<>();
		int removed = 0;
		for (List<String> group : groups) {
			List<String> normalized = normalizeSeqList(group);
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
			for (Map.Entry<String, List<String>> e : deleteGroupMap.entrySet()) {
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
		for (Map.Entry<String, List<String>> e : deleteGroupMap.entrySet()) {
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
	 *
	 * @param path seqList.txt のパス
	 * @return seqグループの一覧
	 * @throws IOException ファイル読み込みに失敗した場合
	 */
	private List<List<String>> readSeqListJson(Path path) throws IOException {
		if (!Files.exists(path)) {
			return new ArrayList<>();
		}
		String raw = Files.readString(path, StandardCharsets.UTF_8).trim();
		if (raw.isEmpty()) {
			return new ArrayList<>();
		}
		if (raw.startsWith("[")) {
			List<List<String>> result = JSON.readValue(raw, new TypeReference<List<List<String>>>() {
			});
			return normalizeGroups(result);
		}
		List<List<String>> result = new ArrayList<>();
		for (String line : raw.split("\n")) {
			String t = safe(line).trim();
			if (t.isEmpty()) {
				continue;
			}
			List<String> group = new ArrayList<>();
			for (String part : t.split(",")) {
				String s = safe(part).trim();
				if (s.isEmpty()) {
					continue;
				}
				try {
					group.add(s);
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

	/**
	 * seqグループ一覧の各グループを正規化する（空グループは除外）。
	 *
	 * @param groups 正規化前のseqグループ一覧
	 * @return 正規化後のseqグループ一覧
	 */
	private List<List<String>> normalizeGroups(List<List<String>> groups) {
		if (groups == null || groups.isEmpty()) {
			return new ArrayList<>();
		}
		List<List<String>> result = new ArrayList<>();
		for (List<String> group : groups) {
			List<String> normalized = normalizeSeqList(group);
			if (!normalized.isEmpty()) {
				result.add(normalized);
			}
		}
		return result;
	}

	/**
	 * seqKey ("ハッシュ-連番" 形式) の連番部分を数値として抽出する。
	 * ExportCsvService#extractSeqNo と同じロジック。
	 * ここでの並び順はグルーピングの一意性そのものには影響しないが、
	 * ログの可読性・保守性のために数値順で正規化する。
	 *
	 * @param seqKey "ハッシュ-連番" 形式の seqKey
	 * @return 連番部分の数値。解析できない場合は Integer.MAX_VALUE
	 */
	private static int extractSeqNo(String seqKey) {
		if (seqKey == null) {
			return Integer.MAX_VALUE;
		}
		int idx = seqKey.lastIndexOf('-');
		String numPart = (idx >= 0) ? seqKey.substring(idx + 1) : seqKey;
		try {
			return Integer.parseInt(numPart.trim());
		} catch (NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
	}

	/**
	 * seqList を重複除去のうえ、seqKey の連番部分に基づいて数値順にソートする。
	 *
	 * @param src ソート前の seqList
	 * @return 重複除去・数値ソート済みの seqList
	 */
	private List<String> normalizeSeqList(List<String> src) {
		if (src == null || src.isEmpty()) {
			return new ArrayList<>();
		}
		return src.stream()
				.filter(Objects::nonNull)
				.distinct()
				.sorted(Comparator.comparingInt(EachCsvTransaction::extractSeqNo)
						.thenComparing(Comparator.naturalOrder()))
				.collect(Collectors.toList());
	}

	/**
	 * フォルダ名セグメントを正規化する（NFKC正規化、コロン->ハイフン統一、
	 * ラウンド表記統一、全角数字の半角化、空白/ハイフンの整形）。
	 *
	 * @param segment 正規化前のセグメント
	 * @return 正規化後のセグメント
	 */
	private String canonicalizeFolderSegment(String segment) {
		String s = Normalizer.normalize(safe(segment), Normalizer.Form.NFKC).trim();
		if (s.isEmpty()) {
			return "";
		}
		// 国: リーグ を 国-リーグ に統一
		s = s.replaceAll("\\s*:\\s*", "-");
		// ラウンド表記を統一
		s = s.replaceAll("[ 　]*-[ 　]*ラウンド[ 　]*([0-9０-９]+)", "-ラウンド$1");
		// 全角数字を半角化
		s = toHalfWidthDigits(s);
		// ハイフン前後空白を除去
		s = s.replaceAll("\\s*-\\s*", "-");
		// 連続ハイフンを圧縮
		s = s.replaceAll("-{2,}", "-");
		// 連続半角空白を圧縮
		s = s.replaceAll(" {2,}", " ").trim();
		return s;
	}

	/**
	 * 正規化済み seqグループに一致する削除対象 csvId を探す。
	 *
	 * @param deleteGroupMap  csvId -> 削除対象seqList の Map
	 * @param normalizedGroup 比較対象の正規化済みseqグループ
	 * @return 一致した csvId。無ければ null
	 */
	private String findMatchedCsvId(Map<String, List<String>> deleteGroupMap, List<String> normalizedGroup) {
		String currentGroupKey = groupKey(normalizedGroup);
		for (Map.Entry<String, List<String>> e : deleteGroupMap.entrySet()) {
			String targetKey = groupKey(e.getValue());
			if (currentGroupKey.equals(targetKey)) {
				return e.getKey();
			}
		}
		return null;
	}

	/**
	 * seqList を一意なグループ識別キー文字列に変換する（正規化 + ハイフン結合）。
	 *
	 * @param ids seqList
	 * @return グループ識別キー文字列
	 */
	private String groupKey(List<String> ids) {
		return normalizeSeqList(ids).stream()
				.map(String::valueOf)
				.collect(Collectors.joining("-"));
	}

	/**
	 * null を空文字に変換するユーティリティ。
	 *
	 * @param s 対象文字列
	 * @return null の場合は空文字、それ以外はそのまま
	 */
	private static String safe(String s) {
		return (s == null) ? "" : s;
	}

	/**
	 * S3 prefix の前後スラッシュを除去する。
	 *
	 * @param prefix 対象の prefix
	 * @return 前後スラッシュを除去した prefix
	 */
	private static String normalizePrefix(String prefix) {
		if (prefix == null) {
			return "";
		}
		String p = prefix.trim();
		p = p.replaceAll("^/+", "");
		p = p.replaceAll("/+$", "");
		return p;
	}

	/**
	 * S3キー先頭のスラッシュを除去する。
	 *
	 * @param key 対象のキー
	 * @return 先頭スラッシュを除去したキー
	 */
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

	/**
	 * prefix とファイル名を "/" で結合してS3キーを組み立てる。
	 *
	 * @param prefix   S3 キー prefix
	 * @param fileName ファイル名
	 * @return 結合後のS3キー
	 */
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

	/**
	 * 全角数字を半角数字に変換する。
	 *
	 * @param in 変換前の文字列
	 * @return 変換後の文字列
	 */
	private static String toHalfWidthDigits(String in) {
		StringBuilder sb = new StringBuilder(in.length());
		for (char ch : in.toCharArray()) {
			if (ch >= '０' && ch <= '９') {
				sb.append((char) ('0' + (ch - '０')));
			} else {
				sb.append(ch);
			}
		}
		return sb.toString();
	}

	/**
	 * INFOレベルのログを出力する。
	 *
	 * @param method  出力元メソッド名
	 * @param message ログメッセージ
	 */
	private void logInfo(String method, String message) {
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, method,
				MessageCdConst.MCD00099I_LOG, message);
	}

	/**
	 * WARNレベルのログを出力する。
	 *
	 * @param method  出力元メソッド名
	 * @param message ログメッセージ
	 */
	private void logWarn(String method, String message) {
		this.manageLoggerComponent.debugWarnLog(
				PROJECT_NAME, CLASS_NAME, method,
				MessageCdConst.MCD00099I_LOG, message);
	}

	/**
	 * 処理終了ログを出力する。
	 *
	 * @param method 出力元メソッド名
	 */
	private void endLog(String method) {
		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, method, "end");
	}

	/**
	 * seq読み取り/バックアップ用に解決したCSV実体の情報を保持する内部クラス。
	 * temporary=true の場合、path は呼び出し元で削除すべき一時ファイルであることを示す。
	 */
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

	/**
	 * deletePhysicalCsvFiles の結果（成功/失敗の内訳）を保持する内部クラス。
	 */
	private static final class DeleteResult {
		private final Set<String> deletedOriginalCsvIds = new LinkedHashSet<>();
		private final Set<String> deletedCanonicalCsvIds = new LinkedHashSet<>();
		private final Set<String> failedOriginalCsvIds = new LinkedHashSet<>();
		private final Set<String> deletedLocalRelativePaths = new LinkedHashSet<>();
	}
}