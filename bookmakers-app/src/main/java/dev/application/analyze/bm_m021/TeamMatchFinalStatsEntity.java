package dev.application.analyze.bm_m021;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * team_match_final_stats テーブルに対応するエンティティ（BM_M021 チーム視点の試合最終成績）。
 *
 * <h2>何を表すクラスか</h2>
 * <p>
 * 1行 = 試合終了した1試合の、1チーム視点の最終成績（自チームの値と対戦相手の値 opposite*）。
 * 1試合につきホームチーム視点（ha=H）とアウェーチーム視点（ha=A）の2行。
 * (season, country, league, teamName, versusTeamName, ha) で一意。
 * </p>
 *
 * <h2>修正履歴</h2>
 * <ul>
 *   <li>season / country / league / matchId / goalsFor / goalsAgainst を追加（別リーグの同名チームの区別・seq 採番・シーズン削除のため）。</li>
 *   <li>値を文字列から数値に変更（回数は Integer、期待値・率・ポゼッションは Double。"%" は付けない）。</li>
 *   <li>順位は数字だけ取り出して Integer（取れなければ null）。</li>
 * </ul>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>値は FIN 行（試合終了時点の累計）</b>。元データに無い項目は null。</li>
 *   <li><b>天気・気温・湿度は元データの文字列のまま</b>。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TeamMatchFinalStatsEntity extends MetaEntity {

	/** <シーズン>-<6桁枝番>（seq_counter で採番） */
	private String seq;

	/** country_league_season_master.season_year */
	private String season;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** ラウンド番号（キーの「ラウンド N」。同じ対戦が複数回あるリーグで試合を区別するため一意キーに含む） */
	private Integer roundNo;

	/** チーム（この行の視点） */
	private String teamName;

	/** 対戦チーム */
	private String versusTeamName;

	/** 対戦場所 H:ホーム / A:アウェー */
	private String ha;

	/** マッチID（参照用） */
	private String matchId;

	/** 得点 */
	private Integer goalsFor;

	/** 失点 */
	private Integer goalsAgainst;

	/** スコア表示（勝敗記号＋自チーム得点-相手得点。例: ○2-1） */
	private String score;

	/** 結果 WIN / LOSE / DRAW */
	private String result;

	/** 試合終了時点の順位（数字が取れなければ NULL） */
	private Integer gameFinRank;

	/** 対戦相手の試合終了時点の順位 */
	private Integer oppositeGameFinRank;

	/** 期待値（xG） */
	private Double exp;

	/** 対戦相手期待値（xG） */
	private Double oppositeExp;

	/** 枠内ゴール期待値 */
	private Double inGoalExp;

	/** 対戦相手枠内ゴール期待値 */
	private Double oppositeInGoalExp;

	/** ポゼッション（%） */
	private Double donation;

	/** 対戦相手ポゼッション（%） */
	private Double oppositeDonation;

	/** シュート数 */
	private Integer shootAll;

	/** 対戦相手シュート数 */
	private Integer oppositeShootAll;

	/** 枠内シュート */
	private Integer shootIn;

	/** 対戦相手枠内シュート */
	private Integer oppositeShootIn;

	/** 枠外シュート */
	private Integer shootOut;

	/** 対戦相手枠外シュート */
	private Integer oppositeShootOut;

	/** ブロックシュート */
	private Integer blockShoot;

	/** 対戦相手ブロックシュート */
	private Integer oppositeBlockShoot;

	/** ビッグチャンス */
	private Integer bigChance;

	/** 対戦相手ビッグチャンス */
	private Integer oppositeBigChance;

	/** コーナーキック */
	private Integer corner;

	/** 対戦相手コーナーキック */
	private Integer oppositeCorner;

	/** ボックス内シュート */
	private Integer boxShootIn;

	/** 対戦相手ボックス内シュート */
	private Integer oppositeBoxShootIn;

	/** ボックス外シュート */
	private Integer boxShootOut;

	/** 対戦相手ボックス外シュート */
	private Integer oppositeBoxShootOut;

	/** ゴールポスト */
	private Integer goalPost;

	/** 対戦相手ゴールポスト */
	private Integer oppositeGoalPost;

	/** ヘディングゴール */
	private Integer goalHead;

	/** 対戦相手ヘディングゴール */
	private Integer oppositeGoalHead;

	/** キーパーセーブ */
	private Integer keeperSave;

	/** 対戦相手キーパーセーブ */
	private Integer oppositeKeeperSave;

	/** フリーキック */
	private Integer freeKick;

	/** 対戦相手フリーキック */
	private Integer oppositeFreeKick;

	/** オフサイド */
	private Integer offside;

	/** 対戦相手オフサイド */
	private Integer oppositeOffside;

	/** ファウル */
	private Integer foul;

	/** 対戦相手ファウル */
	private Integer oppositeFoul;

	/** イエローカード */
	private Integer yellowCard;

	/** 対戦相手イエローカード */
	private Integer oppositeYellowCard;

	/** レッドカード */
	private Integer redCard;

	/** 対戦相手レッドカード */
	private Integer oppositeRedCard;

	/** スローイン */
	private Integer slowIn;

	/** 対戦相手スローイン */
	private Integer oppositeSlowIn;

	/** ボックスタッチ */
	private Integer boxTouch;

	/** 対戦相手ボックスタッチ */
	private Integer oppositeBoxTouch;

	/** パス_成功率（%） */
	private Double passCountSuccessRatio;

	/** パス_成功数 */
	private Integer passCountSuccessCount;

	/** パス_試行数 */
	private Integer passCountTryCount;

	/** 対戦相手パス_成功率（%） */
	private Double oppositePassCountSuccessRatio;

	/** 対戦相手パス_成功数 */
	private Integer oppositePassCountSuccessCount;

	/** 対戦相手パス_試行数 */
	private Integer oppositePassCountTryCount;

	/** ロングパス_成功率（%） */
	private Double longPassCountSuccessRatio;

	/** ロングパス_成功数 */
	private Integer longPassCountSuccessCount;

	/** ロングパス_試行数 */
	private Integer longPassCountTryCount;

	/** 対戦相手ロングパス_成功率（%） */
	private Double oppositeLongPassCountSuccessRatio;

	/** 対戦相手ロングパス_成功数 */
	private Integer oppositeLongPassCountSuccessCount;

	/** 対戦相手ロングパス_試行数 */
	private Integer oppositeLongPassCountTryCount;

	/** ファイナルサードパス_成功率（%） */
	private Double finalThirdPassCountSuccessRatio;

	/** ファイナルサードパス_成功数 */
	private Integer finalThirdPassCountSuccessCount;

	/** ファイナルサードパス_試行数 */
	private Integer finalThirdPassCountTryCount;

	/** 対戦相手ファイナルサードパス_成功率（%） */
	private Double oppositeFinalThirdPassCountSuccessRatio;

	/** 対戦相手ファイナルサードパス_成功数 */
	private Integer oppositeFinalThirdPassCountSuccessCount;

	/** 対戦相手ファイナルサードパス_試行数 */
	private Integer oppositeFinalThirdPassCountTryCount;

	/** クロス_成功率（%） */
	private Double crossCountSuccessRatio;

	/** クロス_成功数 */
	private Integer crossCountSuccessCount;

	/** クロス_試行数 */
	private Integer crossCountTryCount;

	/** 対戦相手クロス_成功率（%） */
	private Double oppositeCrossCountSuccessRatio;

	/** 対戦相手クロス_成功数 */
	private Integer oppositeCrossCountSuccessCount;

	/** 対戦相手クロス_試行数 */
	private Integer oppositeCrossCountTryCount;

	/** タックル_成功率（%） */
	private Double tackleCountSuccessRatio;

	/** タックル_成功数 */
	private Integer tackleCountSuccessCount;

	/** タックル_試行数 */
	private Integer tackleCountTryCount;

	/** 対戦相手タックル_成功率（%） */
	private Double oppositeTackleCountSuccessRatio;

	/** 対戦相手タックル_成功数 */
	private Integer oppositeTackleCountSuccessCount;

	/** 対戦相手タックル_試行数 */
	private Integer oppositeTackleCountTryCount;

	/** クリア数 */
	private Integer clearCount;

	/** 対戦相手クリア数 */
	private Integer oppositeClearCount;

	/** デュエル勝利数 */
	private Integer duelCount;

	/** 対戦相手デュエル勝利数 */
	private Integer oppositeDuelCount;

	/** インターセプト数 */
	private Integer interceptCount;

	/** 対戦相手インターセプト数 */
	private Integer oppositeInterceptCount;

	/** 天気 */
	private String weather;

	/** 気温 */
	private String temperature;

	/** 湿度 */
	private String humid;
}
