package dev.batch.bm_b010;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.batch.common.AbstractJobBatchTemplate;
import dev.batch.repository.master.AllLeagueMasterBatchRepository;
import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.entity.AllLeagueMasterEntity;
import dev.common.s3.S3Operator;

/**
 * JSON変換用
 * @author shiraishitoshio
 *
 */
@Service("B010")
public class RealDataConvertJsonBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = RealDataConvertJsonBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = RealDataConvertJsonBatch.class.getName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B010_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B010";

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

	/** AllLeagueMasterBatchRepository */
	@Autowired
	private AllLeagueMasterBatchRepository allLeagueMasterBatchRepository;

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

		// ログ出力
		this.manageLoggerComponent.init(null, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// バケット名取得
		String outputBucket = pathConfig.getS3BucketsOutputs();
		String seasonBucket = pathConfig.getS3BucketsTeamSeasonDateData();
		String teamBucket = pathConfig.getS3BucketsTeamData();
		String teamMemberBucket = pathConfig.getS3BucketsTeamMemberData();

		final String jsonFolder = pathConfig.getB001JsonFolder(); // /tmp/json/
		final String jsonPath = jsonFolder + "b001_country_league.json";
		final Path jsonFilePath = Paths.get(jsonPath);
		final String s3Key = "json/" + jsonFilePath.getFileName().toString();

		// disp_flgが0のものだけ取得
		List<AllLeagueMasterEntity> entities = allLeagueMasterBatchRepository.
				findActiveByCountryAndLeagueAtJson();

		// entities -> Map<String, Set<String>> に変換
		Map<String, Set<String>> countryLeagueMap = buildCountryLeagueMap(entities);

		// リクエストで受け取ったデータをObjectMapperで変換
		Files.createDirectories(jsonFilePath.getParent());
		makeJson(jsonPath, countryLeagueMap);

		// upload
		upload(outputBucket, s3Key, jsonFilePath);
		upload(seasonBucket, s3Key, jsonFilePath);
		upload(teamBucket, s3Key, jsonFilePath);
		upload(teamMemberBucket, s3Key, jsonFilePath);

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * 国・リーグの組み合わせを {@code Map<国, Set<リーグ>>} 形式に変換する。
	 *
	 * <p>
	 * JSON出力用の内部表現として利用する。リーグ順序を保持したい場合に備えて
	 * {@link LinkedHashSet} を使用する。
	 * </p>
	 *
	 * @param entities 変換元（国・リーグ情報を持つエンティティリスト）
	 * @return 国をキー、リーグ集合を値とするマップ
	 */
	private Map<String, Set<String>> buildCountryLeagueMap(List<AllLeagueMasterEntity> entities) {
		Map<String, Set<String>> map = new HashMap<>();
		for (AllLeagueMasterEntity entity : entities) {
			map.computeIfAbsent(entity.getCountry(), k -> new LinkedHashSet<>())
					.add(entity.getLeague());
		}
		return map;
	}

	/**
	 * 指定のパスへ {@code b001_country_league.json} を作成する。
	 *
	 * <p>
	 * 出力形式は pretty print とし、Python 側が読みやすい形式で保存する。
	 * </p>
	 *
	 * @param jsonPath 作成先JSONパス（ファイルパス）
	 * @param countryLeagueMap 国をキー、リーグ集合を値とするマップ
	 * @throws StreamWriteException JSON書き込みに失敗した場合
	 * @throws DatabindException    変換に失敗した場合
	 * @throws IOException          ファイルI/Oで失敗した場合
	 */
	private void makeJson(String jsonPath, Map<String, Set<String>> countryLeagueMap)
			throws StreamWriteException, DatabindException, IOException {
		this.objectMapper.writerWithDefaultPrettyPrinter()
				.writeValue(new File(jsonPath), countryLeagueMap);
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

}
