package dev.web.api.bm_w021;

import lombok.Data;

/**
 * ECSタスクログ取得APIレスポンス
 *
 * @author shiraishitoshio
 */
@Data
public class EcsTaskProgressResponse {

	/** タスクID */
	private String taskId;

	/** 進捗率 */
    private Double percent;      // 算出できたら値、できなければ null

    /** 取得完了数 */
    private Integer teamsDone;   // X

    /** 取得予定数 */
    private Integer teamsTotal;  // Y

    /** 該当ログ行 */
    private String logLine;      // 該当ログ行（見つかった場合）

    /** 該当ログ時間 */
    private String logTime;      // ISO文字列など

    /** メッセージ */
    private String message;      // 算出できない場合のメッセージ

}
