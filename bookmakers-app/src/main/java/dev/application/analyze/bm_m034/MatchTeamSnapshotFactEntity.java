package dev.application.analyze.bm_m034;

import java.math.BigDecimal;
import java.sql.Timestamp;

import dev.application.analyze.common.entity.AbstractMatchTeamContextEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * match_team_snapshot_fact テーブルに対応するエンティティ（BM_M034 試合中スナップショット）。
 *
 * <h2>何を表すクラスか</h2>
 * <p>
 * 1行 = 1試合 × 1チーム視点 × 1時点（元データ1行 → ホーム視点・アウェー視点の2行）。その時点の累計値を持つ。
 * リアルタイム予測・モメンタム分析・得点確率分析の元データ。
 * </p>
 * <ul>
 *   <li>(data_seq, ha) で一意。同じ時点が再送されても上書きされるだけで重複しない。</li>
 *   <li>時点の並び順は dataSeq（元データの通番）。matchMinute は読めない表記だと null なので並び順には使わない。</li>
 *   <li>パス・ロングパス・ファイナルサードパス・クロス・タックルは「38% (210/300)」を成功数・試行数・成功率に分けて持つ。</li>
 *   <li>直前の時点との差分はビュー match_team_snapshot_diff、各試合の最新の差分は match_team_snapshot_latest（旧 BM_M029）。</li>
 * </ul>
 * <p>数値項目は読めなければ null。% の項目は 0〜100（他テーブルと同じ単位）。</p>
 *
 * @author shiraishitoshio
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MatchTeamSnapshotFactEntity extends AbstractMatchTeamContextEntity {

	/** 元データ（data テーブル）の通番。時点の並び順と一意キーに使う */
	private Long dataSeq;

	/** ラウンド番号（キーの「ラウンド N」） */
	private Integer roundNo;

	/** 1: 前半 / 2: 後半（ハーフタイム行の前後で判定。ハーフタイム行がまだ無ければ null） */
	private Integer half;

	/** 表示上の試合時間（例: 23'、45+2'、ハーフタイム、終了済） */
	private String matchTimeLabel;

	/** 試合時間（分）。読めない表記は null（0 分にはしない） */
	private BigDecimal matchMinute;

	/** 試合終了（FIN）の行か */
	private Boolean finFlg;

	/** 記録時間 */
	private Timestamp recordTime;

	/** この時点の自チーム得点 */
	private Integer teamScore;

	/** この時点の相手得点 */
	private Integer opponentScore;

	/** スコア差（自 − 相手） */
	private Integer scoreDiff;

	/** ポゼッション（%） */
	private BigDecimal possession;

	/** 期待値（xG）（累計） */
	private BigDecimal exp;

	/** 枠内ゴール期待値（累計） */
	private BigDecimal inGoalExp;

	/** シュート数（累計） */
	private Integer shootAll;

	/** 枠内シュート（累計） */
	private Integer shootIn;

	/** 枠外シュート（累計） */
	private Integer shootOut;

	/** ブロックシュート（累計） */
	private Integer blockShoot;

	/** ビッグチャンス（累計） */
	private Integer bigChance;

	/** コーナーキック（累計） */
	private Integer corner;

	/** ボックス内シュート（累計） */
	private Integer boxShootIn;

	/** ボックス外シュート（累計） */
	private Integer boxShootOut;

	/** ゴールポスト（累計） */
	private Integer goalPost;

	/** ヘディングゴール（累計） */
	private Integer goalHead;

	/** キーパーセーブ（累計） */
	private Integer keeperSave;

	/** フリーキック（累計） */
	private Integer freeKick;

	/** オフサイド（累計） */
	private Integer offside;

	/** ファウル（累計） */
	private Integer foul;

	/** イエローカード（累計） */
	private Integer yellowCard;

	/** レッドカード（累計） */
	private Integer redCard;

	/** スローイン（累計） */
	private Integer slowIn;

	/** ボックスタッチ（累計） */
	private Integer boxTouch;

	/** クリア数（累計） */
	private Integer clearCount;

	/** デュエル勝利数（累計） */
	private Integer duelCount;

	/** インターセプト数（累計） */
	private Integer interceptCount;

	/** パス 成功数（累計） */
	private Integer passCountSuccess;

	/** パス 試行数（累計） */
	private Integer passCountTry;

	/** パス 成功率（%。累計） */
	private BigDecimal passCountRate;

	/** ロングパス 成功数（累計） */
	private Integer longPassCountSuccess;

	/** ロングパス 試行数（累計） */
	private Integer longPassCountTry;

	/** ロングパス 成功率（%。累計） */
	private BigDecimal longPassCountRate;

	/** ファイナルサードパス 成功数（累計） */
	private Integer finalThirdPassCountSuccess;

	/** ファイナルサードパス 試行数（累計） */
	private Integer finalThirdPassCountTry;

	/** ファイナルサードパス 成功率（%。累計） */
	private BigDecimal finalThirdPassCountRate;

	/** クロス 成功数（累計） */
	private Integer crossCountSuccess;

	/** クロス 試行数（累計） */
	private Integer crossCountTry;

	/** クロス 成功率（%。累計） */
	private BigDecimal crossCountRate;

	/** タックル 成功数（累計） */
	private Integer tackleCountSuccess;

	/** タックル 試行数（累計） */
	private Integer tackleCountTry;

	/** タックル 成功率（%。累計） */
	private BigDecimal tackleCountRate;
}