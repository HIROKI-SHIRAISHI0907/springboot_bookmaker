package dev.web.batch;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.stereotype.Service;

import dev.batch.constant.BatchConstant;
import dev.batch.interf.BatchIF;
import dev.common.logger.ManageLoggerComponent;
import dev.web.api.bm_w099.BatchExecuteRequestDTO;
import dev.web.api.bm_w099.BatchExecuteResponseDTO;

/**
 * Web画面からのバッチ実行を受けるサービス。
 * <p>
 * Webモジュールは batch に依存できないため、実処理は batch 側が担う。
 * </p>
 */
@Service
public class WebBatchService {

	/** バッチマップ */
    private final Map<String, BatchIF> batchMap;

    /** ロガー */
    private final ManageLoggerComponent logger;

    /**
     * コンストラクタ
     * @param batchMap
     * @param logger
     */
    public WebBatchService(Map<String, BatchIF> batchMap, ManageLoggerComponent logger) {
        this.batchMap = batchMap;
        this.logger = logger;
    }

    /**
     * 実行メソッド
     * @param req
     * @return
     */
    public BatchExecuteResponseDTO execute(BatchExecuteRequestDTO req) {
        LocalDateTime start = LocalDateTime.now();
        String code = req.getBatchCode();

        try {
            // ThreadContext
            String exeMode = (req.getExeMode() == null || req.getExeMode().isBlank()) ? "WEB" : req.getExeMode();

            if (req.getCountry() != null || req.getLeague() != null) {
                logger.init(exeMode,
                        (req.getLogicCd() == null || req.getLogicCd().isBlank()) ? code : req.getLogicCd(),
                        req.getCountry() == null ? "-" : req.getCountry(),
                        req.getLeague() == null ? "-" : req.getLeague()
                );
            } else {
                logger.init(exeMode, (req.getInfo() == null || req.getInfo().isBlank()) ? code : req.getInfo());
            }

            BatchIF batch = batchMap.get(code);
            if (batch == null) {
                return new BatchExecuteResponseDTO(code, BatchConstant.BATCH_ERROR, "ERROR", start, LocalDateTime.now(),
                        "Unknown batchCode: " + code);
            }

            int result = batch.execute();
            String label = (result == BatchConstant.BATCH_SUCCESS) ? "SUCCESS" : "ERROR";

            return new BatchExecuteResponseDTO(code, result, label, start, LocalDateTime.now(),
                    "Batch executed: " + code);

        } catch (Exception e) {
            logger.debugErrorLog("BATCH", "WebBatchService", "execute", "WEB_BATCH_ERROR",
                    (e instanceof Exception) ? (Exception) e : new Exception(e));

            return new BatchExecuteResponseDTO(code, BatchConstant.BATCH_ERROR, "ERROR", start, LocalDateTime.now(),
                    "Exception occurred. See logs.");

        } finally {
            logger.clear();
        }
    }
}
