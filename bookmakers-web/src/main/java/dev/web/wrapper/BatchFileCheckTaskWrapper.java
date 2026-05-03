package dev.web.wrapper;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * タスク単位の事前ファイル確認結果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchFileCheckTaskWrapper {

    /**
     * タスクコード
     * 例: B002, B010
     */
    private String taskCode;

    /**
     * 実行可能か
     */
    private boolean ready;

    /**
     * サマリ
     * 例: 準備OK, 必須不足, 件数確認
     */
    private String summary;

    /**
     * 明細一覧
     */
    @Builder.Default
    private List<BatchFileCheckItemWrapper> items = new ArrayList<>();

}
