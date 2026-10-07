package dev.web.api.dashboard.bm_w003;

import dev.web.api.dashboard.bm_w002.DashboardForecastDTO;
import dev.web.api.dashboard.teamDTO.DashboardTeamDTO;
import lombok.Data;

/**
 * ライブの1試合
 * @author shiraishitoshio
 *
 */
@Data
public class DashboardLiveMatchDTO {

	/** data テーブルの seq（試合詳細へ遷移するときに使う） */
	private Long seq;

	/** マッチID */
	private String matchId;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** 表示用「国 / リーグ」 */
	private String leagueLabel;

	/** 表示用「ラウンド N」（無ければ null） */
	private String roundLabel;

	/** 試合時間の元の値（"67'" "ハーフタイム" など） */
	private String times;

	/** 表示用 LIVE / HT */
	private String status;

	/** 表示用の経過時間（"67'"。HT は "HT"） */
	private String minuteLabel;

	/** 進み具合（0〜100） */
	private Integer progress;

	private Integer homeScore;

	private Integer awayScore;

	/** 前半のスコア（分からなければ null） */
	private Integer halftimeHomeScore;

	private Integer halftimeAwayScore;

	private DashboardTeamDTO home;

	private DashboardTeamDTO away;

	/** ゴール期待値 */
	private Double homeXg;

	private Double awayXg;

	/** 枠内シュート */
	private Integer homeShotsOnTarget;

	private Integer awayShotsOnTarget;

	/** ポゼッション（%） */
	private Integer homePossession;

	private Integer awayPossession;

	/** データの記録時刻（ISO-8601） */
	private String recordTime;

	private DashboardForecastDTO forecast;
}
