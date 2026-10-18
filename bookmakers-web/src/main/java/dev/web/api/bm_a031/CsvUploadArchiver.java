package dev.web.api.bm_a031;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.s3.S3Operator;
import dev.web.util.CsvUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * アップロードされた zip を S3（process.s3.buckets.uploadCheck ＝ aws-s3-upload-check-csv）に置く
 *
 * <pre>
 * uploaded/{種類}/{yyyyMMdd}/{状況管理ID}/{zip 名}            バリデーションを通った zip（そのまま）。バッチサービスが取りに来る
 * error/{種類}/{yyyyMMdd}/{状況管理ID}/{名前}_error.zip       バリデーション NG（中は {名前}_error.csv。「エラー詳細」列付き）
 * success/{種類}/{yyyyMMdd}/{状況管理ID}/{zip 名}             バッチサービスで反映できたもの（バッチサービスが置く）
 *   例: uploaded/season/20261018/123/season_20261018.zip
 *       error/season/20261018/124/season_20261018_error.zip
 * </pre>
 * <ul>
 *   <li>日付は日本時間。状況管理 ID のフォルダで分けるので、同じ zip 名でも重ならない。</li>
 *   <li>オブジェクトのメタデータに状況管理 ID・種類・ログインユーザーを入れる。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CsvUploadArchiver {

	public static final String UPLOADED_DIR = "uploaded";
	public static final String ERROR_DIR = "error";
	public static final String SUCCESS_DIR = "success";

	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("uuuuMMdd");

	private final S3Operator s3Operator;

	private final PathConfig pathConfig;

	/** 置いた場所 */
	public static class Stored {
		private final String bucket;
		private final String key;

		public Stored(String bucket, String key) {
			this.bucket = bucket;
			this.key = key;
		}

		public String getBucket() {
			return bucket;
		}

		public String getKey() {
			return key;
		}
	}

	/**
	 * バリデーションを通った zip を uploaded/ に置く
	 * @throws RuntimeException S3 に置けなかったとき（呼び出し側で異常終了にする）
	 */
	public Stored putUploaded(String masterName, long uploadId, String zipName, byte[] zipBytes, String loginUser) {
		String key = buildKey(UPLOADED_DIR, masterName, uploadId, CsvUtil.baseName(zipName));
		return put(key, zipBytes, masterName, uploadId, loginUser);
	}

	/**
	 * エラー内容を書き足した zip（{名前}_error.zip）を error/ に置く
	 * @throws RuntimeException S3 に置けなかったとき
	 */
	public Stored putError(String masterName, long uploadId, CsvUtil.CsvFile errorZip, String loginUser) {
		String key = buildKey(ERROR_DIR, masterName, uploadId, errorZip.getFileName());
		return put(key, errorZip.getBytes(), masterName, uploadId, loginUser);
	}

	private Stored put(String key, byte[] bytes, String masterName, long uploadId, String loginUser) {
		String bucket = pathConfig.getS3BucketsUploadCheck();
		Map<String, String> metadata = new LinkedHashMap<>();
		metadata.put("upload-id", String.valueOf(uploadId));
		metadata.put("master-name", nvl(masterName));
		metadata.put("login-user", asciiOnly(loginUser));

		PutObjectRequest req = PutObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.contentType("application/zip")
				.metadata(metadata)
				.build();
		s3Operator.getS3Client().putObject(req, RequestBody.fromBytes(bytes));
		log.info("アップロードファイルを置きました s3://{}/{}", bucket, key);
		return new Stored(bucket, key);
	}

	/** {dir}/{種類}/{yyyyMMdd}/{状況管理ID}/{ファイル名} */
	static String buildKey(String dir, String masterName, long uploadId, String fileName) {
		return dir + "/" + nvl(masterName) + "/" + LocalDate.now(JST).format(DAY) + "/" + uploadId + "/" + safeFileName(fileName);
	}

	/** S3 のキーで困る文字は _ にする */
	private static String safeFileName(String name) {
		String n = CsvUtil.baseName(name).replaceAll("[\\p{Cntrl}#?%*:|\"<>]", "_");
		return n.isEmpty() ? "upload.zip" : n;
	}

	/** S3 のユーザーメタデータは ASCII だけにする */
	private static String asciiOnly(String s) {
		return nvl(s).replaceAll("[^\\x20-\\x7E]", "_");
	}

	private static String nvl(String s) {
		return s == null ? "" : s;
	}
}