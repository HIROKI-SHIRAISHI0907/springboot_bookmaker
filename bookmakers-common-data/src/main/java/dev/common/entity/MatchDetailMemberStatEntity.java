package dev.common.entity;

import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 試合詳細: 選手スタッツ 全タブまとめ 選手1人1行（memberStat_*.csv）
 * <p>試合詳細スクレイピング（B016: MatchDetailData_aws_S3.py）が aws-s3-detail-data-csv に出力した CSV の 1 行。
 * 値は CSV のまま文字列で持つ（値が無いときの「-」もそのまま）。</p>
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MatchDetailMemberStatEntity extends MetaEntity implements Serializable {

	/** シリアライズ化 */
	private static final long serialVersionUID = 1L;

	/** ファイル（S3 キー） */
	private String file;

	/** 試合ID */
	private String matchId;

	/** 試合日 */
	private String matchDate;

	/** ホームチーム */
	private String homeTeamName;

	/** アウェーチーム */
	private String awayTeamName;

	/** 選手ID */
	private String playerId;

	/** 選手名 */
	private String playerName;

	/** ポジション */
	private String position;

	/** 側 */
	private String side;

	/** チーム */
	private String teamName;

	/** Rating（FS_RATING） */
	private String fsRating;

	/** 合計シュート（SHOTS_TOTAL） */
	private String shotsTotal;

	/** ゴール期待値（xG）（EXPECTED_GOALS） */
	private String expectedGoals;

	/** 正確なパス（PASSES_ACCURATE） */
	private String passesAccurate;

	/** ボールタッチ回数（TOUCHES_TOTAL） */
	private String touchesTotal;

	/** 相手ボックス内でのタッチ（TOUCHES_BOX_OPPOSITE） */
	private String touchesBoxOpposite;

	/** ドリブル成功回数（DRIBBLES_WON） */
	private String dribblesWon;

	/** デュエル（DUELS_TOTAL） */
	private String duelsTotal;

	/** ゴール（GOALS） */
	private String goals;

	/** 枠内ゴール期待値（xGOT）（EXPECTED_GOALS_ON_TARGET） */
	private String expectedGoalsOnTarget;

	/** 枠内シュート（SHOTS_ON_TARGET） */
	private String shotsOnTarget;

	/** 枠外シュート（SHOTS_OFF_TARGET） */
	private String shotsOffTarget;

	/** シュートブロック（SHOTS_BLOCKED） */
	private String shotsBlocked;

	/** ボックス内からのシュート（SHOTS_BOX_IN） */
	private String shotsBoxIn;

	/** ボックス外からのシュート（SHOTS_BOX_OUT） */
	private String shotsBoxOut;

	/** ヘディングシュート（SHOTS_HEAD） */
	private String shotsHead;

	/** 決定的チャンスでのミス（BIG_CHANCES_MISSED） */
	private String bigChancesMissed;

	/** 被ファウル数（FOULS_SUFFERED） */
	private String foulsSuffered;

	/** オフサイド（OFFSIDES） */
	private String offsides;

	/** 決定的チャンス提供数（BIG_CHANCES_CREATED） */
	private String bigChancesCreated;

	/** キーパス（KEY_PASSES） */
	private String keyPasses;

	/** アシスト（ASSISTS_GOAL） */
	private String assistsGoal;

	/** アシスト期待値（xA）（EXPECTED_ASSISTS） */
	private String expectedAssists;

	/** 正確なファイナルサードへのパス（PASSES_FINAL_THIRD_ACCURATE） */
	private String passesFinalThirdAccurate;

	/** 正確なロングパス（LONG_BALLS_ACCURATE） */
	private String longBallsAccurate;

	/** 正確なクロス（CROSSES_ACCURATE） */
	private String crossesAccurate;

	/** デュエル勝利数（DUELS_WON） */
	private String duelsWon;

	/** 空中戦勝利数（DUELS_AERIAL_WON） */
	private String duelsAerialWon;

	/** 地上戦勝利数（DUELS_GROUND_WON） */
	private String duelsGroundWon;

	/** タックル成功数（TACKLES_WON） */
	private String tacklesWon;

	/** ファウル（FOULS_COMMITTED） */
	private String foulsCommitted;

	/** インターセプト（INTERCEPTIONS） */
	private String interceptions;

	/** クリアリング（CLEARANCES） */
	private String clearances;

	/** 失点につながるミス（ERRORS_LEAD_TO_GOAL） */
	private String errorsLeadToGoal;

	/** シュートにつながるミス（ERRORS_LEAD_TO_SHOT） */
	private String errorsLeadToShot;

	/** プレー時間（MATCH_MINUTES_PLAYED） */
	private String matchMinutesPlayed;

	/** オウンゴール（GOALS_OWN） */
	private String goalsOwn;

	/** イエローカード（CARDS_YELLOW） */
	private String cardsYellow;

	/** レッドカード（CARDS_RED） */
	private String cardsRed;

	/** キーパーセーブ（SAVES_TOTAL） */
	private String savesTotal;

	/** 失点数（GOALS_CONCEDED） */
	private String goalsConceded;

	/** ゴール阻止数（GOALS_PREVENTED） */
	private String goalsPrevented;

	/** 被xGOT（EXPECTED_GOALS_ON_TARGET_FACED） */
	private String expectedGoalsOnTargetFaced;

	/** パンチ（PUNCHES_TOTAL） */
	private String punchesTotal;

	/** キーパースロー（KEEPER_THROWS_TOTAL） */
	private String keeperThrowsTotal;

	/** PA外での守備（KEEPER_SWEEPER_TOTAL） */
	private String keeperSweeperTotal;

	/** 上に無い列キー（flashscore 側で増えた項目）。「列キー=値」をセミコロン区切りで持つ */
	private String extraStats;

}
