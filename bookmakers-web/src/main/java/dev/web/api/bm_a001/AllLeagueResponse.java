package dev.web.api.bm_a001;

import java.util.List;

import lombok.Data;

@Data
public class AllLeagueResponse {

	/** バッチコード */
    private String batchCode;

    /** バケット */
    private String bucket;

    /** prefix */
    private String prefix;

    /** 再帰処理 */
    private boolean recursive;

    /** 返却件数 */
    private long returnedCount;

    /** メッセージ */
    private String message;

    /** S3バケット */
    private List<Item> items;

    @Data
    public static class Item {
        private String key;
        private long size;
        private String lastModifiedIso;
    }
}
