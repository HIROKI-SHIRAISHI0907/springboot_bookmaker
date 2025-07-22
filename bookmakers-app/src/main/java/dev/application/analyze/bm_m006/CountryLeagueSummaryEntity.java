package dev.application.analyze.bm_m006;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * output_通番.xlsxブックから読み込んだデータをマッピングさせるためのDTOクラス
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class CountryLeagueSummaryEntity extends MetaEntity {

	/** ID */
	private String id;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** データ件数 */
	private String dataCount;

	/** CSV件数 */
	private String csvCount;

}
