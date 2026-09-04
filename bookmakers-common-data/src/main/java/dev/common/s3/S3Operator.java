package dev.common.s3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * S3オペレーター
 * @author shiraishitoshio
 */
@Component
public class S3Operator {

	/** 統計CSVパターン */
	private static final Pattern TEAM_SEQ_PATTERN = Pattern.compile("^.*?(\\d+)\\.csv$", Pattern.CASE_INSENSITIVE);
	private final S3Client s3;

	/**
	 * S3ClientGetter
	 * @return
	 */
	public S3Client getS3Client() {
		return s3;
	}

	/**
	 * S3Client をDIで受け取る
	 */
	public S3Operator(S3Client s3Client) {
		this.s3 = s3Client;
	}

	/**
	 * ファイルオブジェクトアップロード
	 * @param bucket
	 * @param key
	 * @param file
	 */
	public void uploadFile(String bucket, String key, Path file) {
		PutObjectRequest req = PutObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.build();
		s3.putObject(req, RequestBody.fromFile(file));
	}

	/** ディレクトリ配下のファイルをまとめてアップロード（必要なら） */
	public void uploadDirectory(String bucket, String prefix, Path dir) throws Exception {
		if (!Files.exists(dir))
			return;
		Files.walk(dir)
				.filter(Files::isRegularFile)
				.forEach(p -> {
					String key = prefix + "/" + dir.relativize(p).toString().replace("\\", "/");
					uploadFile(bucket, key, p);
				});
	}

	/**
	 * prefix配下のオブジェクトキー一覧を取得する
	 */
	public List<String> listKeys(String bucket, String prefix) {
		List<String> keys = new ArrayList<>();
		String token = null;
		do {
			ListObjectsV2Request req = ListObjectsV2Request.builder()
					.bucket(bucket)
					.prefix(prefix)
					.continuationToken(token)
					.build();
			ListObjectsV2Response res = s3.listObjectsV2(req);
			for (S3Object obj : res.contents()) {
				keys.add(obj.key());
			}
			token = res.nextContinuationToken();
		} while (token != null);
		return keys;
	}

	/**
	 * 末尾ファイル名を使って取得
	 * @param bucket
	 * @param suffix
	 * @return
	 */
	public List<String> listKeysBySuffix(String bucket, String suffix) {
		List<String> keys = new ArrayList<>();
		String token = null;
		do {
			ListObjectsV2Response res = s3.listObjectsV2(ListObjectsV2Request.builder()
					.bucket(bucket)
					.continuationToken(token)
					.build());
			for (S3Object obj : res.contents()) {
				String key = obj.key();
				if (key != null && key.endsWith(suffix)) {
					keys.add(key);
				}
			}
			token = res.nextContinuationToken();
		} while (token != null);
		return keys;
	}

	/**
	 * S3オブジェクトをInputStreamで取得する
	 */
	public InputStream download(String bucket, String key) {
		GetObjectRequest req = GetObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.build();
		return s3.getObject(req);
	}

	/**
	 * バケット全体から「YYYY-mm-dd/ 配下の .csv」だけを集め、最終更新日時の昇順で返す
	 */
	public List<S3Object> listAllDateCsvObjectsSortedByLastModifiedAsc(String bucket, Pattern matcher) {
		List<S3Object> objects = new ArrayList<>();
		String token = null;
		do {
			ListObjectsV2Request req = ListObjectsV2Request.builder()
					.bucket(bucket)
					.continuationToken(token)
					.build();
			ListObjectsV2Response res = s3.listObjectsV2(req);
			for (S3Object obj : res.contents()) {
				String key = obj.key();
				if (key != null && matcher.matcher(key).matches()) {
					objects.add(obj);
				}
			}
			token = res.nextContinuationToken();
		} while (token != null);
		objects.sort(Comparator.comparing(
				(S3Object o) -> o.lastModified() == null ? Instant.MAX : o.lastModified()));
		return objects;
	}

	/**
	 * 末尾ファイル名が一致するオブジェクトを、メタデータ（size・lastModified等）付きで取得する。
	 * {@link #listKeysBySuffix(String, String)} はキー文字列しか返さないため、
	 * サイズ・最終更新日時が必要な場合はこちらを使う。
	 *
	 * @param bucket バケット名
	 * @param suffix 対象とするキーの末尾（例: ".zip"）
	 * @return 一致したS3Object一覧（順不同、S3のlistObjectsV2の返却順）
	 */
	public List<S3Object> listObjectsBySuffix(String bucket, String suffix) {
		List<S3Object> objects = new ArrayList<>();
		String token = null;
		do {
			ListObjectsV2Request req = ListObjectsV2Request.builder()
					.bucket(bucket)
					.continuationToken(token)
					.build();
			ListObjectsV2Response res = s3.listObjectsV2(req);
			for (S3Object obj : res.contents()) {
				String key = obj.key();
				if (key != null && key.endsWith(suffix)) {
					objects.add(obj);
				}
			}
			token = res.nextContinuationToken();
		} while (token != null);
		return objects;
	}

	/**
	 * 一括削除
	 * @param bucket
	 * @param keys
	 */
	public void deleteObjects(String bucket, List<String> keys) {
		if (keys == null || keys.isEmpty()) {
			return;
		}
		List<ObjectIdentifier> objects = keys.stream()
				.map(key -> ObjectIdentifier.builder().key(key).build())
				.collect(Collectors.toList());
		Delete delete = Delete.builder()
				.objects(objects)
				.quiet(false)
				.build();
		DeleteObjectsRequest request = DeleteObjectsRequest.builder()
				.bucket(bucket)
				.delete(delete)
				.build();
		DeleteObjectsResponse response = getS3Client().deleteObjects(request);
		if (response.hasErrors() && !response.errors().isEmpty()) {
			StringBuilder sb = new StringBuilder("S3一括削除で一部失敗: ");
			for (S3Error error : response.errors()) {
				sb.append("[key=").append(error.key())
						.append(", code=").append(error.code())
						.append(", message=").append(error.message())
						.append("] ");
			}
			throw new RuntimeException(sb.toString());
		}
	}

	/**
	 * 連番リスト取得（統計用）
	 * @param bucket
	 * @return
	 */
	public List<String> listSeqCsvKeysInRoot(String bucket, Pattern matcher) {
		List<String> keys = new ArrayList<>();
		String token = null;
		do {
			ListObjectsV2Request req = ListObjectsV2Request.builder()
					.bucket(bucket)
					.continuationToken(token)
					.build();
			ListObjectsV2Response res = s3.listObjectsV2(req);
			for (S3Object obj : res.contents()) {
				String key = obj.key();
				if (key != null && matcher.matcher(key).matches()) {
					keys.add(key);
				}
			}
			token = res.nextContinuationToken();
		} while (token != null);
		keys.sort(Comparator.comparingInt(k -> extractTeamSeq(k, matcher)));
		return keys;
	}

	/**
	 * チームデータ用連番ソート
	 * @param bucket
	 * @param matcher
	 * @return
	 */
	public List<String> listTeamDataKeysSortedBySeqAsc(String bucket, Pattern matcher) {
		List<String> keys = new ArrayList<>();
		String token = null;
		do {
			ListObjectsV2Request req = ListObjectsV2Request.builder()
					.bucket(bucket)
					.continuationToken(token)
					.build();
			ListObjectsV2Response res = s3.listObjectsV2(req);
			for (S3Object obj : res.contents()) {
				String key = obj.key();
				if (key != null && matcher.matcher(key).matches()) {
					keys.add(key);
				}
			}
			token = res.nextContinuationToken();
		} while (token != null);
		// ✅ 連番(グループ1)の数値で昇順ソート
		keys.sort(
				Comparator.comparingInt((String k) -> extractTeamSeq(k, matcher))
						.thenComparing(Comparator.naturalOrder()));
		return keys;
	}

	/**
	 * S3のテキストファイルをUTF-8で文字列として読む
	 */
	public String downloadTextUtf8(String bucket, String key) {
		try (InputStream in = download(bucket, key);
				BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			return br.lines().collect(Collectors.joining("\n"));
		} catch (Exception e) {
			throw new RuntimeException("S3 text download failed. bucket=" + bucket + ", key=" + key, e);
		}
	}

	/**
	 * S3オブジェクトをローカルファイルに保存して Path を返す
	 * - 親ディレクトリは自動作成
	 * - 既存ファイルは上書き
	 */
	public Path downloadToFile(String bucket, String key, Path out) throws IOException {
		if (out.getParent() != null) {
			Files.createDirectories(out.getParent());
		}
		try (InputStream in = download(bucket, key);
				OutputStream os = Files.newOutputStream(out,
						StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING,
						StandardOpenOption.WRITE)) {
			in.transferTo(os);
		}
		return out;
	}

	/**
	 * コピー
	 * @param srcBucket
	 * @param srcKey
	 * @param dstBucket
	 * @param dstKey
	 */
	@SuppressWarnings("deprecation")
	public void copy(String srcBucket, String srcKey, String dstBucket, String dstKey) {
		String copySource = URLEncoder.encode(srcBucket + "/" + srcKey, StandardCharsets.UTF_8);
		CopyObjectRequest req = CopyObjectRequest.builder()
				.copySource(copySource) // コピー元（bucket/key）
				.bucket(dstBucket) // コピー先バケット
				.key(dstKey) // コピー先キー
				.build();
		s3.copyObject(req);
	}

	/**
	 * 削除
	 * @param bucket
	 * @param key
	 */
	public void delete(String bucket, String key) {
		s3.deleteObject(b -> b.bucket(bucket).key(key));
	}

	/**
	 * statsバケット直下（or prefix配下）の特定ファイルを読む用：prefixを安全に連結してkey化する
	 * prefixは "" でもOK。 "stats" でも "stats/" でもOK。
	 */
	public String buildKey(String prefix, String filename) {
		if (prefix == null || prefix.isBlank()) {
			return filename;
		}
		String p = prefix.endsWith("/") ? prefix : prefix + "/";
		return p + filename;
	}

	/**
	 * 正規表現
	 * @param key
	 * @param matcher
	 * @return
	 */
	private static int extractTeamSeq(String key, Pattern matcher) {
		if (key == null)
			return Integer.MAX_VALUE;
		Matcher m = TEAM_SEQ_PATTERN.matcher(key);
		if (!m.find()) {
			// CSV以外（seqList.txt など）は末尾へ
			return Integer.MAX_VALUE;
		}
		try {
			return Integer.parseInt(m.group(1));
		} catch (NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
	}

	/**
	 * 指定prefix配下のキーを走査し、patternに一致する連番の最大値 + 1 を返す
	 *
	 * 例:
	 * - keyPrefix: "fin/b008_fin_getting_data_"
	 * - pattern  : ^fin/b008_fin_getting_data_(\d+)\.json$
	 *
	 * 戻り値:
	 * - 既存なし -> 1
	 * - 最大が 7 -> 8
	 */
	public int findNextSequenceNumber(String bucket, String keyPrefix, Pattern pattern) {
		int max = 0;
		String token = null;
		do {
			ListObjectsV2Request req = ListObjectsV2Request.builder()
					.bucket(bucket)
					.prefix(keyPrefix)
					.continuationToken(token)
					.build();
			ListObjectsV2Response res = s3.listObjectsV2(req);
			for (S3Object obj : res.contents()) {
				String key = obj.key();
				if (key == null) {
					continue;
				}
				Matcher matcher = pattern.matcher(key);
				if (!matcher.matches()) {
					continue;
				}
				try {
					int seq = Integer.parseInt(matcher.group(1));
					if (seq > max) {
						max = seq;
					}
				} catch (NumberFormatException e) {
					// 想定外は無視
				}
			}
			token = res.nextContinuationToken();
		} while (token != null);
		return max + 1;
	}

	/**
	 * 指定オブジェクトがS3上に存在するかどうかを判定する。
	 *
	 * @param bucket バケット名
	 * @param key    オブジェクトキー
	 * @return 存在すればtrue、存在しなければfalse
	 */
	public boolean existsOnS3(String bucket, String key) {
		try {
			s3.headObject(HeadObjectRequest.builder()
					.bucket(bucket)
					.key(key)
					.build());
			return true;
		} catch (NoSuchKeyException e) {
			return false;
		}
	}

	/**
	 * 特定のバケットへJSONを格納する(無条件の上書き)。
	 * 同時実行の競合を考慮する必要がある場合は {@link #putTextConditional(String, String, String, String)}
	 * を使うこと。
	 * @param bucket
	 * @param jsonFileName
	 * @param updatedJson
	 */
	public void putJson(String bucket, String jsonFileName, String updatedJson) {
		PutObjectRequest putReq = PutObjectRequest.builder()
				.bucket(bucket)
				.key(jsonFileName)
				.contentType("application/json; charset=UTF-8")
				.build();
		s3.putObject(putReq, RequestBody.fromString(updatedJson, StandardCharsets.UTF_8));
	}

	/**
	 * S3オブジェクトをUTF-8テキストとして取得すると同時に、そのETagも取得する。
	 * 楽観的排他制御(条件付きPUT)のために、読み取り時点のETagを保持しておきたい場合に使う。
	 *
	 * @param bucket バケット名
	 * @param key    オブジェクトキー
	 * @return 存在する場合はテキストとETagを格納したOptional、存在しない場合はOptional.empty()
	 */
	public Optional<TextWithETag> downloadTextUtf8WithETag(String bucket, String key) {
		GetObjectRequest req = GetObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.build();
		try (ResponseInputStream<GetObjectResponse> in = s3.getObject(req);
				BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String etag = in.response().eTag();
			String text = br.lines().collect(Collectors.joining("\n"));
			return Optional.of(new TextWithETag(text, etag));
		} catch (NoSuchKeyException e) {
			return Optional.empty();
		} catch (IOException e) {
			throw new RuntimeException("S3 text download failed. bucket=" + bucket + ", key=" + key, e);
		}
	}

	/**
	 * If-Match(またはIf-None-Match)による条件付き書き込み。
	 *
	 * <p>expectedETagが指定されている場合、S3上の現在のETagがexpectedETagと一致するときのみ
	 * 書き込みを行う(If-Match)。他プロセスが読み取り後に先に書き込んでETagが変わっていた場合は
	 * 412 Precondition Failed として{@link S3Exception}がスローされる。
	 *
	 * <p>expectedETagがnullの場合は「まだオブジェクトが存在しないはず」という前提で
	 * If-None-Match: * を付与する。もし他プロセスが先に同じキーへ新規作成していた場合も
	 * 同様に412が返る。
	 *
	 * <p><b>注意:</b> If-Match/If-None-Matchを使った条件付きPUTはS3の比較的新しい機能
	 * (Conditional Writes)であり、AWS SDK for Java v2 側もこれに対応したバージョン
	 * (2.26系以降が目安)である必要がある。{@code PutObjectRequest.Builder} に
	 * {@code ifMatch}/{@code ifNoneMatch} メソッドが無い場合はSDKのバージョンアップが必要。
	 *
	 * @param bucket       バケット名
	 * @param key          オブジェクトキー
	 * @param content      書き込む内容
	 * @param expectedETag 読み取り時点のETag。nullの場合は新規作成(If-None-Match: *)として扱う
	 * @throws S3Exception ETagが一致しない場合(412 Precondition Failed)や、その他のS3エラー
	 */
	public void putTextConditional(String bucket, String key, String content, String expectedETag) {
		PutObjectRequest.Builder builder = PutObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.contentType("application/json; charset=UTF-8");
		if (expectedETag != null) {
			builder.ifMatch(expectedETag);
		} else {
			builder.ifNoneMatch("*");
		}
		s3.putObject(builder.build(), RequestBody.fromString(content, StandardCharsets.UTF_8));
	}

	/**
	 * 例外がS3の条件付き書き込み失敗(412 Precondition Failed)によるものかどうかを判定する。
	 * {@link #putTextConditional(String, String, String, String)} のIf-Match/If-None-Match
	 * 不一致時にこの例外を判別し、リトライすべきかどうかの判断に使う。
	 *
	 * @param e 判定対象の例外
	 * @return 412 Precondition Failedであればtrue
	 */
	public boolean isPreconditionFailed(Exception e) {
		if (e instanceof S3Exception) {
			return ((S3Exception) e).statusCode() == 412;
		}
		return false;
	}

	/**
	 * S3オブジェクトのテキスト内容とETagを保持する単純な値クラス。
	 */
	public static final class TextWithETag {
		private final String text;
		private final String eTag;

		public TextWithETag(String text, String eTag) {
			this.text = text;
			this.eTag = eTag;
		}

		public String getText() {
			return text;
		}

		public String getETag() {
			return eTag;
		}
	}
}