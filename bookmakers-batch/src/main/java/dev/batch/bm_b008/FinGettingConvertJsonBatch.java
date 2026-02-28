package dev.batch.bm_b008;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.batch.common.AbstractJobBatchTemplate;
import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.s3.S3Operator;

/**
 * JSON変換用
 * @author shiraishitoshio
 *
 */
@Service("B008")
public class FinGettingConvertJsonBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = FinGettingConvertJsonBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = FinGettingConvertJsonBatch.class.getName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B008_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B008";

	/** オーバーライド */
	@Override
	protected String batchCode() {
		return BATCH_CODE;
	}

	@Override
	protected String errorCode() {
		return ERROR_CODE;
	}

	@Override
	protected String projectName() {
		return PROJECT_NAME;
	}

	@Override
	protected String className() {
		return CLASS_NAME;
	}

	/** JSON 生成に利用する ObjectMapper。 */
	@Autowired
	private ObjectMapper objectMapper;

	/** パスや外部実行設定（Python/S3等）を保持する設定クラス。 */
	@Autowired
	private PathConfig pathConfig;

	/** S3Operator。 */
	@Autowired
	private S3Operator s3Operator;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doExecute(JobContext ctx) throws Exception {
	    final String METHOD_NAME = "doExecute";

	    this.manageLoggerComponent.init(null, null);
	    this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

	    debugJobContext(ctx);

	    String outputBucket = pathConfig.getS3BucketsOutputs();

	    final String jsonFolder = pathConfig.getB008JsonFolder(); // /tmp/json/
	    final String fileName = "b008_fin_getting.json";          // ←ここは任意
	    final Path jsonFilePath = Paths.get(jsonFolder, fileName);

	    // S3キー：Python側が読むキーに合わせる
	    // 例: fin/b008_fin_getting_data.json にしたいならファイル名も合わせるのが安全
	    final String s3Key = "fin/" + jsonFilePath.getFileName().toString();

	    // 1) ctx から受け取ったパラメータを map へ変換
	    Map<String, List<Map<String, Object>>> finGettingMap = buildFinGettingMapFromCtx(ctx);

	    // 2) JSON出力
	    Files.createDirectories(jsonFilePath.getParent());
	    makeJson(jsonFilePath.toString(), finGettingMap);

	    // 3) upload
	    upload(outputBucket, s3Key, jsonFilePath);

	    this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	    this.manageLoggerComponent.clear();
	}

	/**
	 * matchId, matchUrlの組み合わせを {@code Map<国, Set<リーグ>>} 形式に変換する。
	 *
	 * <p>
	 * JSON出力用の内部表現として利用する。リーグ順序を保持したい場合に備えて
	 * {@link LinkedHashSet} を使用する。
	 * </p>
	 *
	 * @param ctx アプリケーション
	 * @return マップ
	 */
	private Map<String, List<Map<String, Object>>> buildFinGettingMapFromCtx(JobContext ctx) {
	    String countStr = ctxGet(ctx, "FIN_GETTING_COUNT");
	    if (countStr == null || countStr.isBlank()) {
	        throw new IllegalArgumentException("FIN_GETTING_COUNT がありません");
	    }

	    int count = Integer.parseInt(countStr.trim());
	    Map<String, List<Map<String, Object>>> out = new HashMap<>();

	    for (int i = 0; i < count; i++) {
	        String date = ctxGet(ctx, "FIN_GETTING_" + i + "_DATE");
	        String matchId = ctxGet(ctx, "FIN_GETTING_" + i + "_MATCH_ID");
	        String matchUrl = ctxGet(ctx, "FIN_GETTING_" + i + "_MATCH_URL"); // 無ければ null 想定

	        if (date == null || date.isBlank()) {
	            throw new IllegalArgumentException("DATE がありません: index=" + i);
	        }
	        if (matchId == null || matchId.isBlank()) {
	            throw new IllegalArgumentException("MATCH_ID がありません: index=" + i);
	        }

	        Map<String, Object> row = new HashMap<>();
	        row.put("matchId", matchId.trim());

	        if (matchUrl != null && !matchUrl.isBlank()) {
	            row.put("matchUrl", matchUrl.trim()); // ★キー名は matchUrl 推奨
	        }

	        out.computeIfAbsent(date.trim(), k -> new java.util.ArrayList<>()).add(row);
	    }

	    return out;
	}


	/**
	 * 指定のパスへ {@code b001_country_league.json} を作成する。
	 *
	 * <p>
	 * 出力形式は pretty print とし、Python 側が読みやすい形式で保存する。
	 * </p>
	 *
	 * @param jsonPath 作成先JSONパス（ファイルパス）
	 * @param anyPojoOrMap 国をキー、リーグ集合を値とするマップ
	 * @throws StreamWriteException JSON書き込みに失敗した場合
	 * @throws DatabindException    変換に失敗した場合
	 * @throws IOException          ファイルI/Oで失敗した場合
	 */
	private void makeJson(String jsonPath, Object anyPojoOrMap)
	        throws StreamWriteException, DatabindException, IOException {
	    this.objectMapper.writerWithDefaultPrettyPrinter()
	            .writeValue(new File(jsonPath), anyPojoOrMap);
	}

	/** upload */
	private void upload(String bucket, String key, Path file) {
		final String METHOD_NAME = "upload";
		try {
			s3Operator.uploadFile(bucket, key, file);
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00023E_S3_UPLOAD_FAILED;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
					"bucket: " + bucket + ", key: " + key + ", file: " + file);
		}
	}

	private String ctxGet(JobContext ctx, String key) {
	    // TODO: あなたの基盤に合わせて差し替え
	    // 例) return ctx.getEnv(key);
	    // 例) return (String) ctx.getJobParam().get(key);
	    // 例) return ctx.getParamMap().get(key);
	    throw new UnsupportedOperationException("ctxGet(ctx,key) をプロジェクトの JobContext 実装に合わせて実装してください: key=" + key);
	}

	// デバッグ用
	private void debugJobContext(JobContext ctx) {
	    System.out.println("JobContext impl = " + ctx.getClass().getName());

	    for (Method m : ctx.getClass().getMethods()) {
	        String name = m.getName().toLowerCase();
	        if (name.contains("env") || name.contains("param") || name.contains("map")) {
	            System.out.println("[JobContext] " + m.toString());
	        }
	    }
	}

}
