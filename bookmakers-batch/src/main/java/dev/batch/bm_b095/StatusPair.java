package dev.batch.bm_b095;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StatusPair {

	/** ステータス(batch_job_exec用) */
	private String status;

}
