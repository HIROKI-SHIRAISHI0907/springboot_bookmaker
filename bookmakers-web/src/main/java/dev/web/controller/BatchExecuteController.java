package dev.web.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w099.BatchExecuteRequestDTO;
import dev.web.api.bm_w099.BatchExecuteResponseDTO;
import dev.web.batch.WebBatchService;

/**
 * バッチ実行コントローラー（B001〜B005）
 * <p>
 * Web画面（またはRESTクライアント）からバッチを実行するためのエンドポイントを提供する。
 * </p>
 */
@RestController
@RequestMapping("/admin/batch")
public class BatchExecuteController {

    /** Webからバッチを実行するサービス */
	@Autowired
    private WebBatchService runner;

    /**
     * バッチ一覧（固定）を返す。
     * <p>
     * 画面側でボタン表示に使う想定。
     * </p>
     *
     * @return バッチコード一覧
     */
    @GetMapping("/codes")
    public ResponseEntity<String[]> codes() {
        return ResponseEntity.ok(new String[] { "B001", "B002", "B003", "B004", "B005" });
    }

    /**
     * 指定されたバッチを実行する。
     *
     * @param request 実行リクエスト
     * @return 実行結果
     */
    @PostMapping("/execute")
    public ResponseEntity<BatchExecuteResponseDTO> execute(@RequestBody BatchExecuteRequestDTO request) {
        validate(request);
        return ResponseEntity.ok(this.runner.execute(request));
    }

    /**
     * 入力チェック（最小限）
     *
     * @param request リクエスト
     */
    private void validate(BatchExecuteRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.getBatchCode() == null || request.getBatchCode().isBlank()) {
            throw new IllegalArgumentException("batchCode is required");
        }

        // B001〜B005 以外は弾く（必要なら増やす）
        String code = request.getBatchCode().trim();
        if (!code.matches("^B00[1-5]$")) {
            throw new IllegalArgumentException("invalid batchCode: " + code);
        }
    }
}
