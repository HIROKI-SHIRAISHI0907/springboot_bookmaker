package dev.application.analyze.bm_m001;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import dev.application.analyze.interf.OriginEntityIF;
import dev.common.entity.DataEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M001統計分析ロジック（並列）
 */
@Component
public class OriginStat implements OriginEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = OriginStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = OriginStat.class.getSimpleName();

	/** DBサービス */
	@Autowired
	private OriginDBService originDBService;

	/** ログ管理 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/** トランザクション管理 */
	@Autowired
	private PlatformTransactionManager txManager;

	/**
	 * CSVごとに独立トランザクションで登録。
	 * 成功したCSVのみ削除。
	 * 各CSVの登録完了（コミット）直後に進捗ログ（完了CSV件数）を出力。
	 * 一部失敗があれば最後に例外を投げる（成功分はコミット済のまま保持）。
	 */
	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void originStat(Map<String, List<DataEntity>> entities) throws Exception {
		final String METHOD_NAME = "originStat";
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		final int total = entities.size();
		final AtomicInteger successCounter = new AtomicInteger(0);

		// 並列度：CPUコア数か件数の少ない方
		final int threads = Math.min(
				Runtime.getRuntime().availableProcessors(),
				Math.max(1, total));
		ExecutorService pool = Executors.newFixedThreadPool(threads);

		List<Future<TaskResult>> futures = new ArrayList<>(total);

		// 3) コミット順制御用の「順番チケット」
		final AtomicInteger nextTurn = new AtomicInteger(0);
		int idx = 0;
		// タスク生成（CSV 1本＝1トランザクション）
		for (Map.Entry<String, List<DataEntity>> entry : entities.entrySet()) {
			final int myTurn = idx++;
			final String filePath = entry.getKey();
			final List<DataEntity> dataList = entry.getValue();

			Callable<TaskResult> task = () -> {
	            final String fillChar = "ファイル名: " + filePath;

	            // (A) 準備：select はトランザクション外で並列に先行実行
	            List<DataEntity> insertEntities;
	            try {
	                insertEntities = originDBService.selectInBatch(dataList, fillChar);
	            } catch (Exception e) {
	                // 準備段階で失敗したらこのCSVはNGとして返却（順番は進める）
	                manageLoggerComponent.debugErrorLog(
	                        PROJECT_NAME, CLASS_NAME, METHOD_NAME, "selectInBatch失敗", e, filePath);
	                // このタスクが順番をブロックしないように解放
	                // ※ myTurn より前のタスクが成功/失敗で解放していれば自然に進むが保険的に
	                while (nextTurn.get() < myTurn) Thread.onSpinWait();
	                nextTurn.compareAndSet(myTurn, myTurn + 1);
	                return TaskResult.ng(filePath, e);
	            }

	            // (B) 順番待ち：ここから先（insert + コミット）は y 昇順で直列化
	            while (nextTurn.get() != myTurn) {
	                Thread.onSpinWait(); // 軽いスピン（必要なら LockSupport.parkNanos でも可）
	            }

	            try {
	                TransactionTemplate tpl = new TransactionTemplate(txManager);
	                tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

	                TaskResult result = tpl.execute(status -> {
	                    try {
	                        int r = originDBService.insertInBatch(insertEntities);
	                        if (r == 9) throw new Exception("新規登録エラー");
	                        return TaskResult.ok(filePath);
	                    } catch (Exception e) {
	                        status.setRollbackOnly();
	                        manageLoggerComponent.debugErrorLog(
	                                PROJECT_NAME, CLASS_NAME, METHOD_NAME,
	                                "insertInBatch失敗", e, filePath);
	                        return TaskResult.ng(filePath, e);
	                    }
	                });

	                if (result != null && result.success) {
	                    int done = successCounter.incrementAndGet();
	                    manageLoggerComponent.debugInfoLog(
	                            PROJECT_NAME, CLASS_NAME, METHOD_NAME,
	                            String.format("CSV登録完了件数: %d/%d （完了: %s）", done, total, filePath)
	                    );
	                }
	                return result;
	            } finally {
	                // 次の番号を解放（成功・失敗に関わらず）
	                nextTurn.incrementAndGet();
	            }
	        };

	        futures.add(pool.submit(task));
		}

		List<String> successPaths = new ArrayList<>();
		List<String> failedPaths = new ArrayList<>();
		Exception firstThrown = null;

		try {
			for (Future<TaskResult> f : futures) {
				TaskResult r = f.get(); // タスク完了待ち（各タスク中の例外はここで再スロー）
				if (r != null && r.success) {
					successPaths.add(r.filePath);
				} else if (r != null) {
					failedPaths.add(r.filePath);
					if (firstThrown == null) {
						firstThrown = new Exception("一部のCSV登録に失敗しました: " + r.filePath, r.error);
					}
				}
			}
		} finally {
			pool.shutdown();
		}

		// 成功したCSVのみ削除（DB登録はREQUIRES_NEWでコミット済）
		for (String path : successPaths) {
			try {
				Files.deleteIfExists(Paths.get(path));
			} catch (IOException e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, "ファイル削除失敗", e, path);
				// ここでは例外は投げず、DB登録は保持
			}
		}

		// 終了ログ（最終サマリ）
		int success = successPaths.size();
		int failure = total - success;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				String.format("CSV登録完了件数（最終）: %d/%d（失敗: %d）", success, total, failure));

		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 失敗が1件でもあれば呼び出し元へ通知（成功分はコミット済）
		if (firstThrown != null) {
			throw firstThrown;
		}
	}

	/** タスク結果の簡易DTO */
	private static class TaskResult {
		final String filePath;
		final boolean success;
		final Exception error;

		private TaskResult(String filePath, boolean success, Exception error) {
			this.filePath = filePath;
			this.success = success;
			this.error = error;
		}

		static TaskResult ok(String filePath) {
			return new TaskResult(filePath, true, null);
		}

		static TaskResult ng(String filePath, Exception e) {
			return new TaskResult(filePath, false, e);
		}
	}
}
