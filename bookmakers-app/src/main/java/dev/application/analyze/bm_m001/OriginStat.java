package dev.application.analyze.bm_m001;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;

/**
 * BM_M001統計分析ロジック（並列）
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

	/**
	 * CSVごとに独立トランザクションで登録。
	 * 各CSVの登録完了（コミット）直後に進捗ログを出力。
	 * 最後に、全て成功した場合のみ、全CSVを削除。
	 * 一部失敗があれば例外を投げる（成功分はコミット済のまま保持）。
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
		Exception firstThrown = null;

		// ここで並列禁止：順番に1件ずつ処理
		for (Map.Entry<String, List<DataEntity>> entry : entities.entrySet()) {
			final String filePath = entry.getKey();
			final List<DataEntity> dataList = entry.getValue();
			final String fillChar = "ファイル名: " + filePath;
			// timesが入っていないデータはスキップ
            if (dataList == null || dataList.isEmpty()
                    || dataList.get(0).getTimes() == null
                    || "".equals(dataList.get(0).getTimes())) {
                skipped++;
                continue;
            }

			try {
				// 事前準備はトランザクション外
				List<DataEntity> insertEntities = originDBService.selectInBatch(dataList, fillChar);

				// CSV 1本＝1トランザクション（逐次）
				TransactionTemplate tpl = new TransactionTemplate(txManager);
				tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

				Boolean ok = tpl.execute(status -> {
					try {
						int r = originDBService.insertInBatch(insertEntities);
						if (r == -99)
							throw new Exception("新規登録エラー");
						return true;
					} catch (Exception e) {
						status.setRollbackOnly();
						manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, "insertInBatch失敗", e, filePath);
						return false;
					}
				});

				if (Boolean.TRUE.equals(ok)) {
					successPaths.add(filePath);
					done++;
					manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							String.format("CSV登録完了: %d/%d （%s）", done, total, filePath));
				} else {
					failedPaths.add(filePath);
					if (firstThrown == null) {
						firstThrown = new Exception("CSV登録に失敗しました: " + filePath);
					}
				}
			} catch (Exception e) {
				// 準備(select)での失敗など
				failedPaths.add(filePath);
				if (firstThrown == null) {
					firstThrown = new Exception("CSV登録に失敗しました: " + filePath, e);
				}
				manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, "逐次処理中に失敗", e, filePath);
			}
		}

		// サマリ
        manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                String.format("CSV登録結果（最終）: 成功=%d, 失敗=%d, スキップ=%d, 全体=%d",
                        successPaths.size(), failedPaths.size(), skipped, total));

        // 全成功時のみ全削除
        if (failedPaths.isEmpty()) {
            String bucket = config.getS3BucketsOutputs();
            List<String> s3KeysToDelete = new ArrayList<>();

            manageLoggerComponent.debugInfoLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    "削除フェーズ開始: total=" + entities.size());

            int deleteIndex = 0;
            for (String localPath : entities.keySet()) {
                deleteIndex++;

                if (deleteIndex % 100 == 0 || deleteIndex == entities.size()) {
                    manageLoggerComponent.debugInfoLog(
                            PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                            String.format("ローカル削除進捗: %d/%d", deleteIndex, entities.size()));
                }

                // ローカル削除
                try {
                    Files.deleteIfExists(Paths.get(localPath));
                } catch (IOException e) {
                    manageLoggerComponent.debugErrorLog(
                            PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                            "ローカルCSV削除失敗", e, localPath);
                }

                // S3 key 収集
                List<DataEntity> list = entities.get(localPath);
                if (list != null && !list.isEmpty()) {
                    String s3Key = list.get(0).getFile(); // 例: 2026-02-05/mid=xxx/seq=....csv
                    if (s3Key != null && !s3Key.isBlank()) {
                        s3KeysToDelete.add(s3Key);
                    }
                }
            }

            // S3まとめ削除
            deleteS3ObjectsInBatches(bucket, s3KeysToDelete, METHOD_NAME);

            manageLoggerComponent.debugInfoLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    "全CSV削除を完了しました（S3 + ローカル）（全登録成功）");
        } else {
            manageLoggerComponent.debugInfoLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    "失敗があったためCSVは残します。失敗数: " + failedPaths.size());
        }

        manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

        // 1件でも失敗があれば通知（成功分はコミット済）
        if (firstThrown != null) {
            throw firstThrown;
        }
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
            s3Operator.deleteObjects(bucket, chunk);
        }
    }
}
