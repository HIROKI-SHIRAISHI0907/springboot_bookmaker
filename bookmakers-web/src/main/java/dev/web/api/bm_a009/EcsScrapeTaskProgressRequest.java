package dev.web.api.bm_a009;

import lombok.Data;

/**
 * EcsScrapeTaskProgressRequest
 * @author shiraishitoshio
 *
 */
@Data
public class EcsScrapeTaskProgressRequest {

	/** バッチコード */
	private String batchCd;
    // 以降は未来データ取得スクレイピング用
    /**
     * 実行モード
     * WEEK（省略時）: 従来通り、今日から1週間分を取得
     * NEXT_DAY_ONLY : 翌日の1日分だけを取得
     * SPECIFIC_DATE : targetDate(YYYY-MM-DD) で指定した1日だけを取得（過去日・未来日どちらも可）
     */
    private String runMode;

    /** runMode=SPECIFIC_DATE のときに使用する対象日（"YYYY-MM-DD"） */
    private String targetDate;

}
