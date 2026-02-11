package dev.web.api.bm_a006;

import lombok.Data;

@Data
public class S3FileCountResponse {

	/** バッチコード */
    private String batchCode;

    /** S3バケット */
    private String bucket;

    /** プレフィックス */
    private String prefix;

    /** 再帰確認 */
    private boolean recursive;

    /** 対象日 */
    private String dayJst;            // 例: 2026-01-30

    /** 全件 */
    private long totalCount;          // 全件

    /** 対象件数(上書きされた日がその日にであればそれも含まれる) */
    private long countOnDay;          // その日(JST)にLastModifiedの件数

    /** メッセージ */
    private String message;

}
