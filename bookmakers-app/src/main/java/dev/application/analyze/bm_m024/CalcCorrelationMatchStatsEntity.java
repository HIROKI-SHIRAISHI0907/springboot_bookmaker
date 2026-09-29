package dev.application.analyze.bm_m024;

import java.math.BigDecimal;
import java.sql.Timestamp;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * calc_correlation_match_stats テーブルに対応するエンティティ（BM_M024 相関分析の明細）。
 *
 * <h2>何を表すクラスか</h2>
 * <p>
 * 1行 = 試合終了した1試合 × 区分（ALL / 1st / 2nd）× 特徴量。ホーム・アウェーそれぞれについて、
 * 連続する2スナップショット（区間）ごとの組 (x, y) の件数・Σx・Σy・Σx²・Σy²・Σxy を持つ。
 * </p>
 * <ul>
 *   <li>x: 区間の終わり（後のスナップショット）の特徴量の値</li>
 *   <li>y: その区間にその側（ホーム特徴量ならホーム）が得点したか（1 / 0）。ゴール取り消し判定の区間は 0</li>
 * </ul>
 * <p>
 * Σ は足し合わせられるので、リーグ・チーム・カード単位、ラウンド推移の相関係数はビューで正確に計算できる
 * （calc_correlation_stats / each_team_calc_correlation_stats / card_calc_correlation_stats / *_trend）。
 * (season, country, league, homeTeamName, awayTeamName, chkBody, feature) で一意。
 * </p>
 *
 * @author shiraishitoshio
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class CalcCorrelationMatchStatsEntity extends MetaEntity {

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

	/** 区分: ALL / 1st / 2nd */
	private String chkBody;

	/** 特徴量（ScoreBasedFeature#getFeatureName） */
	private String feature;

	/** 特徴量の並び順 */
	private Integer featureOrder;

	/** ホーム 組の数（x と y が両方ある区間数） */
	private Integer homeN;

	/** ホーム Σx */
	private BigDecimal homeSx;

	/** ホーム Σy（得点した区間数） */
	private BigDecimal homeSy;

	/** ホーム Σx² */
	private BigDecimal homeSxx;

	/** ホーム Σy² */
	private BigDecimal homeSyy;

	/** ホーム Σxy */
	private BigDecimal homeSxy;

	/** アウェー 組の数（x と y が両方ある区間数） */
	private Integer awayN;

	/** アウェー Σx */
	private BigDecimal awaySx;

	/** アウェー Σy（得点した区間数） */
	private BigDecimal awaySy;

	/** アウェー Σx² */
	private BigDecimal awaySxx;

	/** アウェー Σy² */
	private BigDecimal awaySyy;

	/** アウェー Σxy */
	private BigDecimal awaySxy;
}