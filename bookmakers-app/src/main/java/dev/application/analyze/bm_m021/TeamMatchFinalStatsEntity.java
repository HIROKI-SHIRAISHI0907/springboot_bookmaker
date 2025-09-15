package dev.application.analyze.bm_m021;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * DBデータをマッピングさせるためのDTOクラス
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TeamMatchFinalStatsEntity extends MetaEntity {

	/** 通番 */
	private String seq;

	/** チーム */
	private String teamName;

	/** 対戦チーム */
	private String versusTeamName;

	/** 対戦場所 */
	private String ha;

	/** スコア */
	private String score;

	/** 結果 */
	private String result;

	/** 途中順位 */
	private String gameFinRank;

	/** 対戦相手途中順位 */
	private String oppositeGameFinRank;

	/** 期待値 */
	private String exp;

	/** 対戦相手期待値 */
	private String oppositeExp;

	/** ホーム枠内ゴール期待値 */
	private String inGoalExp;

	/** 対戦相手枠内ゴール期待値 */
	private String oppositeInGoalExp;

	/** ポゼッション */
	private String donation;

	/** 対戦相手ポゼッション */
	private String oppositeDonation;

	/** シュート数 */
	private String shootAll;

	/** 対戦相手シュート数 */
	private String oppositeShootAll;

	/** 枠内シュート */
	private String shootIn;

	/** 対戦相手枠内シュート */
	private String oppositeShootIn;

	/** 枠外シュート */
	private String shootOut;

	/** 対戦相手枠外シュート */
	private String oppositeShootOut;

	/** ブロックシュート */
	private String blockShoot;

	/** 対戦相手ブロックシュート */
	private String oppositeBlockShoot;

	/** ビックチャンス */
	private String bigChance;

	/** 対戦相手ビックチャンス */
	private String oppositeBigChance;

	/** コーナーキック */
	private String corner;

	/** 対戦相手コーナーキック */
	private String oppositeCorner;

	/** ボックス内シュート */
	private String boxShootIn;

	/** 対戦相手ボックス内シュート */
	private String oppositeBoxShootIn;

	/** ボックス外シュート */
	private String boxShootOut;

	/** 対戦相手ボックス外シュート */
	private String oppositeBoxShootOut;

	/** ゴールポスト */
	private String goalPost;

	/** 対戦相手ゴールポスト */
	private String oppositeGoalPost;

	/** ヘディングゴール */
	private String goalHead;

	/** 対戦相手ヘディングゴール */
	private String oppositeGoalHead;

	/** キーパーセーブ */
	private String keeperSave;

	/** 対戦相手キーパーセーブ */
	private String oppositeKeeperSave;

	/** フリーキック */
	private String freeKick;

	/** 対戦相手フリーキック */
	private String oppositeFreeKick;

	/** オフサイド */
	private String offside;

	/** 対戦相手オフサイド */
	private String oppositeOffside;

	/** ファウル */
	private String foul;

	/** 対戦相手ファウル */
	private String oppositeFoul;

	/** イエローカード */
	private String yellowCard;

	/** 対戦相手イエローカード */
	private String oppositeYellowCard;

	/** レッドカード */
	private String redCard;

	/** 対戦相手レッドカード */
	private String oppositeRedCard;

	/** スローイン */
	private String slowIn;

	/** 対戦相手スローイン */
	private String oppositeSlowIn;

	/** ボックスタッチ */
	private String boxTouch;

	/** 対戦相手ボックスタッチ */
	private String oppositeBoxTouch;

	/** パス数_成功率 */
	private String passCountSuccessRatio;

	/** パス数_成功数 */
	private String passCountSuccessCount;

	/** パス数_試行数 */
	private String passCountTryCount;

	/** 対戦相手パス数_成功率 */
	private String oppositePassCountSuccessRatio;

	/** 対戦相手パス数_成功数 */
	private String oppositePassCountSuccessCount;

	/** 対戦相手パス数_試行数 */
	private String oppositePassCountTryCount;

	/** ロングパス数_成功率 */
	private String longPassCountSuccessRatio;

	/** ロングパス数_成功数 */
	private String longPassCountSuccessCount;

	/** ロングパス数_試行数 */
	private String longPassCountTryCount;

	/** 対戦相手ロングパス数_成功率 */
	private String oppositeLongPassCountSuccessRatio;

	/** 対戦相手ロングパス数_成功数 */
	private String oppositeLongPassCountSuccessCount;

	/** 対戦相手ロングパス数_試行数 */
	private String oppositeLongPassCountTryCount;

	/** ファイナルサードパス数_成功率 */
	private String finalThirdPassCountSuccessRatio;

	/** ファイナルサードパス数_成功数 */
	private String finalThirdPassCountSuccessCount;

	/** ファイナルサードパス数_試行数 */
	private String finalThirdPassCountTryCount;

	/** 対戦相手ファイナルサードパス数_成功率 */
	private String oppositeFinalThirdPassCountSuccessRatio;

	/** 対戦相手ファイナルサードパス数_成功数 */
	private String oppositeFinalThirdPassCountSuccessCount;

	/** 対戦相手ファイナルサードパス数_試行数 */
	private String oppositeFinalThirdPassCountTryCount;

	/** クロス数_成功率 */
	private String crossCountSuccessRatio;

	/** クロス数_成功数 */
	private String crossCountSuccessCount;

	/** クロス数_試行数 */
	private String crossCountTryCount;

	/** 対戦相手クロス数_成功率 */
	private String oppositeCrossCountSuccessRatio;

	/** 対戦相手クロス数_成功数 */
	private String oppositeCrossCountSuccessCount;

	/** 対戦相手クロス数_試行数 */
	private String oppositeCrossCountTryCount;

	/** タックル数_成功率 */
	private String tackleCountSuccessRatio;

	/** タックル数_成功数 */
	private String tackleCountSuccessCount;

	/** タックル数_試行数 */
	private String tackleCountTryCount;

	/** 対戦相手タックル数_成功率 */
	private String oppositeTackleCountSuccessRatio;

	/** 対戦相手タックル数_成功数 */
	private String oppositeTackleCountSuccessCount;

	/** 対戦相手タックル数_試行数 */
	private String oppositeTackleCountTryCount;

	/** クリア数 */
	private String clearCount;

	/** 対戦相手クリア数 */
	private String oppositeClearCount;

	/** デュエル数 */
	private String duelCount;

	/** 対戦相手デュエル数 */
	private String oppositeDuelCount;

	/** インターセプト数 */
	private String interceptCount;

	/** 対戦相手インターセプト数 */
	private String oppositeInterceptCount;

	/** 天気 */
	private String weather;

	/** 気温 */
	private String temperature;

	/** 湿度 */
	private String humid;

}
