package dev.web.api.bm_w099;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Web画面表示用のバッチ一覧（固定表示用）。
 * <p>
 * Webは batch 実装に依存しないため、コード一覧のみを保持する。
 * </p>
 */
@Component
public class BatchRegistry {

    public List<String> listBatchCodes() {
        return List.of("B001", "B002", "B003", "B004", "B005");
    }
}
