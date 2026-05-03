package dev.web.wrapper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * タスクごとの事前ファイル確認 明細
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchFileCheckItemWrapper {

    /**
     * 表示名
     * 例: 入力JSON, seqList.txt, 直フォルダ数
     */
    private String label;

    /**
     * S3バケット名
     */
    private String bucket;

    /**
     * S3キー
     * count系では null 可
     */
    private String key;

    /**
     * kind:
     * - file  : ファイル存在確認
     * - folder: フォルダ存在/件数
     * - count : 件数表示
     */
    private String kind;

    /**
     * type:
     * - json
     * - csv
     * - txt
     * - folder
     * - count
     * など
     */
    private String type;

    /**
     * 存在有無
     * count表示だけの場合は false でも可
     */
    private boolean exists;

    /**
     * 必須項目か
     */
    private boolean required;

    /**
     * 件数表示用
     * file系では null 可
     */
    private Long count;

}
