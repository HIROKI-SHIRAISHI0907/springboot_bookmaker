package dev.application.analyze.bm_m018;

import java.sql.Timestamp;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * classify_result_data テーブルに対応するエンティティ（BM_M019 分類別の試合スナップショット）。
 *
 * <h2>何を表すクラスか</h2>
 * <p>
 * 試合終了した試合の、ある時点の BookDataEntity の全項目＋その試合の分類モード。1試合につき次の行を持つ。
 * </p>
 * <ul>
 *   <li>KICKOFF（goalNo=0）: 試合開始時（最初の行）</li>
 *   <li>GOAL（goalNo=n）: 合計 n 点目のゴールを検出した行（1回の取得で2点入った場合は、2点目の行だけ）</li>
 *   <li>HT（goalNo=その時点の合計得点）: ハーフタイムの行</li>
 *   <li>FIN（goalNo=最終の合計得点）: 試合終了の行</li>
 * </ul>
 * <p>
 * (season, country, league, homeTeamName, awayTeamName, snapshotType, goalNo) で一意。
 * BM_M020（分類モード別の試合数）は、このテーブルから数えるビュー classify_result_data_detail で出す。
 * </p>
 *
 * <h2>修正履歴</h2>
 * <ul>
 *   <li>id → seq（seq_counter 採番）。season・country・league・snapshotType・goalNo・dataSeq・matchId を追加。</li>
 *   <li>BookDataEntity にあって抜けていた項目を追加: ロングパス数・デュエル勝利数（ホーム/アウェー）・matchId。</li>
 *   <li>recordTime を String → Timestamp（RecordTimeConverter で変換）。classifyMode を String → Integer。</li>
 * </ul>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>値は元データの文字列のまま</b>（"55%"、"80% (40/50)" など）。</li>
 *   <li><b>項目名の綴り</b>（temparature / studium / probablity など）は BookDataEntity と同じ名前で自動マッピングされる。
 *       違う場合は Mapper のビルド警告に出る。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MatchClassificationResultEntity extends MetaEntity {

	/** 時点: 試合開始時 */
	public static final String SNAPSHOT_KICKOFF = "KICKOFF";

	/** 時点: 得点した時点 */
	public static final String SNAPSHOT_GOAL = "GOAL";

	/** 時点: ハーフタイム */
	public static final String SNAPSHOT_HT = "HT";

	/** 時点: 試合終了時 */
	public static final String SNAPSHOT_FIN = "FIN";

	/** seq（主キー。「<シーズン>-<6桁枝番>」。Writer で seq_counter から採番する）【変更】id から変更 */
	private String seq;

	/** シーズン（country_league_season_master.season_year。Writer で設定）【追加】 */
	private String season;

	/** 国【追加】 */
	private String country;

	/** リーグ【追加】 */
	private String league;

	/** 分類モード（ClassifyMode の番号。試合単位で同じ値）【変更】String → Integer */
	private Integer classifyMode;

	/** 時点の種類（KICKOFF: 試合開始時 / GOAL: 得点した時点 / HT: ハーフタイム / FIN: 試合終了時）【追加】 */
	private String snapshotType;

	/** その時点までの合計得点数（KICKOFF は 0、GOAL は何点目か）【追加】 */
	private Integer goalNo;

	/** 元データ（BookDataEntity）の通番【追加】 */
	private String dataSeq;

	/** マッチID【追加】 */
	private String matchId;

	/** 対戦チームカテゴリ */
	private String dataCategory;

	/** 試合時間 */
	private String times;

	/** ホーム順位 */
	private String homeRank;

	/** ホームチーム */
	private String homeTeamName;

	/** ホームスコア */
	private String homeScore;

	/** アウェー順位 */
	private String awayRank;

	/** アウェーチーム */
	private String awayTeamName;

	/** アウェースコア */
	private String awayScore;

	/** ホーム期待値 */
	private String homeExp;

	/** アウェー期待値 */
	private String awayExp;

	/** ホーム枠内ゴール期待値 */
	private String homeInGoalExp;

	/** アウェー枠内ゴール期待値 */
	private String awayInGoalExp;

	/** ホームポゼッション */
	private String homeDonation;

	/** アウェーポゼッション */
	private String awayDonation;

	/** ホームシュート数 */
	private String homeShootAll;

	/** アウェーシュート数 */
	private String awayShootAll;

	/** ホーム枠内シュート */
	private String homeShootIn;

	/** アウェー枠内シュート */
	private String awayShootIn;

	/** ホーム枠外シュート */
	private String homeShootOut;

	/** アウェー枠外シュート */
	private String awayShootOut;

	/** ホームブロックシュート */
	private String homeBlockShoot;

	/** アウェーブロックシュート */
	private String awayBlockShoot;

	/** ホームビッグチャンス */
	private String homeBigChance;

	/** アウェービッグチャンス */
	private String awayBigChance;

	/** ホームコーナーキック */
	private String homeCorner;

	/** アウェーコーナーキック */
	private String awayCorner;

	/** ホームボックス内シュート */
	private String homeBoxShootIn;

	/** アウェーボックス内シュート */
	private String awayBoxShootIn;

	/** ホームボックス外シュート */
	private String homeBoxShootOut;

	/** アウェーボックス外シュート */
	private String awayBoxShootOut;

	/** ホームゴールポスト */
	private String homeGoalPost;

	/** アウェーゴールポスト */
	private String awayGoalPost;

	/** ホームヘディングゴール */
	private String homeGoalHead;

	/** アウェーヘディングゴール */
	private String awayGoalHead;

	/** ホームキーパーセーブ */
	private String homeKeeperSave;

	/** アウェーキーパーセーブ */
	private String awayKeeperSave;

	/** ホームフリーキック */
	private String homeFreeKick;

	/** アウェーフリーキック */
	private String awayFreeKick;

	/** ホームオフサイド */
	private String homeOffside;

	/** アウェーオフサイド */
	private String awayOffside;

	/** ホームファウル */
	private String homeFoul;

	/** アウェーファウル */
	private String awayFoul;

	/** ホームイエローカード */
	private String homeYellowCard;

	/** アウェーイエローカード */
	private String awayYellowCard;

	/** ホームレッドカード */
	private String homeRedCard;

	/** アウェーレッドカード */
	private String awayRedCard;

	/** ホームスローイン */
	private String homeSlowIn;

	/** アウェースローイン */
	private String awaySlowIn;

	/** ホームボックスタッチ */
	private String homeBoxTouch;

	/** アウェーボックスタッチ */
	private String awayBoxTouch;

	/** ホームパス数 */
	private String homePassCount;

	/** アウェーパス数 */
	private String awayPassCount;

	/** ホームロングパス数【追加】 */
	private String homeLongPassCount;

	/** アウェーロングパス数【追加】 */
	private String awayLongPassCount;

	/** ホームファイナルサードパス数 */
	private String homeFinalThirdPassCount;

	/** アウェーファイナルサードパス数 */
	private String awayFinalThirdPassCount;

	/** ホームクロス数 */
	private String homeCrossCount;

	/** アウェークロス数 */
	private String awayCrossCount;

	/** ホームタックル数 */
	private String homeTackleCount;

	/** アウェータックル数 */
	private String awayTackleCount;

	/** ホームクリア数 */
	private String homeClearCount;

	/** アウェークリア数 */
	private String awayClearCount;

	/** ホームデュエル勝利数【追加】 */
	private String homeDuelCount;

	/** アウェーデュエル勝利数【追加】 */
	private String awayDuelCount;

	/** ホームインターセプト数 */
	private String homeInterceptCount;

	/** アウェーインターセプト数 */
	private String awayInterceptCount;

	/** 記録時間 */
	private Timestamp recordTime;

	/** 天気 */
	private String weather;

	/** 気温 */
	private String temparature;

	/** 湿度 */
	private String humid;

	/** 審判 */
	private String judgeMember;

	/** ホーム監督 */
	private String homeManager;

	/** アウェー監督 */
	private String awayManager;

	/** ホームフォーメーション */
	private String homeFormation;

	/** アウェーフォーメーション */
	private String awayFormation;

	/** スタジアム */
	private String studium;

	/** 収容人数 */
	private String capacity;

	/** 観客数 */
	private String audience;

	/** ホームチーム最大得点者 */
	private String homeMaxGettingScorer;

	/** アウェーチーム最大得点者 */
	private String awayMaxGettingScorer;

	/** ホームチーム最大得点者出場状況 */
	private String homeMaxGettingScorerGameSituation;

	/** アウェーチーム最大得点者出場状況 */
	private String awayMaxGettingScorerGameSituation;

	/** ホームチームホーム得点数 */
	private String homeTeamHomeScore;

	/** ホームチームホーム失点数 */
	private String homeTeamHomeLost;

	/** アウェーチームホーム得点数 */
	private String awayTeamHomeScore;

	/** アウェーチームホーム失点数 */
	private String awayTeamHomeLost;

	/** ホームチームアウェー得点数 */
	private String homeTeamAwayScore;

	/** ホームチームアウェー失点数 */
	private String homeTeamAwayLost;

	/** アウェーチームアウェー得点数 */
	private String awayTeamAwayScore;

	/** アウェーチームアウェー失点数 */
	private String awayTeamAwayLost;

	/** 通知フラグ */
	private String noticeFlg;

	/** ゴール時間 */
	private String goalTime;

	/** ゴール選手名 */
	private String goalTeamMember;

	/** 判定結果 */
	private String judge;

	/** ホームチームスタイル */
	private String homeTeamStyle;

	/** アウェーチームスタイル */
	private String awayTeamStyle;

	/** 確率 */
	private String probablity;

	/** スコア予想時間 */
	private String predictionScoreTime;
}