package dev.batch.bm_b004;

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
public class TeamColorMasterEntity extends MetaEntity {

	/** ID */
	private String id;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** チーム */
	private String team;

	/** メインカラーコード */
	private String teamColorMainHex;

	/** サブカラーコード */
	private String teamColorSubHex;

}
