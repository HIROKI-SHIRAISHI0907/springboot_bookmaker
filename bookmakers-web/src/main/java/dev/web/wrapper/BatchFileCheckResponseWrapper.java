package dev.web.wrapper;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 事前ファイル確認 API レスポンス
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchFileCheckResponseWrapper {

    /**
     * タスク一覧
     */
    @Builder.Default
    private List<BatchFileCheckTaskWrapper> tasks = new ArrayList<>();

}
