package dev.application.analyze.bm_m001;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import dev.application.analyze.interf.OriginEntityIF;
import dev.application.domain.repository.bm.MatchKeySaveRepository;
import dev.common.config.PathConfig;
import dev.common.entity.DataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;

/**
 * BM_M001統計分析ロジック（逐次）
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

	/** マッチキー */
	@Autowired
	private MatchKeySaveRepository matchKeySaveRepository;

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
	 *
	 * 仕様:
	 * - 成功したCSVのみ successPaths に積む
	 * - skipしたCSV（times空 / match_key重複）は削除対象にしない
	 * - 失敗が1件でもあれば削除フェーズは実施しない
	 * - 失敗が無い場合のみ、successPaths のCSVだけ削除する
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
		Exception firstThrown = null;

		for (Map.Entry<String, List<DataEntity>> entry : entities.entrySet()) {
			final String filePath = entry.getKey();
			final List<DataEntity> dataList = entry.getValue();
			final String fillChar = "ファイル名: " + filePath;

			// times が無い場合はスキップ（削除対象にしない）
			if (dataList == null || dataList.isEmpty()
					|| dataList.get(0).getTimes() == null
					|| "".equals(dataList.get(0).getTimes())) {

				skipped++;
				skippedPaths.add(filePath);

				manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						String.format("timesが空のためスキップ（削除対象外）: %s", filePath));
				continue;
			}

			// マッチキーをDBから取得し、既存ならスキップ（削除対象にしない）
			List<String> matchIdList = dataList.stream()
					.filter(Objects::nonNull)
					.map(DataEntity::getMatchId)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());

			boolean existsMatchKey = false;
			String existsMatchId = null;

			for (String matchId : matchIdList) {
				if (matchKeySaveRepository.findMatchKeys(matchId) > 0) {
					manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							String.format("match_keyが存在するため別データです。（%s: skip, 削除対象外, file=%s）",
									matchId, filePath));
					existsMatchKey = true;
					existsMatchId = matchId;
					break;
				}
			}

			if (existsMatchKey) {
				skipped++;
				skippedPaths.add(filePath);

				manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						String.format("match_key重複スキップ確定（削除対象外）: matchId=%s, file=%s",
								existsMatchId, filePath));
				continue;
			}

			try {
				// 事前準備はトランザクション外
				List<DataEntity> insertEntities = originDBService.selectInBatch(dataList, fillChar);

				// CSV 1本 = 1トランザクション
				TransactionTemplate tpl = new TransactionTemplate(txManager);
				tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

				Boolean ok = tpl.execute(status -> {
					try {
						int r = originDBService.insertInBatch(insertEntities);
						if (r == -99) {
							throw new Exception("新規登録エラー");
						}
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
				String.format("CSV登録結果（最終）: 成功=%d, 失敗=%d, スキップ=%d, 全体=%d",
						successPaths.size(), failedPaths.size(), skipped, total));

		manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				String.format("削除対象サマリ: successPaths=%d, skippedPaths=%d, failedPaths=%d",
						successPaths.size(), skippedPaths.size(), failedPaths.size()));

		// 失敗が無い場合のみ、成功したCSVだけ削除
		if (failedPaths.isEmpty()) {
			String bucket = config.getS3BucketsOutputs();
			List<String> s3KeysToDelete = new ArrayList<>();

			manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					"削除フェーズ開始: successOnly=" + successPaths.size());

			int deleteIndex = 0;
			for (String localPath : successPaths) {
				deleteIndex++;

				if (deleteIndex % 100 == 0 || deleteIndex == successPaths.size()) {
					manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							String.format("削除進捗: %d/%d", deleteIndex, successPaths.size()));
				}

				// ローカル削除
				try {
					Files.deleteIfExists(Paths.get(localPath));
					manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							String.format("ローカルCSV削除完了: %s", localPath));
				} catch (IOException e) {
					manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							"ローカルCSV削除失敗", e, localPath);
				}

				// S3 key 収集（successPathsのみ）
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
					"成功したCSVのみ削除を完了しました（S3 + ローカル）。skipファイルは削除していません。");
		} else {
			manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					String.format("失敗があったため削除フェーズは実施しません。成功=%d, 失敗=%d, スキップ=%d",
							successPaths.size(), failedPaths.size(), skippedPaths.size()));
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

			manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, methodName,
					String.format("S3削除実行: from=%d, to=%d, batchSize=%d",
							from, to, chunk.size()));

			s3Operator.deleteObjects(bucket, chunk);
		}
	}
}
