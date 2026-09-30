package dev.application.analyze.bm_m031;

import java.sql.Timestamp;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * surface_overview_match テーブルに対応するエンティティ（BM_M031 チーム × 1試合の明細）。
 *
 * <h2>何を表すクラスか</h2>
 * <p>
 * 1行 = チーム × 1試合（1試合でホーム視点・アウェー視点の2行）。勝敗・勝ち点・得失点（前後半）・先制・リード/ビハインドの有無を持つ。
 * 月別の成績（ビュー surface_overview）、連続記録・表示文言（surface_overview_match_state / surface_overview_season）、
 * ラウンドごとの順位（surface_overview_standing）はすべてこの明細からビューで計算する。
 * </p>
 * <p>
 * (season, country, league, team, opponent, ha) で一意。同じ試合が再送されても上書きされるだけで二重にならない。
 * 欠けていた試合を後から入れると、ビューの連続記録・順位はその試合以降も含めて自動で正しくなる。
 * </p>
 * <p>旧 SurfaceOverviewEntity（月単位の累計を文字列で持つ）を置き換える。</p>
 *
 * @author shiraishitoshio
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class SurfaceOverviewMatchEntity extends MetaEntity {

	/** seq（&lt;シーズン&gt;-&lt;6桁枝番&gt;） */
	private String seq;

	/** シーズン */
	private String season;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** チーム（この行の視点） */
	private String team;

	/** 対戦相手 */
	private String opponent;

	/** H: ホーム / A: アウェー */
	private String ha;

	/** マッチID（参照用） */
	private String matchId;

	/** ラウンド番号（取れなければ null＝連続記録・順位には入らない） */
	private Integer roundNo;

	/** シーズンの総ラウンド数（マスタ。無ければ null） */
	private Integer totalRounds;

	/** 序盤/中盤/終盤（{@link Phase} の名前。総ラウンド数が無ければ null） */
	private String phase;

	/** 試合終了行の記録時間 */
	private Timestamp matchTime;

	/** 試合の年 */
	private Integer gameYear;

	/** 試合の月 */
	private Integer gameMonth;

	/** W: 勝ち / D: 引分 / L: 負け */
	private String result;

	/** PK 決着 */
	private Boolean pkFlg;

	/** この試合で得た勝ち点 */
	private Integer points;

	/** 得点（PK 戦を除く） */
	private Integer goalsFor;

	/** 失点 */
	private Integer goalsAgainst;

	/** 前半得点（ハーフタイムの行が無ければ null） */
	private Integer goalsFor1st;

	/** 後半得点 */
	private Integer goalsFor2nd;

	/** 前半失点 */
	private Integer goalsAgainst1st;

	/** 後半失点 */
	private Integer goalsAgainst2nd;

	/** 先制: T: このチーム / O: 相手 / N: 両者無得点 / U: 不明 */
	private String firstGoal;

	/** スコアの推移が分かるか（試合終了の行しか無ければ false） */
	private Boolean flowKnown;

	/** 試合中にリードした */
	private Boolean everLed;

	/** 試合中にリードされた */
	private Boolean everTrailed;

	/** 1-0（このチーム視点）になった */
	private Boolean led10;

	/** 2-0 になった */
	private Boolean led20;

	/** 0-1 になった */
	private Boolean trailed01;

	/** 0-2 になった */
	private Boolean trailed02;

	/** 元のキー（「国: リーグ - ラウンドN」。参照・調査用） */
	private String dataCategory;

	/** その試合の使えたスナップショット数（取得エラー行を除く。少ない試合は前後半・推移の精度が低い） */
	private Integer snapshotCount;

	/** PK 戦の得点（PK 決着でなければ null） */
	private Integer pkGoalsFor;

	/** PK 戦の失点 */
	private Integer pkGoalsAgainst;

	/** サイト表示の順位（試合終了行の順位。旧 BM_M033 の元データ。取れなければ null） */
	private Integer teamRank;
}