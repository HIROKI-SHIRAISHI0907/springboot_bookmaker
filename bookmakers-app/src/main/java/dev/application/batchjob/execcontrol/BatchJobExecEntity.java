package dev.application.batchjob.execcontrol;

// 2026-11-20 bookmakers-batch（dev.batch.bm_b099.BatchJobExecEntity）から移動（B011 を統計用の bookmakers-app で動かすため）

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ジョブ実行管理テーブル用Entity
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class BatchJobExecEntity extends MetaEntity {

	/** ジョブID */
	private String jobId;

	/** バッチコード */
	private String batchCd;

	/** ステータス */
	private int status;

}
