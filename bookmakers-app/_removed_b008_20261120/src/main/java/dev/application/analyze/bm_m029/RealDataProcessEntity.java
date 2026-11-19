package dev.application.analyze.bm_m029;

import java.math.BigDecimal;
import java.sql.Timestamp;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * real_data_process テーブルに対応するエンティティ（BM_M029 リアルタイム差分）。
 *
 * <h2>何を表すクラスか</h2>
 * <p>
 * 1行 = 1試合（match_id）。最新のデータと1つ前のデータを比べて「どれだけ増えたか」を持つ。
 * 新しいデータが来るたびに同じ行を上書きする（最新の差分だけを持つ）。
 * </p>
 * <ul>
 *   <li>区間: prevTimes（1つ前）〜 times（最新）。hasPrevious = false の場合は1つ前が無く、増加量は試合開始（0）からの値＝最新の累計値。</li>
 *   <li>増加量（home* / away* の数値項目）: 最新 − 1つ前。データの訂正などでマイナスになることがある。</li>
 *   <li>パス・ロングパス・ファイナルサードパス・クロス・タックル: 成功数・試行数の増加と、その区間の成功率
 *       （成功数の増加 ÷ 試行数の増加 × 100）。「38% (210/300)」の % 同士の引き算はしない。</li>
 *   <li>現在のスコア・順位・天気などの付帯情報は最新の値。</li>
 * </ul>
 * <p>
 * 数値項目は数値型。読めない値は null。旧 timeSortSeconds は不要のため削除。
 * 気温は他テーブルに合わせて temperature（DataEntity 側の綴りは temparature）。
 * </p>
 *
 * @author shiraishitoshio
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class RealDataProcessEntity extends MetaEntity {

	/** <シーズン>-<6桁枝番>（seq_counter で採番） */
	private String seq;

	/** country_league_season_master.season_year */
	private String season;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** マッチID（1試合1行のキー） */
	private String matchId;

	/** 対戦チームカテゴリ（国: リーグ - ラウンドN） */
	private String dataCategory;

	/** 試合ID */
	private String gameId;

	/** 試合リンク */
	private String gameLink;

	/** 条件分岐結果通番ID（最新側） */
	private String conditionResultDataSeqId;

	/** ホームチーム */
	private String homeTeamName;

	/** アウェーチーム */
	private String awayTeamName;

	/** ホーム順位（最新） */
	private String homeRank;

	/** アウェー順位（最新） */
	private String awayRank;

	/** 1つ前のデータがあるか（false なら差分は試合開始＝0 からの増加＝最新の累計値） */
	private Boolean hasPrevious;

	/** 区間の開始: 1つ前のデータの試合時間 */
	private String prevTimes;

	/** 区間の終了: 最新の試合時間 */
	private String times;

	/** 1つ前のデータの記録時間 */
	private Timestamp prevRecordTime;

	/** 最新の記録時間 */
	private Timestamp recordTime;

	/** ホーム 現在のスコア（最新） */
	private Integer homeCurrentScore;

	/** アウェー 現在のスコア（最新） */
	private Integer awayCurrentScore;

	/** ホーム 増加: 得点 */
	private Integer homeScore;

	/** ホーム 増加: 期待値（xG） */
	private BigDecimal homeExp;

	/** ホーム 増加: 枠内ゴール期待値 */
	private BigDecimal homeInGoalExp;

	/** ホーム 増加: ポゼッション（% の増減ポイント） */
	private BigDecimal homeDonation;

	/** ホーム 増加: シュート数 */
	private Integer homeShootAll;

	/** ホーム 増加: 枠内シュート */
	private Integer homeShootIn;

	/** ホーム 増加: 枠外シュート */
	private Integer homeShootOut;

	/** ホーム 増加: ブロックシュート */
	private Integer homeBlockShoot;

	/** ホーム 増加: ビッグチャンス */
	private Integer homeBigChance;

	/** ホーム 増加: コーナーキック */
	private Integer homeCorner;

	/** ホーム 増加: ボックス内シュート */
	private Integer homeBoxShootIn;

	/** ホーム 増加: ボックス外シュート */
	private Integer homeBoxShootOut;

	/** ホーム 増加: ゴールポスト */
	private Integer homeGoalPost;

	/** ホーム 増加: ヘディングゴール */
	private Integer homeGoalHead;

	/** ホーム 増加: キーパーセーブ */
	private Integer homeKeeperSave;

	/** ホーム 増加: フリーキック */
	private Integer homeFreeKick;

	/** ホーム 増加: オフサイド */
	private Integer homeOffside;

	/** ホーム 増加: ファウル */
	private Integer homeFoul;

	/** ホーム 増加: イエローカード */
	private Integer homeYellowCard;

	/** ホーム 増加: レッドカード */
	private Integer homeRedCard;

	/** ホーム 増加: スローイン */
	private Integer homeSlowIn;

	/** ホーム 増加: ボックスタッチ */
	private Integer homeBoxTouch;

	/** ホーム 増加: クリア数 */
	private Integer homeClearCount;

	/** ホーム 増加: デュエル数 */
	private Integer homeDuelCount;

	/** ホーム 増加: インターセプト数 */
	private Integer homeInterceptCount;

	/** ホーム 増加: パス 成功数 */
	private Integer homePassCountSuccess;

	/** ホーム 増加: パス 試行数 */
	private Integer homePassCountTry;

	/** ホーム 増加: パス 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL） */
	private BigDecimal homePassCountRate;

	/** ホーム 増加: ロングパス 成功数 */
	private Integer homeLongPassCountSuccess;

	/** ホーム 増加: ロングパス 試行数 */
	private Integer homeLongPassCountTry;

	/** ホーム 増加: ロングパス 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL） */
	private BigDecimal homeLongPassCountRate;

	/** ホーム 増加: ファイナルサードパス 成功数 */
	private Integer homeFinalThirdPassCountSuccess;

	/** ホーム 増加: ファイナルサードパス 試行数 */
	private Integer homeFinalThirdPassCountTry;

	/** ホーム 増加: ファイナルサードパス 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL） */
	private BigDecimal homeFinalThirdPassCountRate;

	/** ホーム 増加: クロス 成功数 */
	private Integer homeCrossCountSuccess;

	/** ホーム 増加: クロス 試行数 */
	private Integer homeCrossCountTry;

	/** ホーム 増加: クロス 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL） */
	private BigDecimal homeCrossCountRate;

	/** ホーム 増加: タックル 成功数 */
	private Integer homeTackleCountSuccess;

	/** ホーム 増加: タックル 試行数 */
	private Integer homeTackleCountTry;

	/** ホーム 増加: タックル 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL） */
	private BigDecimal homeTackleCountRate;

	/** アウェー 増加: 得点 */
	private Integer awayScore;

	/** アウェー 増加: 期待値（xG） */
	private BigDecimal awayExp;

	/** アウェー 増加: 枠内ゴール期待値 */
	private BigDecimal awayInGoalExp;

	/** アウェー 増加: ポゼッション（% の増減ポイント） */
	private BigDecimal awayDonation;

	/** アウェー 増加: シュート数 */
	private Integer awayShootAll;

	/** アウェー 増加: 枠内シュート */
	private Integer awayShootIn;

	/** アウェー 増加: 枠外シュート */
	private Integer awayShootOut;

	/** アウェー 増加: ブロックシュート */
	private Integer awayBlockShoot;

	/** アウェー 増加: ビッグチャンス */
	private Integer awayBigChance;

	/** アウェー 増加: コーナーキック */
	private Integer awayCorner;

	/** アウェー 増加: ボックス内シュート */
	private Integer awayBoxShootIn;

	/** アウェー 増加: ボックス外シュート */
	private Integer awayBoxShootOut;

	/** アウェー 増加: ゴールポスト */
	private Integer awayGoalPost;

	/** アウェー 増加: ヘディングゴール */
	private Integer awayGoalHead;

	/** アウェー 増加: キーパーセーブ */
	private Integer awayKeeperSave;

	/** アウェー 増加: フリーキック */
	private Integer awayFreeKick;

	/** アウェー 増加: オフサイド */
	private Integer awayOffside;

	/** アウェー 増加: ファウル */
	private Integer awayFoul;

	/** アウェー 増加: イエローカード */
	private Integer awayYellowCard;

	/** アウェー 増加: レッドカード */
	private Integer awayRedCard;

	/** アウェー 増加: スローイン */
	private Integer awaySlowIn;

	/** アウェー 増加: ボックスタッチ */
	private Integer awayBoxTouch;

	/** アウェー 増加: クリア数 */
	private Integer awayClearCount;

	/** アウェー 増加: デュエル数 */
	private Integer awayDuelCount;

	/** アウェー 増加: インターセプト数 */
	private Integer awayInterceptCount;

	/** アウェー 増加: パス 成功数 */
	private Integer awayPassCountSuccess;

	/** アウェー 増加: パス 試行数 */
	private Integer awayPassCountTry;

	/** アウェー 増加: パス 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL） */
	private BigDecimal awayPassCountRate;

	/** アウェー 増加: ロングパス 成功数 */
	private Integer awayLongPassCountSuccess;

	/** アウェー 増加: ロングパス 試行数 */
	private Integer awayLongPassCountTry;

	/** アウェー 増加: ロングパス 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL） */
	private BigDecimal awayLongPassCountRate;

	/** アウェー 増加: ファイナルサードパス 成功数 */
	private Integer awayFinalThirdPassCountSuccess;

	/** アウェー 増加: ファイナルサードパス 試行数 */
	private Integer awayFinalThirdPassCountTry;

	/** アウェー 増加: ファイナルサードパス 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL） */
	private BigDecimal awayFinalThirdPassCountRate;

	/** アウェー 増加: クロス 成功数 */
	private Integer awayCrossCountSuccess;

	/** アウェー 増加: クロス 試行数 */
	private Integer awayCrossCountTry;

	/** アウェー 増加: クロス 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL） */
	private BigDecimal awayCrossCountRate;

	/** アウェー 増加: タックル 成功数 */
	private Integer awayTackleCountSuccess;

	/** アウェー 増加: タックル 試行数 */
	private Integer awayTackleCountTry;

	/** アウェー 増加: タックル 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL） */
	private BigDecimal awayTackleCountRate;

	/** 確率の増減（数値が読めた場合） */
	private BigDecimal probablityDiff;

	/** 確率（最新） */
	private String probablity;

	/** スコア予想時間（最新） */
	private String predictionScoreTime;

	/** 天気（最新） */
	private String weather;

	/** 気温（最新） */
	private String temperature;

	/** 湿度（最新） */
	private String humid;

	/** 審判（最新） */
	private String judgeMember;

	/** ホーム監督（最新） */
	private String homeManager;

	/** アウェー監督（最新） */
	private String awayManager;

	/** ホームフォーメーション（最新） */
	private String homeFormation;

	/** アウェーフォーメーション（最新） */
	private String awayFormation;

	/** スタジアム（最新） */
	private String studium;

	/** 収容人数（最新） */
	private String capacity;

	/** 観客数（最新） */
	private String audience;

	/** 開催場所（最新） */
	private String location;

	/** ホームチーム最大得点者（最新） */
	private String homeMaxGettingScorer;

	/** アウェーチーム最大得点者（最新） */
	private String awayMaxGettingScorer;

	/** ホームチーム最大得点者出場状況（最新） */
	private String homeMaxGettingScorerGameSituation;

	/** アウェーチーム最大得点者出場状況（最新） */
	private String awayMaxGettingScorerGameSituation;

	/** ホームチームホーム得点数（最新） */
	private String homeTeamHomeScore;

	/** ホームチームホーム失点数（最新） */
	private String homeTeamHomeLost;

	/** アウェーチームホーム得点数（最新） */
	private String awayTeamHomeScore;

	/** アウェーチームホーム失点数（最新） */
	private String awayTeamHomeLost;

	/** ホームチームアウェー得点数（最新） */
	private String homeTeamAwayScore;

	/** ホームチームアウェー失点数（最新） */
	private String homeTeamAwayLost;

	/** アウェーチームアウェー得点数（最新） */
	private String awayTeamAwayScore;

	/** アウェーチームアウェー失点数（最新） */
	private String awayTeamAwayLost;

	/** 通知フラグ（最新） */
	private String noticeFlg;

	/** ゴール時間（最新） */
	private String goalTime;

	/** ゴール選手名（最新） */
	private String goalTeamMember;

	/** 判定結果（最新） */
	private String judge;

	/** ホームチームスタイル（最新） */
	private String homeTeamStyle;

	/** アウェーチームスタイル（最新） */
	private String awayTeamStyle;
}