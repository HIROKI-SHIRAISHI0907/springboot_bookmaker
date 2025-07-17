package dev.application.analyze.bm_m025;

import lombok.Data;

/**
 * 相関係数保存特徴量Mapping
 * @author shiraishitoshio
 *
 */
@Data
public class CalcCorrelationRankingEntity {

	/** ID */
	private String id;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** ホーム */
	private String home;

	/** アウェー */
	private String away;

	/** スコア */
	private String score;

	/** 導出内容 */
	private String chkBody;

	/** ランキング1位 */
	private String rank1st;

	/** ランキング2位 */
	private String rank2nd;

	/** ランキング3位 */
	private String rank3rd;

	/** ランキング4位 */
	private String rank4th;

	/** ランキング5位 */
	private String rank5th;

	/** 登録ID */
	private String registerId;

	/** 登録時間 */
	private String registerTime;

	/** 更新ID */
	private String updateId;

	/** 更新時間 */
	private String updateTime;

}
