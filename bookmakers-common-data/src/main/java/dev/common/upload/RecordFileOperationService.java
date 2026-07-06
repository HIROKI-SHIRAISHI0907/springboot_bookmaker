package dev.common.upload;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;

/**
 * 記録データS3ファイル操作サービス
 * @author shiraishitoshio
 *
 */
@Service
public class RecordFileOperationService {

	private static final String PROJECT_NAME = RecordFileOperationService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	private static final String CLASS_NAME = RecordFileOperationService.class.getName();

	/** Config */
	@Autowired
	private PathConfig config;

	/** S3操作 */
	@Autowired
	private S3Operator s3Operator;

	/** ログ管理 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 複数のCSVファイルをZIP化してS3へアップロードする
	 * @param folder S3フォルダ名（prefix）
	 * @param zipFileName ZIPファイル名（例: records.zip）
	 * @param csvFiles ZIP対象のCSVファイル一覧
	 * @param workDir ZIP作成用の作業ディレクトリ
	 * @return 処理結果
	 */
	public RecordFileOperationOutputDTO uploadCsvFilesAsZip(
			String folder,
			String zipFileName,
			List<Path> csvFiles,
			Path workDir) {

		final String METHOD_NAME = "uploadCsvFilesAsZip";

		try {
			if (csvFiles == null || csvFiles.isEmpty()) {
				logWarn(METHOD_NAME, "ZIP対象のCSVファイルが未指定です。");
				return new RecordFileOperationOutputDTO(9, "ZIPアップロードに失敗しました。");
			}

			if (workDir == null) {
				logWarn(METHOD_NAME, "ZIP作成用ディレクトリが未指定です。");
				return new RecordFileOperationOutputDTO(9, "ZIPアップロードに失敗しました。");
			}

			Files.createDirectories(workDir);

			String safeZipFileName = Path.of(zipFileName).getFileName().toString();
			if (safeZipFileName.isBlank()) {
				logWarn(METHOD_NAME, "ZIPファイル名が未指定です。");
				return new RecordFileOperationOutputDTO(9, "ZIPアップロードに失敗しました。");
			}
			if (!safeZipFileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
				safeZipFileName = safeZipFileName + ".zip";
			}

			for (Path csvFile : csvFiles) {
				if (csvFile == null) {
					logWarn(METHOD_NAME, "CSVファイル一覧に null が含まれています。");
					return new RecordFileOperationOutputDTO(9, "ZIPアップロードに失敗しました。");
				}
				if (!Files.exists(csvFile) || !Files.isRegularFile(csvFile)) {
					logWarn(METHOD_NAME, "CSVファイルが存在しません: " + csvFile);
					return new RecordFileOperationOutputDTO(9, "ZIPアップロードに失敗しました。");
				}
				String fileName = csvFile.getFileName().toString();
				if (!fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
					logWarn(METHOD_NAME, "CSV以外のファイルが含まれています: " + csvFile);
					return new RecordFileOperationOutputDTO(9, "ZIPアップロードに失敗しました。");
				}
			}

			Path zipFile = workDir.resolve(safeZipFileName);

			createZipFromCsvFiles(csvFiles, zipFile);

			String s3Key = s3Operator.buildKey(folder, safeZipFileName);
			s3Operator.uploadFile(config.getS3Record(), s3Key, zipFile);

			logInfo(METHOD_NAME,
					"CSV ZIPアップロード成功: bucket=" + config.getS3Record()
					+ ", key=" + s3Key
					+ ", zipFile=" + zipFile
					+ ", csvCount=" + csvFiles.size());

			return new RecordFileOperationOutputDTO(0, "ZIPアップロードに成功しました。");

		} catch (Exception e) {
			logError(METHOD_NAME,
					"CSV ZIPアップロードエラー: （バケット: " + config.getS3Record()
					+ ", フォルダ: " + folder
					+ ", zipFileName: " + zipFileName
					+ ", workDir: " + workDir + "）",
					e);
			return new RecordFileOperationOutputDTO(9, "ZIPアップロードに失敗しました。");
		}
	}

	/**
	 * 複数CSVファイルをZIP化する
	 * @param csvFiles CSVファイル一覧
	 * @param zipFile 出力ZIPファイル
	 * @throws IOException
	 */
	private void createZipFromCsvFiles(List<Path> csvFiles, Path zipFile) throws IOException {
		if (zipFile.getParent() != null) {
			Files.createDirectories(zipFile.getParent());
		}

		Map<String, Integer> entryNameCounter = new HashMap<>();

		try (ZipOutputStream zos = new ZipOutputStream(
				Files.newOutputStream(
						zipFile,
						StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING,
						StandardOpenOption.WRITE))) {

			byte[] buffer = new byte[8192];

			for (Path csvFile : csvFiles) {
				String originalEntryName = csvFile.getFileName().toString();
				String zipEntryName = createUniqueZipEntryName(originalEntryName, entryNameCounter);

				ZipEntry entry = new ZipEntry(zipEntryName);
				zos.putNextEntry(entry);

				try (InputStream is = new BufferedInputStream(Files.newInputStream(csvFile))) {
					int len;
					while ((len = is.read(buffer)) != -1) {
						zos.write(buffer, 0, len);
					}
				}

				zos.closeEntry();
			}
		}
	}

	/**
	 * ZIP内の重複ファイル名を回避する
	 * 例:
	 * sample.csv
	 * sample_2.csv
	 * sample_3.csv
	 * @param originalName 元ファイル名
	 * @param counter 管理用カウンタ
	 * @return ZIPエントリ名
	 */
	private String createUniqueZipEntryName(String originalName, Map<String, Integer> counter) {
		int current = counter.getOrDefault(originalName, 0) + 1;
		counter.put(originalName, current);

		if (current == 1) {
			return originalName;
		}

		int dotIndex = originalName.lastIndexOf('.');
		if (dotIndex < 0) {
			return originalName + "_" + current;
		}

		String base = originalName.substring(0, dotIndex);
		String ext = originalName.substring(dotIndex);
		return base + "_" + current + ext;
	}

	/**
	 * 記録データアップロード処理
	 * @param s3Bucket バケット名
	 * @param folder フォルダ名
	 * @param fillChar 備考
	 */
	public RecordFileOperationOutputDTO upload(String folder, Path file, String fillChar) {
		final String METHOD_NAME = "upload";
		try {
			s3Operator.uploadFile(config.getS3Record(), folder, file);
		} catch (Exception e) {
			logError(METHOD_NAME, "アップロードエラー: （バケット: " + config.getS3Record() + ", フォルダ: " + folder + ", file: " + file,
					e);
			return new RecordFileOperationOutputDTO(9, "アップロードに失敗しました。");
		}
		return new RecordFileOperationOutputDTO(0, "アップロードに成功しました。");
	}

	/**
	 * 指定した ZIP ファイルを S3 からダウンロードする
	 * @param folder S3フォルダ名（prefix）
	 * @param fileName ファイル名（例: sample.zip）
	 * @param outDir 保存先ディレクトリ
	 * @return 0:正常 / 9:異常
	 */
	public RecordFileOperationOutputDTO downloadZip(String folder, String fileName, Path outDir) {
		return downloadFile("downloadZip", folder, fileName, outDir, ".zip");
	}

	/**
	 * 指定した CSV ファイルを S3 からダウンロードする
	 * @param folder S3フォルダ名（prefix）
	 * @param fileName ファイル名（例: sample.csv）
	 * @param outDir 保存先ディレクトリ
	 * @return 0:正常 / 9:異常
	 */
	public RecordFileOperationOutputDTO downloadCsv(String folder, String fileName, Path outDir) {
		return downloadFile("downloadCsv", folder, fileName, outDir, ".csv");
	}

	/**
	 * 指定ファイルを S3 からダウンロードする共通処理
	 * @param method メソッド
	 * @param folder S3フォルダ名（prefix）
	 * @param fileName ファイル名
	 * @param outDir 保存先ディレクトリ
	 * @param expectedExtension 想定拡張子（.zip / .csv）
	 * @return 0:正常 / 9:異常
	 */
	private RecordFileOperationOutputDTO downloadFile(String method, String folder, String fileName, Path outDir, String expectedExtension) {
		try {
			if (fileName == null || fileName.isBlank()) {
				logWarn(method, "ダウンロード対象ファイル名が未指定です。");
				return new RecordFileOperationOutputDTO(9, "ダウンロードに失敗しました。");
			}

			if (outDir == null) {
				logWarn(method, "保存先ディレクトリが未指定です。");
				return new RecordFileOperationOutputDTO(9, "ダウンロードに失敗しました。");
			}

			String safeFileName = Path.of(fileName).getFileName().toString();

			if (!safeFileName.toLowerCase(Locale.ROOT).endsWith(expectedExtension)) {
				logWarn(method,
						"拡張子不正: fileName=" + safeFileName + ", expected=" + expectedExtension);
				return new RecordFileOperationOutputDTO(9, "ダウンロードに失敗しました。");
			}

			Files.createDirectories(outDir);

			String s3Key = s3Operator.buildKey(folder, safeFileName);
			Path outFile = outDir.resolve(safeFileName);

			s3Operator.downloadToFile(config.getS3Record(), s3Key, outFile);

			logInfo(method,
					"ダウンロード成功: bucket=" + config.getS3Record()
					+ ", key=" + s3Key
					+ ", outFile=" + outFile);

			return new RecordFileOperationOutputDTO(0, "ダウンロードに成功しました。");

		} catch (IOException e) {
			logError(method,
					"ローカル保存先作成エラー: （バケット: " + config.getS3Record()
					+ ", フォルダ: " + folder
					+ ", fileName: " + fileName
					+ ", outDir: " + outDir + "）",
					e);
			return new RecordFileOperationOutputDTO(9, "ダウンロードに失敗しました。");

		} catch (Exception e) {
			logError(method,
					"ダウンロードエラー: （バケット: " + config.getS3Record()
					+ ", フォルダ: " + folder
					+ ", fileName: " + fileName
					+ ", outDir: " + outDir + "）",
					e);
			return new RecordFileOperationOutputDTO(9, "ダウンロードに失敗しました。");
		}
	}

	/**
	 * 正常ログ
	 * @param method
	 * @param message
	 */
	private void logInfo(String method, String message) {
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, method,
				MessageCdConst.MCD00099I_LOG, message);
	}

	/**
	 * 警告ログ
	 * @param method
	 * @param message
	 */
	private void logWarn(String method, String message) {
		this.manageLoggerComponent.debugWarnLog(
				PROJECT_NAME, CLASS_NAME, method,
				MessageCdConst.MCD00099I_LOG, message);
	}

	/**
	 * エラーログ
	 * @param method
	 * @param message
	 * @param e
	 */
	private void logError(String method, String message, Exception e) {
		this.manageLoggerComponent.debugErrorLog(
				PROJECT_NAME, CLASS_NAME, method,
				MessageCdConst.MCD00099I_LOG, e, message);
	}

}
