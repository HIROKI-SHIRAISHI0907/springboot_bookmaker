package dev.application.analyze.bm_m019_bm_m020;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * classify_result_data_detailデータEntity
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MatchClassificationResultCountEntity extends MetaEntity {

	/** ID */
	private String id;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** 分類モード */
	private String classifyMode;

	/** 該当数 */
	private String count;

	/** 備考 */
	private String remarks;

}
