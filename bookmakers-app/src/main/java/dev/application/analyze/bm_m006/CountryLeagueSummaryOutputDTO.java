package dev.application.analyze.bm_m006;

import lombok.Getter;
import lombok.Setter;

/**
 * country_league_summary outputDTO
 * @author shiraishitoshio
 *
 */
@Setter
@Getter
public class CountryLeagueSummaryOutputDTO {

	/**
	 * 更新用通番
	 */
	private String seq;

	/**
	 * 更新フラグ
	 */
	private boolean updFlg;

	/**
	 * 件数
	 */
	private String cnt;

}
