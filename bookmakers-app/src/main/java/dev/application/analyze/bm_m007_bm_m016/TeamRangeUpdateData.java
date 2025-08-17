package dev.application.analyze.bm_m007_bm_m016;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 更新保持データ
 * @author shiraishitoshio
 *
 */
@Data
@AllArgsConstructor
public class TeamRangeUpdateData {

	/** id */
	private String id;

	/** target */
    private String target;

    /** search */
    private String search;

    /** table */
    private String table;

}
