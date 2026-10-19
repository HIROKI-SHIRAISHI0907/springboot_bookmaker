package dev.batch.bm_b015;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.s3.S3Operator;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * アップロードファイルの最終的な置き場所（aws-s3-upload-check-csv）
 *
 * <pre>
 * uploaded/{種類}/{yyyyMMdd}/{ID}/{zip 名}          web が置いたもの（B015 の入力）
 *   ├ 反映できた   → success/{種類}/{yyyyMMdd}/{ID}/{zip 名}        （同じ zip をそのまま）
 *   └ DB チェック NG → error/{種類}/{yyyyMMdd}/{ID}/{名前}_error.zip（「エラー詳細」列を足した CSV）
 * どちらも置けたら uploaded/ の zip を消す。
 * </pre>
 * <ul>
 *   <li>日付・ID のフォルダは uploaded/ のキーをそのまま使う（受付日のまま）。uploaded/ で始まらないときは今日の日付で作る。</li>
 *   <li>移動はコピーではなく、ダウンロード済みの中身をそのまま put する（{@link S3Operator#copy} はキーの URL エンコードで
 *       空白などを含む名前を正しく扱えないため）。</li>
 *   <li>メタデータに状況管理 ID・種類・ログインユーザー（ASCII 以外は _）・結果を入れる。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Component
public class FileUploadArchiveService {

	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("uuuuMMdd");

	@Autowired
	private S3Operator s3Operator;

	/** success/ のキー */
	public String successKey(FileUploadStatusRow row) {
		String rest = restOf(row);
		return FileUploadBatchConst.DIR_SUCCESS + rest;
	}

	/** error/ のキー（ファイル名はエラーファイルの名前にする） */
	public String errorKey(FileUploadStatusRow row, String errorFileName) {
		String rest = restOf(row);
		int slash = rest.lastIndexOf('/');
		String dir = slash >= 0 ? rest.substring(0, slash + 1) : "";
		return FileUploadBatchConst.DIR_ERROR + dir + errorFileName;
	}

	/** zip を置く */
	public void put(String bucket, String key, byte[] bytes, FileUploadStatusRow row, String result) {
		Map<String, String> metadata = new LinkedHashMap<>();
		metadata.put("upload-id", String.valueOf(row.getId()));
		metadata.put("master-name", nvl(row.getMasterName()));
		metadata.put("login-user", asciiOnly(row.getLoginUser()));
		metadata.put("result", result);
		PutObjectRequest req = PutObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.contentType("application/zip")
				.metadata(metadata)
				.build();
		s3Operator.getS3Client().putObject(req, RequestBody.fromBytes(bytes));
	}

	/** uploaded/ の zip を消す */
	public void deleteUploaded(String bucket, String key) {
		s3Operator.delete(bucket, key);
	}

	/** uploaded/ を除いた残り（{種類}/{yyyyMMdd}/{ID}/{zip 名}） */
	private static String restOf(FileUploadStatusRow row) {
		String key = nvl(row.getS3Key());
		if (key.startsWith(FileUploadBatchConst.DIR_UPLOADED)) {
			return key.substring(FileUploadBatchConst.DIR_UPLOADED.length());
		}
		String name = UploadCsvUtil.baseName(key.isEmpty() ? row.getFileName() : key);
		return nvl(row.getMasterName()) + "/" + LocalDate.now(JST).format(DAY) + "/" + row.getId() + "/"
				+ (name.isEmpty() ? "upload.zip" : name);
	}

	private static String asciiOnly(String s) {
		return nvl(s).replaceAll("[^\\x20-\\x7E]", "_");
	}

	private static String nvl(String s) {
		return s == null ? "" : s;
	}
}
