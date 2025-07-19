package dev.application.analyze.bm_m007_bm_m016;

import lombok.Data;

/**
 * 登録保持データ
 * @author shiraishitoshio
 *
 */
@Data
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

    /**
     * コンストラクタ
     * @param country 国
     * @param league リーグ
     * @param timeRange 時間範囲
     * @param feature 特徴量
     * @param thresHold 閾値
     * @param target 対象数
     * @param search 探索数
     * @param table テーブル
     */
    public TeamRangeRegisterData(String country, String league, String timeRange, String feature,
    		String thresHold, String target, String search, String table) {
    	this.country = country;
    	this.league = league;
    	this.timeRange = timeRange;
    	this.feature = feature;
    	this.thresHold = thresHold;
        this.target = target;
        this.search = search;
        this.table = table;
    }

}
