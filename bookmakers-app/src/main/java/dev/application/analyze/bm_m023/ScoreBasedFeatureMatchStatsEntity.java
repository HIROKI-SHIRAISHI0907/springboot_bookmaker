package dev.application.analyze.bm_m023;

import java.math.BigDecimal;
import java.sql.Timestamp;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * score_based_feature_match_stats テーブルに対応するエンティティ（BM_M023 / BM_M026 共通の明細）。
 *
 * <h2>何を表すクラスか</h2>
 * <p>
 * 1行 = 試合終了した1試合 × 区分（ALL / 1st / 2nd / スコア）× 特徴量。
 * ホーム・アウェーそれぞれについて、区分内のスナップショットの値の件数・Σx・Σx²・Σx³・Σx⁴・最小・最大と、
 * 値があった時点の試合時間（分）の件数・Σ・Σ²・最小・最大を持つ。
 * </p>
 * <p>
 * Σ は足し合わせられるので、リーグ・チーム・カード単位の平均・標準偏差・歪度・尖度はビューで正確に計算できる
 * （score_based_feature_stats / each_team_score_based_feature_stats / card_score_based_feature_stats）。
 * Σ は BigDecimal / NUMERIC で持つため、足し合わせても誤差が出ない。
 * </p>
 * <p>
 * (season, country, league, homeTeamName, awayTeamName, chkBody, feature) で一意。
 * </p>
 * <p>
 * roundNo / recordTime を持つため、「ラウンド N 終了時点」「日付 X 時点」の統計や推移も明細から計算できる
 * （旧 BM_M023H / BM_M026H の履歴コピーは不要になった）。
 * </p>
 *
 * @author shiraishitoshio
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ScoreBasedFeatureMatchStatsEntity extends MetaEntity {

	/** seq（主キー。「<シーズン>-<6桁枝番>」。Writer で seq_counter から採番） */
	private String seq;

	/** シーズン（Writer で設定） */
	private String season;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** ホームチーム */
	private String homeTeamName;

	/** アウェーチーム */
	private String awayTeamName;

	/** マッチID（参照用） */
	private String matchId;

	/** ラウンド番号（キーの「ラウンド N」の N。取れなければ null） */
	private Integer roundNo;

	/** 試合終了（FIN）行の記録時間（取れなければ null） */
	private Timestamp recordTime;

	/** 得点あり / 得点なし（試合終了時のスコア） */
	private String situation;

	/** 区分: ALL / 1st / 2nd / スコア（例: 1-0） */
	private String chkBody;

	/** 特徴量（ScoreBasedFeature#getFeatureName） */
	private String feature;

	/** 特徴量の並び順 */
	private Integer featureOrder;

	/** ホーム 値がある観測数 */
	private Integer homeN;

	/** ホーム Σx */
	private BigDecimal homeS1;

	/** ホーム Σx² */
	private BigDecimal homeS2;

	/** ホーム Σx³ */
	private BigDecimal homeS3;

	/** ホーム Σx⁴ */
	private BigDecimal homeS4;

	/** ホーム 最小 */
	private BigDecimal homeMin;

	/** ホーム 最大 */
	private BigDecimal homeMax;

	/** ホーム 時間: 観測数 */
	private Integer homeTn;

	/** ホーム 時間: Σ分 */
	private BigDecimal homeTs1;

	/** ホーム 時間: Σ分² */
	private BigDecimal homeTs2;

	/** ホーム 時間: 最小（分） */
	private BigDecimal homeTmin;

	/** ホーム 時間: 最大（分） */
	private BigDecimal homeTmax;

	/** アウェー 値がある観測数 */
	private Integer awayN;

	/** アウェー Σx */
	private BigDecimal awayS1;

	/** アウェー Σx² */
	private BigDecimal awayS2;

	/** アウェー Σx³ */
	private BigDecimal awayS3;

	/** アウェー Σx⁴ */
	private BigDecimal awayS4;

	/** アウェー 最小 */
	private BigDecimal awayMin;

	/** アウェー 最大 */
	private BigDecimal awayMax;

	/** アウェー 時間: 観測数 */
	private Integer awayTn;

	/** アウェー 時間: Σ分 */
	private BigDecimal awayTs1;

	/** アウェー 時間: Σ分² */
	private BigDecimal awayTs2;

	/** アウェー 時間: 最小（分） */
	private BigDecimal awayTmin;

	/** アウェー 時間: 最大（分） */
	private BigDecimal awayTmax;
}