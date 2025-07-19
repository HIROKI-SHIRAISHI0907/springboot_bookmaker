package dev.application.analyze.bm_m017_bm_m018;

import lombok.Data;

/**
 * 全体情報保持データ
 * @author shiraishitoshio
 *
 */
@Data
public class LeagueScoreMainData {

    /** sumScoreValue */
    private String sumScoreValue;

    /** timeRangeArea */
    private String timeRangeArea;

    /** homeSumScoreValue */
    private String homeScoreValue;

    /** awaySumScoreValue */
    private String awayScoreValue;

    /** homeTimeRangeArea */
    private String homeTimeRangeArea;

    /** awayTimeRangeArea */
    private String awayTimeRangeArea;

	/** target */
    private String target;

    /** search */
    private String search;

    /** table */
    private String table;

    /**
     * コンストラクタ
     * @param sumScoreValue 合計スコア
     * @param timeRangeArea 時間範囲
     * @param homeScoreValue ホームスコア
     * @param awayScoreValue アウェースコア
     * @param homeTimeRangeArea ホーム時間範囲
     * @param awayTimeRangeArea アウェー時間範囲
     * @param target 対象数
     * @param search 探索数
     * @param table テーブル
     */
    public LeagueScoreMainData(String sumScoreValue, String timeRangeArea, String homeScoreValue
    		, String awayScoreValue, String homeTimeRangeArea, String awayTimeRangeArea,
    		String target, String search, String table) {
    	this.sumScoreValue = sumScoreValue;
    	this.timeRangeArea = timeRangeArea;
    	this.homeScoreValue = homeScoreValue;
    	this.awayScoreValue = awayScoreValue;
    	this.homeTimeRangeArea = homeTimeRangeArea;
    	this.awayTimeRangeArea = awayTimeRangeArea;
        this.target = target;
        this.search = search;
        this.table = table;
    }

}
