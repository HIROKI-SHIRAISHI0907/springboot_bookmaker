package dev.application.analyze.bm_m003;

import lombok.Data;

/**
 * 読み込んだデータから結果マスタにマッピングさせるためのDTOクラス
 * @author shiraishitoshio
 *
 */
@Data
public class TeamMonthlyScoreSummaryEntity {

	/** 通番 */
	private String seq;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** チーム名 */
	private String teamName;

	/** ホームorアウェー */
	private String ha;

	/** 年 */
	private String year;

	/** 1月スコア数 */
	private String januaryScoreSumCount;

	/** 2月スコア数 */
	private String februaryScoreSumCount;

	/** 3月スコア数 */
	private String marchScoreSumCount;

	/** 4月スコア数 */
	private String aprilScoreSumCount;

	/** 5月スコア数 */
	private String mayScoreSumCount;

	/** 6月スコア数 */
	private String juneScoreSumCount;

	/** 7月スコア数 */
	private String julyScoreSumCount;

	/** 8月スコア数 */
	private String augustScoreSumCount;

	/** 9月スコア数 */
	private String septemberScoreSumCount;

	/** 10月スコア数 */
	private String octoberScoreSumCount;

	/** 11月スコア数 */
	private String novemberScoreSumCount;

	/** 12月スコア数 */
	private String decemberScoreSumCount;

	/** 登録ID */
	private String registerId;

	/** 登録時間 */
	private String registerTime;

	/** 更新ID */
	private String updateId;

	/** 更新時間 */
	private String updateTime;

}
