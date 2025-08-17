package dev.application.analyze.bm_m007_bm_m016;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登録保持データ
 * @author shiraishitoshio
 *
 */
@Data
@AllArgsConstructor
public class TeamRangeRegisterData {

	/** country */
    private String country;

    /** league */
    private String league;

    /** timeRange */
    private String timeRange;

    /** feature */
    private String feature;

    /** thresHold */
    private String thresHold;

	/** target */
    private String target;

    /** search */
    private String search;

    /** table */
    private String table;

}
