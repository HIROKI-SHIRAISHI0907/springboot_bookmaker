package dev.common.s3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.common.constant.S3Const;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * S3オペレーター
 * @author shiraishitoshio
 */
@Component
public class S3Operator {

	/** 統計CSVパターン */
	private static final Pattern TEAM_SEQ_PATTERN =
		    Pattern.compile("^.*?(\\d+)\\.csv$", Pattern.CASE_INSENSITIVE);

	private final S3Client s3;

	/**
	 * インスタンス生成（東京リージョンで生成）
	 */
	public S3Operator() {
		this.s3 = S3Client.builder()
				.region(Region.of(S3Const.TOKYO_REGION_AP_NORTHEAST_1))
				.build();
	}

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
		if (key == null) return Integer.MAX_VALUE;

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
}
