package dev.common.getinfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.zip.ZipArchiveHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * CSVをmid単位のバックアップzip({@code bucket/mid名.zip})へ追加格納するクラス。
 *
 * <p>
 * 既に {@code bucket/mid名.zip} が存在する場合はダウンロード・解凍してCSVを追加し、
 * 存在しない場合は新規に作成したうえで、同じファイル名で再度zip化してアップロードする。
 * 利用者からは同じzipファイル名のままに見えるが、中身のCSVは増えていく。
 * </p>
 * <p>
 * mid単位で成否を判定し、バックアップ格納に成功したlocalPathと失敗したlocalPathを
 * {@link BackupResult} として返す。削除してよいかどうかの最終判断(全件中止にするか、
 * 失敗分だけ除外するか)は呼び出し元({@code OriginStat})に委ねる。
 * </p>
 */
@Slf4j
@Component
public class PutOriginBackUpInfo {

	private static final String PROJECT_NAME = "BM_B001_BACK_UP";
	private static final String CLASS_NAME = "PutOriginBackUpInfo";

	private static final String ZIP_EXTENSION = ".zip";

	/**
	 * 想定キー(zip内CSVエントリ / originのS3 key):
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
	private ZipArchiveHandler zipArchiveHandler;

	/**
	 * targetLocalPaths を mid 単位でグルーピングし、それぞれ
	 * {@code bucket/mid名.zip} に追加格納する。
	 *
	 * @param entities         localPath -> DataEntity一覧
	 *                         (DataEntity#getFile() に元のS3 key
	 *                         「yyyy-MM-dd/mid=XXXX/seq=...csv」が入っている想定)
	 * @param targetLocalPaths バックアップ対象のローカルCSVパス一覧
	 * @return mid単位での成功/失敗localPath一覧
	 */
	public BackupResult backup(Map<String, List<DataEntity>> entities, List<String> targetLocalPaths) {
		final String METHOD_NAME = "backup";

		List<String> succeeded = new ArrayList<>();
		List<String> failed = new ArrayList<>();

		if (targetLocalPaths == null || targetLocalPaths.isEmpty()) {
			return new BackupResult(succeeded, failed);
		}

		String bucket = config.getS3BucketsOutputsBackUp();

		// mid毎にグルーピング(mid特定不可のものは即失敗扱い)
		Map<String, List<LocalCsv>> midGroups = new LinkedHashMap<>();
		for (String localPath : targetLocalPaths) {
			String s3Key = extractS3Key(entities, localPath);
			String mid = extractMid(s3Key);
			if (mid == null || mid.isEmpty()) {
				manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00003E_EXECUTION_SKIP,
						null,
						"mid特定不可のためバックアップ対象外: localPath=" + localPath + ", s3Key=" + s3Key);
				failed.add(localPath);
				continue;
			}
			midGroups.computeIfAbsent(mid, m -> new ArrayList<>())
					.add(new LocalCsv(localPath, s3Key));
		}

		for (Map.Entry<String, List<LocalCsv>> entry : midGroups.entrySet()) {
			String mid = entry.getKey();
			List<LocalCsv> csvList = entry.getValue();
			try {
				backupOneMid(bucket, mid, csvList);
				for (LocalCsv csv : csvList) {
					succeeded.add(csv.localPath);
				}
				manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						String.format("バックアップ格納成功: mid=%s, csv件数=%d", mid, csvList.size()));
			} catch (Exception e) {
				for (LocalCsv csv : csvList) {
					failed.add(csv.localPath);
				}
				manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00003E_EXECUTION_SKIP,
						e,
						String.format("バックアップ格納失敗: mid=%s, csv件数=%d, zipKey=%s",
								mid, csvList.size(), mid + ZIP_EXTENSION));
			}
		}

		return new BackupResult(succeeded, failed);
	}

	/**
	 * 1mid分の {@code bucket/mid名.zip} を
	 * 「(存在すれば)ダウンロード → 解凍 → CSV追加 → 再zip化 → アップロード」する。
	 * 途中で例外が発生した場合、S3上の既存zipは上書きされない(アップロード自体を呼ばないため)。
	 */
	private void backupOneMid(String bucket, String mid, List<LocalCsv> csvList) throws IOException {
		String zipKey = mid + ZIP_EXTENSION;
		Path workDir = Files.createTempDirectory("backup-" + mid + "-");
		try {
			Path extractDir = workDir.resolve("extract");
			Files.createDirectories(extractDir);

			if (existsZip(bucket, zipKey)) {
				Path downloadedZip = workDir.resolve("download-" + zipKey);
				s3Operator.downloadToFile(bucket, zipKey, downloadedZip);
				zipArchiveHandler.decompress(downloadedZip, extractDir);
			}

			for (LocalCsv csv : csvList) {
				Path source = resolveActualLocalPath(csv);
				Path dest = extractDir.resolve(csv.s3Key);
				if (dest.getParent() != null) {
					Files.createDirectories(dest.getParent());
				}
				Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
			}

			Path newZip = workDir.resolve("new-" + zipKey);
			zipArchiveHandler.compress(extractDir, newZip);

			s3Operator.uploadFile(bucket, zipKey, newZip);
		} finally {
			deleteRecursivelyQuietly(workDir);
		}
	}

	/**
	 * entitiesのキー(localPath)は実際には {@link GetOriginInfo#getData(List)} が返す
	 * S3 keyそのものであり、ローカルディスク上の実ファイルパスではない。
	 * ダウンロード済みの実ファイルは {@link GetOriginInfo#resolveLocalPath(String)} が
	 * 算出する一時フォルダ配下に存在するため、常にそちら経由で実パスを求める。
	 */
	private Path resolveActualLocalPath(LocalCsv csv) {
		return GetOriginInfo.resolveLocalPath(csv.s3Key);
	}

	/**
	 * bucket直下に zipKey が既に存在するか確認する。
	 * 確認自体に失敗した場合は「新規作成」として扱う(ログのみ出力)。
	 */
	private boolean existsZip(String bucket, String zipKey) {
		try {
			List<String> keys = s3Operator.listKeys(bucket, zipKey);
			return keys != null && keys.contains(zipKey);
		} catch (Exception e) {
			log.warn("[B001] zip存在確認に失敗。新規作成として扱います: bucket={}, zipKey={}", bucket, zipKey, e);
			return false;
		}
	}

	private String extractS3Key(Map<String, List<DataEntity>> entities, String localPath) {
		List<DataEntity> list = entities == null ? null : entities.get(localPath);
		if (list == null || list.isEmpty() || list.get(0) == null) {
			return null;
		}
		return list.get(0).getFile();
	}

	private String extractMid(String s3Key) {
		if (s3Key == null) {
			return null;
		}
		Matcher m = KEY_PATTERN.matcher(s3Key);
		if (!m.matches()) {
			return null;
		}
		return m.group(2);
	}

	private void deleteRecursivelyQuietly(Path dir) {
		if (dir == null || !Files.exists(dir)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder())
					.forEach(p -> {
						try {
							Files.deleteIfExists(p);
						} catch (IOException ignore) {
							log.debug("[B001] 一時ファイル削除失敗: {}", p, ignore);
						}
					});
		} catch (IOException e) {
			log.debug("[B001] 一時ディレクトリ削除失敗: {}", dir, e);
		}
	}

	private static class LocalCsv {
		final String localPath;
		final String s3Key;

		LocalCsv(String localPath, String s3Key) {
			this.localPath = localPath;
			this.s3Key = s3Key;
		}
	}

	/**
	 * mid単位のバックアップ格納結果。
	 */
	public static class BackupResult {
		private final List<String> succeededLocalPaths;
		private final List<String> failedLocalPaths;

		public BackupResult(List<String> succeededLocalPaths, List<String> failedLocalPaths) {
			this.succeededLocalPaths = succeededLocalPaths;
			this.failedLocalPaths = failedLocalPaths;
		}

		public List<String> getSucceededLocalPaths() {
			return succeededLocalPaths;
		}

		public List<String> getFailedLocalPaths() {
			return failedLocalPaths;
		}
	}
}