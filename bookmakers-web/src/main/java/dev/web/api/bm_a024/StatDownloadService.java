package dev.web.api.bm_a024;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import dev.common.config.PathConfig;
import dev.common.s3.S3Operator;

/**
 * 過去の統計情報のダウンロードサービスクラス
 * @author shiraishitoshio
 *
 */
@Service
public class StatDownloadService {

	private static final String DEFAULT_FOLDER = "EachCsvTransaction";
	private static final MediaType ZIP_MEDIA_TYPE = MediaType.parseMediaType("application/zip");
	private static final DateTimeFormatter FILE_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private final PathConfig config;
	private final S3Operator s3Operator;

	public StatDownloadService(
			PathConfig config,
			S3Operator s3Operator) {
		this.config = config;
		this.s3Operator = s3Operator;
	}

	/**
	 * 指定国リーグ名に一致するCSVを、対象フォルダ配下の全ZIPから抽出して
	 * 再ZIP化して返却する
	 * @param req
	 * @return
	 */
	public ResponseEntity<byte[]> downloadFilteredZip(StatDownloadRequest req) {
		validateRequest(req);

		String folder = isBlank(req.getFolder()) ? DEFAULT_FOLDER : req.getFolder().trim();
		String countryLeagueName = req.getCountryLeagueName().trim();

		String sourceBucket = config.getS3Record();
		List<String> sourceZipKeys = findTargetZipKeys(sourceBucket, folder);

		byte[] filteredZipBytes = buildFilteredZipFromAllZips(sourceBucket, sourceZipKeys, countryLeagueName);

		String downloadFileName = buildDownloadFileName(countryLeagueName);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(ZIP_MEDIA_TYPE);
		headers.setContentLength(filteredZipBytes.length);
		headers.setContentDisposition(
				ContentDisposition.attachment()
						.filename(downloadFileName, StandardCharsets.UTF_8)
						.build());

		return ResponseEntity.ok()
				.headers(headers)
				.body(filteredZipBytes);
	}

	/**
	 * 対象フォルダ配下のZIP一覧を取得する
	 * @param bucket
	 * @param folder
	 * @return
	 */
	private List<String> findTargetZipKeys(String bucket, String folder) {
		String prefix = normalizeFolderPrefix(folder);

		List<String> keys = s3Operator.listKeys(bucket, prefix);
		List<String> zipKeys = new ArrayList<>();

		if (keys != null) {
			for (String key : keys) {
				if (key == null || key.isBlank()) {
					continue;
				}
				if (key.toLowerCase(Locale.ROOT).endsWith(".zip")) {
					zipKeys.add(key);
				}
			}
		}

		if (zipKeys.isEmpty()) {
			throw new ResponseStatusException(
					HttpStatus.NOT_FOUND,
					"対象フォルダ配下にZIPファイルが存在しません。 folder=" + folder);
		}

		zipKeys.sort(String::compareTo);
		return zipKeys;
	}

	/**
	 * 複数ZIPから対象CSVを抽出し、再ZIP化する
	 * @param bucket
	 * @param zipKeys
	 * @param countryLeagueName
	 * @return
	 */
	private byte[] buildFilteredZipFromAllZips(
			String bucket,
			List<String> zipKeys,
			String countryLeagueName) {

		String normalizedTarget = normalizeCountryLeague(countryLeagueName);

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
			 ZipOutputStream zos = new ZipOutputStream(baos)) {

			int copiedCount = 0;
			Set<String> usedEntryNames = new HashSet<>();

			for (String zipKey : zipKeys) {
				try (InputStream s3In = s3Operator.download(bucket, zipKey);
					 ZipInputStream zis = new ZipInputStream(s3In)) {

					ZipEntry entry;

					while ((entry = zis.getNextEntry()) != null) {
						if (entry.isDirectory()) {
							zis.closeEntry();
							continue;
						}

						String entryName = entry.getName();
						String fileName = extractFileName(entryName);

						if (!fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
							zis.closeEntry();
							continue;
						}

						if (!matchesTargetCountryLeague(entryName, fileName, normalizedTarget)) {
							zis.closeEntry();
							continue;
						}

						String outputEntryName = createUniqueEntryName(fileName, usedEntryNames);

						zos.putNextEntry(new ZipEntry(outputEntryName));
						copy(zis, zos);
						zos.closeEntry();

						copiedCount++;
						zis.closeEntry();
					}

				} catch (Exception e) {
					// 個別ZIPの破損や一時的な取得失敗があっても、
					// 他ZIPから回収できるように継続
				}
			}

			zos.finish();

			if (copiedCount == 0) {
				throw new ResponseStatusException(
						HttpStatus.NOT_FOUND,
						"指定した国リーグ名に一致するCSVが対象ZIP群内に存在しません。");
			}

			return baos.toByteArray();

		} catch (ResponseStatusException e) {
			throw e;
		} catch (Exception e) {
			throw new ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"ZIPの抽出・再作成に失敗しました。", e);
		}
	}

	/**
	 * 対象国リーグ名に一致するCSVか判定
	 * 例:
	 * サンプル国_サンプルリーグ-ラウンド15_1.csv
	 * サンプル国_サンプルリーグ-ラウンド16_1.csv
	 * @param entryName
	 * @param fileName
	 * @param normalizedTarget
	 * @return
	 */
	private boolean matchesTargetCountryLeague(
			String entryName,
			String fileName,
			String normalizedTarget) {

		String normalizedEntryName = normalizeForCompare(entryName);
		String normalizedFileName = normalizeForCompare(fileName);

		if (normalizedFileName.startsWith(normalizedTarget + "-ラウンド")) {
			return true;
		}
		if (normalizedFileName.startsWith(normalizedTarget + "_ラウンド")) {
			return true;
		}

		if (normalizedEntryName.contains("/" + normalizedTarget + "-ラウンド")) {
			return true;
		}
		if (normalizedEntryName.contains("/" + normalizedTarget + "_ラウンド")) {
			return true;
		}

		return false;
	}

	/**
	 * 入力値の国リーグ名をZIP内比較用に正規化
	 * @param raw
	 * @return
	 */
	private String normalizeCountryLeague(String raw) {
		String s = normalizeForCompare(raw);
		s = s.replace(":", "_");
		s = s.replace("：", "_");
		return s;
	}

	/**
	 * 比較用正規化
	 * @param raw
	 * @return
	 */
	private String normalizeForCompare(String raw) {
		String s = raw == null ? "" : raw;
		s = Normalizer.normalize(s, Normalizer.Form.NFKC);
		s = s.trim();
		s = s.replace("\\", "/");
		s = s.replaceAll("[ 　]+", "");
		return s.toLowerCase(Locale.ROOT);
	}

	/**
	 * パスからファイル名抽出
	 * @param path
	 * @return
	 */
	private String extractFileName(String path) {
		if (path == null || path.isBlank()) {
			return "";
		}
		String normalized = path.replace("\\", "/");
		int idx = normalized.lastIndexOf('/');
		return (idx >= 0) ? normalized.substring(idx + 1) : normalized;
	}

	/**
	 * ZIP内ファイル名重複回避
	 * @param fileName
	 * @param usedEntryNames
	 * @return
	 */
	private String createUniqueEntryName(String fileName, Set<String> usedEntryNames) {
		if (usedEntryNames.add(fileName)) {
			return fileName;
		}

		int dot = fileName.lastIndexOf('.');
		String base = dot >= 0 ? fileName.substring(0, dot) : fileName;
		String ext = dot >= 0 ? fileName.substring(dot) : "";

		int seq = 2;
		while (true) {
			String candidate = base + "_" + seq + ext;
			if (usedEntryNames.add(candidate)) {
				return candidate;
			}
			seq++;
		}
	}

	/**
	 * ダウンロードZIPファイル名生成
	 * @param countryLeagueName
	 * @return
	 */
	private String buildDownloadFileName(String countryLeagueName) {
		String league = sanitizeFileName(normalizeCountryLeague(countryLeagueName));
		String ts = LocalDateTime.now().format(FILE_TS_FORMAT);
		return "stat_download_" + league + "_" + ts + ".zip";
	}

	/**
	 * ファイル名危険文字除去
	 * @param raw
	 * @return
	 */
	private String sanitizeFileName(String raw) {
		String s = raw == null ? "" : raw;
		s = Normalizer.normalize(s, Normalizer.Form.NFKC);
		s = s.replaceAll("[\\\\/:*?\"<>|]", "_");
		s = s.replaceAll("\\s+", "");
		if (s.isBlank()) {
			return "download";
		}
		return s;
	}

	/**
	 * ZIPストリームコピー
	 * @param in
	 * @param out
	 * @throws IOException
	 */
	private void copy(ZipInputStream in, ZipOutputStream out) throws IOException {
		byte[] buffer = new byte[8192];
		int len;
		while ((len = in.read(buffer)) != -1) {
			out.write(buffer, 0, len);
		}
	}

	/**
	 * 入力チェック
	 * @param req
	 */
	private void validateRequest(StatDownloadRequest req) {
		if (req == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "リクエストが未指定です。");
		}
		if (isBlank(req.getCountryLeagueName())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "countryLeagueName は必須です。");
		}
	}

	/**
	 * フォルダprefix正規化
	 * @param folder
	 * @return
	 */
	private String normalizeFolderPrefix(String folder) {
		String s = folder == null ? "" : folder.trim();
		s = s.replaceAll("^/+", "");
		if (!s.isEmpty() && !s.endsWith("/")) {
			s = s + "/";
		}
		return s;
	}

	/**
	 * 空判定
	 * @param s
	 * @return
	 */
	private boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}
}
