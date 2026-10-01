package dev.application.analyze.bm_m017;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * league_score_goal_event テーブルに対応するエンティティ（BM_M017 / BM_M018 共通）。
 *
 * <h2>何を表すクラスか</h2>
 * <p>
 * 1行 = 試合終了（FIN）した1試合の1ゴール。「その試合の何点目か・どちらが取ったか・得点後のスコア・時間帯」を持つ。
 * (season, country, league, roundNo, homeTeamName, awayTeamName, goalNo) で一意。
 * 【変更】同じ対戦がシーズン中に複数回あるリーグ（スイス・スコットランドなど）で試合を区別するため、ラウンド番号を一意キーに追加。
 * </p>
 * <p>
 * 件数・割合はテーブルに持たず、ビューで出す。
 * </p>
 * <ul>
 *   <li>BM_M017: league_score_time_band_stats（合計N点目 × 時間帯。goalNo = 合計N点目）</li>
 *   <li>BM_M018: league_score_time_band_stats_split_score（得点側 × 得点後スコア × 時間帯）</li>
 * </ul>
 *
 * <h2>統合したクラス</h2>
 * <p>
 * 旧 LeagueScoreTimeBandStatsEntity / LeagueScoreTimeBandStatsSplitScoreEntity / LeagueScoreMainData /
 * LeagueScoreRegisterData / LeagueScoreUpdateData / LeagueScoreTimeBandOutputDTO をこのクラス1つにまとめた。
 * </p>
 *
 * @author shiraishitoshio
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class LeagueScoreGoalEventEntity extends MetaEntity {

	/** 得点側: ホーム */
	public static final String SIDE_HOME = "H";

	/** 得点側: アウェー */
	public static final String SIDE_AWAY = "A";

	/** seq（主キー。「<シーズン>-<6桁枝番>」。Writer で seq_counter から採番する） */
	private String seq;

	/** シーズン（country_league_season_master.season_year。Writer で設定） */
	private String season;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** ホームチーム */
	private String homeTeamName;

	/** アウェーチーム */
	private String awayTeamName;

	/** ラウンド番号（キーの「ラウンド N」の N） */
	private Integer roundNo;

	/** マッチID（参照用） */
	private String matchId;

	/** その試合の何点目か（合計。1〜） */
	private Integer goalNo;

	/** 得点した側（H / A） */
	private String scoredSide;

	/** 得点後のホームスコア */
	private Integer homeScoreValue;

	/** 得点後のアウェースコア */
	private Integer awayScoreValue;

	/** 時間帯（ExecuteMainUtil.classifyMatchTime の値: 0〜10, …, 90〜） */
	private String timeRangeArea;

	/** 時間帯の並び順（0〜10） */
	private Integer timeBandOrder;

	/** 得点を検出した行の試合時間（参照用） */
	private String goalTimes;
}
