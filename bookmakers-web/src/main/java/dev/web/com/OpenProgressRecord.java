package dev.web.com;

import lombok.Data;

@Data
public class OpenProgressRecord {

	/** 進行ID */
	private String progressId;

	/** バッチコード */
	private String batchCd;

	/** タスクID */
	private String taskId;

	/** task_arn */
	private String taskArn;

	/** ステータス */
	private String status;

}
