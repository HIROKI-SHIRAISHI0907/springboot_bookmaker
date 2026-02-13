package dev.web.api.bm_a010;

import lombok.Data;

/**
 * TeamColorDTO
 * @author shiraishitoshio
 *
 */
@Data
public class TeamColorDTO {

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
