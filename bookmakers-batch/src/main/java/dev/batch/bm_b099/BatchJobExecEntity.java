package dev.batch.bm_b099;

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
