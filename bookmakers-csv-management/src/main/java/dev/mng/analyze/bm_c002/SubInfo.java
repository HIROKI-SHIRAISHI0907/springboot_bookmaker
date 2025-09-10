package dev.mng.analyze.bm_c002;

import lombok.Data;

/**
 * 国リーグステータスオブジェクト
 * @author shiraishitoshio
 *
 */
@Data
public class SubInfo {

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** ステータス */
	private String status;

}
