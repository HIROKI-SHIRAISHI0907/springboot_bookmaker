package dev.application.analyze.bm_m033;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 読み込んだデータから結果マスタにマッピングさせるためのDTOクラス
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class RankHistoryEntity extends MetaEntity {

	/** ID */
	private String id;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** シーズン年 */
	private String seasonYear;

	/** 節 */
	private Integer match;

	/** チーム */
	private String team;

	/** 順位 */
	private Integer rank;

}
