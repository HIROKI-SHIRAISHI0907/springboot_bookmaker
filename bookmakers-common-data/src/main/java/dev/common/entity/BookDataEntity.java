package dev.common.entity;

import java.io.Serializable;

import lombok.Data;

/**
 * output_通番.xlsxブックから読み込んだデータをマッピングさせるためのDTOクラス
 * @author shiraishitoshio
 *
 */
@Data
public class BookDataEntity implements Serializable {

	/** シリアライズ化 */
	private static final long serialVersionUID = 1L;

	/** 通番 */
	private String seq;

	/** 条件分岐結果通番ID */
	private String conditionResultDataSeqId;

	/** ホーム順位 */
	private String homeRank;

	/** 対戦チームカテゴリ */
	private String gameTeamCategory;

	/** 試合時間 */
	private String time;

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

	/** ホームポゼッション */
	private String homeBallPossesion;

	/** アウェーポゼッション */
	private String awayBallPossesion;

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
	private String homeShootBlocked;

	/** アウェーブロックシュート */
	private String awayShootBlocked;

	/** ホームビックチャンス */
	private String homeBigChance;

	/** アウェービックチャンス */
	private String awayBigChance;

	/** ホームコーナーキック */
	private String homeCornerKick;

	/** アウェーコーナーキック */
	private String awayCornerKick;

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
	private String homeOffSide;

	/** アウェーオフサイド */
	private String awayOffSide;

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

	/** ホームインターセプト数 */
	private String homeInterceptCount;

	/** アウェーインターセプト数 */
	private String awayInterceptCount;

	/** 記録時間 */
	private String recordTime;

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

	/** ファイルパス */
	private String filePath;

}
