package dev.common.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * チーム情報マスタ
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TeamMemberMasterEntity extends MetaEntity {

	/** ID */
	private String id;

	/** file */ //設定あり
	private String file;

	/** 国 */ //設定あり
	private String country;

	/** リーグ */ //設定あり
	private String league;

	/** チーム(暫定データ) */ //設定あり
	private String team;

	/** 得点数(暫定データ) */ //設定あり
	private String score;

	/** ローン保有元 */ //設定あり
	private String loanBelong;

	/** 背番号 */ //設定あり
	private String jersey;

	/** 選手名 */ //設定あり
	private String member;

	/** 顔写真パス */ //設定あり
	private String facePicPath;

	/** 所属チームリスト */
	private String belongList;

	/** 身長(今までの変化も記録) */
	private String height;

	/** 体重(今までの変化も記録) */
	private String weight;

	/** ポジション(今までの変化も記録) */ //設定あり
	private String position;

	/** 誕生日 */ //設定あり
	private String birth;

	/** 年齢 */ //設定あり
	private String age;

	/** 市場価値(今までの変化も記録) */ //設定あり
	private String marketValue;

	/** 故障情報 */ //設定あり
	private String injury;

	/** 対戦相手,得点数(対戦相手-得点数をカンマ繋ぎ) */
	private String versusTeamScoreData;

	/** 引退フラグ(0: 現役, 1:引退) */ //設定あり
	private String retireFlg;

	/** 期限付き状態(0: なし, 1:あり) */ //設定あり
	private String deadline;

	/** 期限付き契約期限 */ //設定あり
	private String deadlineContractDate;

	/** 最新情報断面日付 */ //設定あり
	private String latestInfoDate;

	/** 更新済みスタンプ */
	private String updStamp;

}
