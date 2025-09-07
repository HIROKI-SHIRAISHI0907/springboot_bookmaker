package dev.application.analyze.bm_m097;

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
