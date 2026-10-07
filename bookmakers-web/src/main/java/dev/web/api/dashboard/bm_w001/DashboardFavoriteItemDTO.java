package dev.web.api.dashboard.bm_w001;

import lombok.Data;

/**
 * お気に入りチーム1件（ライブ中ならその試合、無ければ次の試合）
 * @author shiraishitoshio
 *
 */
@Data
public class DashboardFavoriteItemDTO {

	private String teamName;

	private String country;

	private String league;

	private Integer rank;

	/** LIVE / NEXT / NONE（予定なし） */
	private String status;

	/** 相手チーム */
	private String opponent;

	/** H=ホーム / A=アウェー */
	private String homeAway;

	/** LIVE: 経過時間（"67'" / "HT"） */
	private String minuteLabel;

	/** LIVE: 自チームの得点・相手の得点 */
	private Integer teamScore;

	private Integer opponentScore;

	/** NEXT: キックオフ（ISO-8601） */
	private String kickoff;

	/** 勝つ確率（%） */
	private Integer winProb;

	/** LIVE: 試合詳細への遷移用 */
	private Long seq;
}
