package dev.batch.bm_b010;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;
import dev.batch.repository.bm.MatchKeySaveRepository;
import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.getinfo.GetOriginInfo;
import dev.common.readfile.dto.MatchKeyItem;
import dev.common.s3.S3Operator;
import dev.common.util.FileDeleteUtil;

/**
 * 「終了済」欠損データ登録バッチ実行クラス。
 * <p>
 * 国・リーグの全容マスタデータを取得し、登録ロジック（Transactional想定）を実行する。
 * </p>
 *
 * <p><b>実行方式</b></p>
 * <ul>
 *   <li>開始/終了ログを必ず出力する</li>
 *   <li>例外は内部で捕捉し、debugErrorLog に例外を付与して出力する</li>
 *   <li>戻り値で成功/失敗を返却する</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Service("B010")
public class FinGettingBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = FinGettingBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = FinGettingBatch.class.getName();

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

	/** MatchKeySaveRepository */
	@Autowired
	private MatchKeySaveRepository matchKeySaveRepository;

	@Autowired
	private GetOriginInfo getOriginInfo;

	/** 終了済ロジック */
	@Autowired
	private FinGettingStat finGettingStat;

	/** Truncateロジック */
	@Autowired
	private FinGettingTruncate finGettingTruncate;

	/** パスや外部実行設定（Python/S3等）を保持する設定クラス。 */
	@Autowired
	private PathConfig pathConfig;

	/** S3Operator */
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

		List<String> insertPath = new ArrayList<String>();
		final String jsonFolder = pathConfig.getB008JsonFolder(); // /tmp/json/
		final String jsonPath = jsonFolder + "b008_fin_getting_data.json";
		final Path jsonFilePath = Paths.get(jsonPath);
		final String s3Key = "json/" + jsonFilePath.getFileName().toString();
		insertPath.add(s3Key);

		// マッチキーDBから保存済マッチキーを取得
		List<MatchKeyItem> items = matchKeySaveRepository.findMatchKeys().stream()
			    .map(k -> { MatchKeyItem e = new MatchKeyItem(); e.setMatchKey(k); return e; })
			    .collect(Collectors.toList());

		// 取得できなかった場合は誤ってリアルタイムデータを登録してしまうのを防ぐためErrorを出力
		if (items.isEmpty()) {
			/** エラーコード（運用ルールに合わせて変更） */
			String ERROR_CODE = MessageCdConst.MCD00003E_EXECUTION_SKIP;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null,
					"items.isEmpty() マッチキーが取得できなかったため処理を終了します。");
			return;
		}

		// ObjectをダウンロードしEntityにマッピング
		Map<String, List<DataEntity>> map = getOriginInfo.getData(items);
		this.finGettingStat.finGettingStat(map);

		// 削除
		finGettingTruncate.truncate();

		String bucket = pathConfig.getS3BucketsOutputs(); // バケット名取得
		FileDeleteUtil.deleteS3Files(
				insertPath,
				bucket,
				s3Operator,
				manageLoggerComponent,
				PROJECT_NAME,
				CLASS_NAME,
				METHOD_NAME,
				"b008_fin_getting_data.json");

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

}
