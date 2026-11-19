package dev.application.batchjob.dto;

// 2026-11-20 bookmakers-batch（dev.batch.bm_b095.StatusPair）から移動（B011 を統計用の bookmakers-app で動かすため）

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StatusPair {

	/** ステータス(batch_job_exec用) */
	private String status;

}
