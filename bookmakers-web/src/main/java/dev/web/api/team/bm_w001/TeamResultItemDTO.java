package dev.web.api.team.bm_w001;

import lombok.Data;

/**
 * チームの過去の1試合（画面用・チーム視点）
 * @author shiraishitoshio
 */
@Data
public class TeamResultItemDTO {

	/** ラウンド */
	private int roundNo;

	/** OVERVIEW（集計済み）/ STATIC（終了済の行から）/ NONE（データなし＝空欄） */
	private String source;

	/** 対戦相手（NONE は null） */
	private String opponent;

	/** H / A（NONE は null） */
	private String homeAway;

	private Integer goalsFor;

	private Integer goalsAgainst;

	/** W / D / L（スコアが分からなければ null） */
	private String result;

	/** PK 決着 */
	private boolean pk;

	private Integer pkGoalsFor;

	private Integer pkGoalsAgainst;

	/** 試合時刻（JST "yyyy-MM-dd HH:mm:ss"） */
	private String matchTime;

	/** 試合詳細（/match-detail?seqKey=）へのキー。分からなければ null（2026-11 追加） */
	private String seqKey;

	/** 試合ID（Flashscore の mid。分からなければ null） */
	private String matchId;

	/** 統計詳細データ（match_detail_*）がある（2026-11 追加） */
	private boolean hasDetail;
}
