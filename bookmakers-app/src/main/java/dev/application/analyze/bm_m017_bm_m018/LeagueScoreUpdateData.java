package dev.application.analyze.bm_m017_bm_m018;

import lombok.Data;

/**
 * 更新保持データ
 * @author shiraishitoshio
 *
 */
@Data
public class LeagueScoreUpdateData {

	/** id */
	private String id;

	/** target */
    private String target;

    /** search */
    private String search;

    /** table */
    private String table;

    /** timeRange */
    private String timeRange;

    /**
     * コンストラクタ
     * @param id ID
     * @param target 対象数
     * @param search 探索数
     * @param table テーブル
     */
    public LeagueScoreUpdateData(String id, String target, String search, String table, String timeRange) {
    	this.id = id;
        this.target = target;
        this.search = search;
        this.table = table;
        this.timeRange = timeRange;
    }

}
