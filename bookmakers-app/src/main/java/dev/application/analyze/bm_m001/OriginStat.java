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

        List<String> successPaths = new ArrayList<>(total);
        List<String> failedPaths  = new ArrayList<>();
        Exception firstThrown = null;

        // ここで並列禁止：順番に1件ずつ処理
        for (Map.Entry<String, List<DataEntity>> entry : entities.entrySet()) {
            final String filePath = entry.getKey();
            final List<DataEntity> dataList = entry.getValue();
            final String fillChar = "ファイル名: " + filePath;

            try {
                // 事前準備はトランザクション外
                List<DataEntity> insertEntities = originDBService.selectInBatch(dataList, fillChar);

                // CSV 1本＝1トランザクション（逐次）
                TransactionTemplate tpl = new TransactionTemplate(txManager);
                tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

                Boolean ok = tpl.execute(status -> {
                    try {
                        int r = originDBService.insertInBatch(insertEntities);
                        if (r == -99) throw new Exception("新規登録エラー");
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
                String.format("CSV登録完了件数（最終）: %d/%d（失敗: %d）",
                        successPaths.size(), total, failedPaths.size()));

        // 全成功時のみ全削除
        if (failedPaths.isEmpty()) {
            for (String path : entities.keySet()) {
                try {
                    Files.deleteIfExists(Paths.get(path));
                } catch (IOException e) {
                    manageLoggerComponent.debugErrorLog(
                            PROJECT_NAME, CLASS_NAME, METHOD_NAME, "全成功後のファイル削除失敗", e, path);
                }
            }
            manageLoggerComponent.debugInfoLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, "全CSV削除を完了しました（全登録成功）");
        } else {
            manageLoggerComponent.debugInfoLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    "失敗があったためCSVは残します。失敗数: " + failedPaths.size());
        }

        manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

        // 1件でも失敗があれば通知（成功分はコミット済）
        if (firstThrown != null) throw firstThrown;
    }

}
