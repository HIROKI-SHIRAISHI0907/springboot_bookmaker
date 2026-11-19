package dev.application.analyze.bm_m001;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import dev.application.analyze.interf.OriginEntityIF;
import dev.common.config.PathConfig;
import dev.common.entity.DataEntity;
import dev.common.getinfo.GetOriginInfo;
import dev.common.getinfo.PutOriginBackUpInfo;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;

/**
 * BM_M001統計分析ロジック（逐次）
 *
 * <p>2026-10 修正（ヘッダしかない CSV が S3 に残り続ける問題）</p>
 * <ul>
 *   <li>S3 から消すキーを「リストの先頭行の getFile()」だけから取っていたため、
 *       データ行が 0 件（ヘッダのみ）のファイルは S3 キーが取れず、ローカルだけ消えて S3 に残り続けていた。
 *       先頭行が無い・getFile() が空のときは、Map のキー（GetOriginInfo が返す S3 キー）を使う。</li>
 *   <li>ヘッダのみ（データ行 0 件）のファイルは、登録・バックアップの対象にせず、
 *       他の CSV の失敗やバックアップ失敗に関係なく、S3 とローカルから削除する（残しておく中身が無いため）。</li>
 *   <li>times が空でデータ行があるファイルは、今までどおり「バックアップ格納したうえで削除」の対象。</li>
 * </ul>
 */
@Component
public class OriginStat implements OriginEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = OriginStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = OriginStat.class.getName();

	/** S3 DeleteObjects 最大件数 */
	private static final int S3_DELETE_BATCH_SIZE = 1000;

	/** DBサービス */
	@Autowired
	private OriginDBService originDBService;

	@Autowired
	private S3Operator s3Operator;

	@Autowired
	private PathConfig config;

	/** ログ管理 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/** トランザクション管理 */
	@Autowired
	private PlatformTransactionManager txManager;

	/** バックアップ格納(mid単位zip追加格納) */
	@Autowired
	private PutOriginBackUpInfo putOriginBackUpInfo;

	/**
	 * CSVごとに独立トランザクションで登録。
	 *
	 * 仕様:
	 * - 成功したCSVのみ successPaths に積む
	 * - ヘッダのみ（データ行 0 件）は DB登録・バックアップせず、必ず削除する（headerOnlyPaths）
	 * - times空はDB登録しないが削除対象にする
	 * - 登録0件(match_key重複等でinsertInBatchが0件返す)でもバックアップ格納したうえで削除する
	 * - 失敗が1件でもあれば削除フェーズは実施しない（ヘッダのみのファイルは除く）
	 * - 失敗が無い場合のみ、successPaths + timesEmptyPaths のCSVをバックアップ格納したうえで削除する
	 * - バックアップ格納(mid単位zip追加)に1件でも失敗した場合、削除フェーズ全体を中止する
	 *   (バックアップに成功した分も含めて、このタイミングでは何も削除しない。ヘッダのみのファイルは除く)
	 */
	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void originStat(Map<String, List<DataEntity>> entities) throws Exception {
		final String METHOD_NAME = "originStat";
		manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		final int total = entities.size();
		int done = 0;
		int skipped = 0;

		List<String> successPaths = new ArrayList<>(total);
		List<String> failedPaths = new ArrayList<>();
		List<String> skippedPaths = new ArrayList<>();
		List<String> timesEmptyPaths = new ArrayList<>();
		List<String> headerOnlyPaths = new ArrayList<>();
		Exception firstThrown = null;

		for (Map.Entry<String, List<DataEntity>> entry : entities.entrySet()) {
			final String filePath = entry.getKey();
			final List<DataEntity> dataList = entry.getValue();
			final String fillChar = "ファイル名: " + filePath;

			// ヘッダのみ（GetOriginInfo が空のリストで返す）: DB登録・バックアップせず、削除だけ行う
			if (dataList == null || dataList.isEmpty()) {
				skipped++;
				headerOnlyPaths.add(filePath);
				manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						String.format("データ行なし（ヘッダのみ）のためDB登録スキップ（削除対象）: %s", filePath));
				continue;
			}

			// times が無い場合はDB登録スキップ、ただし削除対象にはする
			if (dataList.get(0).getTimes() == null
					|| "".equals(dataList.get(0).getTimes())) {
				skipped++;
				timesEmptyPaths.add(filePath);
				manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						String.format("timesが空のためDB登録スキップ（削除対象）: %s", filePath));
				continue;
			}

			try {
				// 事前準備はトランザクション外
				List<DataEntity> insertEntities = originDBService.selectInBatch(dataList, fillChar);

				// CSV 1本 = 1トランザクション
				TransactionTemplate tpl = new TransactionTemplate(txManager);
				tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

				AtomicInteger insertedCount = new AtomicInteger(0);
				Boolean ok = tpl.execute(status -> {
					try {
						int r = originDBService.insertInBatch(insertEntities);
						if (r == -99) {
							throw new Exception("新規登録エラー");
						}
						insertedCount.set(r);
						return true;
					} catch (Exception e) {
						status.setRollbackOnly();
						manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME,
								"insertInBatch失敗", e, filePath);
						return false;
					}
				});

				if (Boolean.TRUE.equals(ok)) {
					// 登録0件(match_key重複等)でもバックアップ格納・削除の対象にする
					successPaths.add(filePath);
					done++;
					if (insertedCount.get() == 0) {
						manageLoggerComponent.debugInfoLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME,
								String.format("登録0件(match_key重複等)ですがバックアップ・削除対象とします: %d/%d （%s）",
										done, total, filePath));
					} else {
						manageLoggerComponent.debugInfoLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME,
								String.format("CSV登録完了: %d/%d （%s）", done, total, filePath));
					}
				} else {
					failedPaths.add(filePath);
					if (firstThrown == null) {
						firstThrown = new Exception("CSV登録に失敗しました: " + filePath);
					}
				}
			} catch (Exception e) {
				// selectInBatch等の失敗
				failedPaths.add(filePath);
				if (firstThrown == null) {
					firstThrown = new Exception("CSV登録に失敗しました: " + filePath, e);
				}
				manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						"逐次処理中に失敗", e, filePath);
			}
		}

		// サマリ
		manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				String.format("CSV登録結果（最終）: 成功=%d, 失敗=%d, スキップ=%d（うちヘッダのみ=%d）, 全体=%d",
						successPaths.size(), failedPaths.size(), skipped, headerOnlyPaths.size(), total));

		manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				String.format("削除対象サマリ: successPaths=%d, timesEmptyPaths=%d, headerOnlyPaths=%d, skippedPaths=%d, failedPaths=%d",
						successPaths.size(), timesEmptyPaths.size(), headerOnlyPaths.size(),
						skippedPaths.size(), failedPaths.size()));

		String bucket = config.getS3BucketsOutputs();

		// ヘッダのみのファイルは、他の失敗・バックアップ結果に関係なく削除する（中身が無いのでバックアップ不要）
		if (!headerOnlyPaths.isEmpty()) {
			manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					"ヘッダのみのCSVを削除します（バックアップなし）: " + headerOnlyPaths.size() + "件");
			deleteLocalAndS3(bucket, headerOnlyPaths, entities, METHOD_NAME);
		}

		// 失敗が無い場合のみ、成功したCSV + times空CSVをバックアップ格納したうえで削除
		if (failedPaths.isEmpty()) {
			List<String> deleteCandidates = new ArrayList<>();
			deleteCandidates.addAll(successPaths);
			deleteCandidates.addAll(timesEmptyPaths);

			if (deleteCandidates.isEmpty()) {
				manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						"バックアップ格納・削除の対象なし（ヘッダのみのCSV以外なし）");
			} else {
				manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						"バックアップ格納開始: deleteCandidates=" + deleteCandidates.size()
								+ " (successOnly=" + successPaths.size()
								+ ", timesEmpty=" + timesEmptyPaths.size() + ")");

				// mid単位(bucket/mid名.zip)でバックアップ格納。1件でも失敗したら削除フェーズ全体を中止する。
				PutOriginBackUpInfo.BackupResult backupResult = putOriginBackUpInfo.backup(entities, deleteCandidates);

				if (!backupResult.getFailedLocalPaths().isEmpty()) {
					manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							String.format(
									"バックアップ格納に失敗したCSVがあるため、削除フェーズ全体を中止します。"
											+ "backupSucceeded=%d, backupFailed=%d, backupFailedPaths=%s",
									backupResult.getSucceededLocalPaths().size(),
									backupResult.getFailedLocalPaths().size(),
									backupResult.getFailedLocalPaths()));
				} else {
					List<String> deleteTargets = backupResult.getSucceededLocalPaths();

					manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							"削除フェーズ開始: deleteTargets=" + deleteTargets.size()
									+ " (successOnly=" + successPaths.size()
									+ ", timesEmpty=" + timesEmptyPaths.size() + ")");

					deleteLocalAndS3(bucket, deleteTargets, entities, METHOD_NAME);

					manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							"バックアップ格納済みのCSVの削除を完了しました（S3 + ローカル）。");
				}
			}
		} else {
			manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					String.format("失敗があったため削除フェーズは実施しません（ヘッダのみのCSVは削除済み）。"
							+ "成功=%d, times空=%d, ヘッダのみ=%d, 失敗=%d, スキップ=%d",
							successPaths.size(), timesEmptyPaths.size(), headerOnlyPaths.size(),
							failedPaths.size(), skippedPaths.size()));
		}

		manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 1件でも失敗があれば通知（成功分はコミット済）
		if (firstThrown != null) {
			throw firstThrown;
		}
	}

	/**
	 * ローカルCSVを削除し、S3キーを集めてまとめて削除する。
	 *
	 * @param bucket   バケット
	 * @param keys     entities のキー（GetOriginInfo#getData() が返す S3 key）
	 * @param entities 読み込み結果（S3 key の取得に使う）
	 * @param methodName ログ用メソッド名
	 */
	private void deleteLocalAndS3(String bucket, List<String> keys,
			Map<String, List<DataEntity>> entities, String methodName) {

		List<String> s3KeysToDelete = new ArrayList<>();

		int deleteIndex = 0;
		for (String localPath : keys) {
			deleteIndex++;
			if (deleteIndex % 100 == 0 || deleteIndex == keys.size()) {
				manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, methodName,
						String.format("削除進捗: %d/%d", deleteIndex, keys.size()));
			}

			// ローカル削除
			// 注意: entitiesのキー(localPath)はGetOriginInfo#getData()が返すS3 keyそのものであり、
			// ローカルディスク上の実ファイルパスではない。実ファイルはGetOriginInfo#resolveLocalPath()が
			// 算出する一時フォルダ配下にあるため、必ずそちら経由で実パスを求めてから削除する。
			try {
				Path actualLocalPath = GetOriginInfo.resolveLocalPath(localPath);
				boolean deleted = Files.deleteIfExists(actualLocalPath);
				manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, methodName,
						String.format("ローカルCSV削除%s: %s",
								deleted ? "完了" : "対象ファイルなし", actualLocalPath));
			} catch (IOException e) {
				manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, methodName,
						"ローカルCSV削除失敗", e, localPath);
			}

			// S3 key 収集（先頭行の getFile() を優先し、無ければ Map のキー = S3 key を使う）
			String s3Key = null;
			List<DataEntity> list = entities.get(localPath);
			if (list != null && !list.isEmpty() && list.get(0) != null) {
				s3Key = list.get(0).getFile(); // 例: 2026-02-05/mid=xxx/seq=....csv
			}
			if (s3Key == null || s3Key.isBlank()) {
				s3Key = localPath;
			}
			if (s3Key != null && !s3Key.isBlank()) {
				s3KeysToDelete.add(s3Key);
			}
		}

		// S3まとめ削除
		deleteS3ObjectsInBatches(bucket, s3KeysToDelete, methodName);
	}

	/**
	 * S3のオブジェクトを1000件単位でまとめて削除
	 */
	private void deleteS3ObjectsInBatches(String bucket, List<String> s3Keys, String methodName) {
		if (s3Keys == null || s3Keys.isEmpty()) {
			manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, methodName,
					"S3削除対象なし");
			return;
		}

		int total = s3Keys.size();
		for (int from = 0; from < total; from += S3_DELETE_BATCH_SIZE) {
			int to = Math.min(from + S3_DELETE_BATCH_SIZE, total);
			List<String> chunk = s3Keys.subList(from, to);
			manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, methodName,
					String.format("S3削除実行: from=%d, to=%d, batchSize=%d",
							from, to, chunk.size()));
			s3Operator.deleteObjects(bucket, chunk);
		}
	}
}