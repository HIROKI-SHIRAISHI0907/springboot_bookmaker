package dev.application.analyze.bm_m021;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import dev.common.entity.BookDataEntity;

@Mapper(componentModel = "spring")
public interface BookDataToTeamMatchFinalMapper {

	@Mappings({
			// 基本情報
			@Mapping(source = "book.homeTeamName", target = "teamName"),
			@Mapping(source = "book.awayTeamName", target = "versusTeamName"),
			@Mapping(constant = "ha", target = "ha"),
			@Mapping(constant = "score", target = "score"),
			@Mapping(constant = "result", target = "result"),
			@Mapping(source = "book.homeRank", target = "gameFinRank"),
			@Mapping(source = "book.awayRank", target = "oppositeGameFinRank"),
			// 期待値
			@Mapping(source = "book.homeExp", target = "exp"),
			@Mapping(source = "book.awayExp", target = "oppositeExp"),
			// ポゼッション（保持率）
			@Mapping(source = "finalData.possession", target = "donation"),
			@Mapping(source = "finalOppoData.possession", target = "oppositeDonation"),
			// シュート系（例として一部のみ）
			@Mapping(source = "book.homeShootAll", target = "shootAll"),
			@Mapping(source = "book.awayShootAll", target = "oppositeShootAll"),
			@Mapping(source = "book.homeShootIn", target = "shootIn"),
			@Mapping(source = "book.awayShootIn", target = "oppositeShootIn"),
			@Mapping(source = "book.homeShootOut", target = "shootOut"),
			@Mapping(source = "book.awayShootOut", target = "oppositeShootOut"),
			// ブロックシュート
			@Mapping(source = "book.homeShootBlocked", target = "blockShoot"),
			@Mapping(source = "book.awayShootBlocked", target = "oppositeBlockShoot"),
			// ビッグチャンス
			@Mapping(source = "book.homeBigChance", target = "bigChance"),
			@Mapping(source = "book.awayBigChance", target = "oppositeBigChance"),
			// コーナーキック
			@Mapping(source = "book.homeCornerKick", target = "corner"),
			@Mapping(source = "book.awayCornerKick", target = "oppositeCorner"),
			// ボックス内シュート
			@Mapping(source = "book.homeBoxShootIn", target = "boxShootIn"),
			@Mapping(source = "book.awayBoxShootIn", target = "oppositeBoxShootIn"),
			// ボックス外シュート
			@Mapping(source = "book.homeBoxShootOut", target = "boxShootOut"),
			@Mapping(source = "book.awayBoxShootOut", target = "oppositeBoxShootOut"),
			// ゴールポスト
			@Mapping(source = "book.homeGoalPost", target = "goalPost"),
			@Mapping(source = "book.awayGoalPost", target = "oppositeGoalPost"),
			// ヘディングゴール
			@Mapping(source = "book.homeGoalHead", target = "goalHead"),
			@Mapping(source = "book.awayGoalHead", target = "oppositeGoalHead"),
			// キーパーセーブ
			@Mapping(source = "book.homeKeeperSave", target = "keeperSave"),
			@Mapping(source = "book.awayKeeperSave", target = "oppositeKeeperSave"),
			// フリーキック
			@Mapping(source = "book.homeFreeKick", target = "freeKick"),
			@Mapping(source = "book.awayFreeKick", target = "oppositeFreeKick"),
			// オフサイド
			@Mapping(source = "book.homeOffSide", target = "offside"),
			@Mapping(source = "book.awayOffSide", target = "oppositeOffside"),
			// ファウル
			@Mapping(source = "book.homeFoul", target = "foul"),
			@Mapping(source = "book.awayFoul", target = "oppositeFoul"),
			// イエローカード
			@Mapping(source = "book.homeYellowCard", target = "yellowCard"),
			@Mapping(source = "book.awayYellowCard", target = "oppositeYellowCard"),
			// レッドカード
			@Mapping(source = "book.homeRedCard", target = "redCard"),
			@Mapping(source = "book.awayRedCard", target = "oppositeRedCard"),
			// スローイン
			@Mapping(source = "book.homeSlowIn", target = "slowIn"),
			@Mapping(source = "book.awaySlowIn", target = "oppositeSlowIn"),
			// ボックスタッチ
			@Mapping(source = "book.homeBoxTouch", target = "boxTouch"),
			@Mapping(source = "book.awayBoxTouch", target = "oppositeBoxTouch"),
			// パス分割データ（例: 成功率、成功数、試行数）
			@Mapping(source = "finalData.pass.ratio", target = "passCountSuccessRatio"),
			@Mapping(source = "finalData.pass.success", target = "passCountSuccessCount"),
			@Mapping(source = "finalData.pass.trys", target = "passCountTryCount"),
			@Mapping(source = "finalOppoData.pass.ratio", target = "oppositePassCountSuccessRatio"),
			@Mapping(source = "finalOppoData.pass.success", target = "oppositePassCountSuccessCount"),
			@Mapping(source = "finalOppoData.pass.trys", target = "oppositePassCountTryCount"),
			@Mapping(source = "finalData.finalThirdPass.ratio", target = "finalThirdPassCountSuccessRatio"),
			@Mapping(source = "finalData.finalThirdPass.success", target = "finalThirdPassCountSuccessCount"),
			@Mapping(source = "finalData.finalThirdPass.trys", target = "finalThirdPassCountTryCount"),
			@Mapping(source = "finalOppoData.finalThirdPass.ratio", target = "oppositeFinalThirdPassCountSuccessRatio"),
			@Mapping(source = "finalOppoData.finalThirdPass.success", target = "oppositeFinalThirdPassCountSuccessCount"),
			@Mapping(source = "finalOppoData.finalThirdPass.trys", target = "oppositeFinalThirdPassCountTryCount"),
			@Mapping(source = "finalData.cross.ratio", target = "crossCountSuccessRatio"),
			@Mapping(source = "finalData.cross.success", target = "crossCountSuccessCount"),
			@Mapping(source = "finalData.cross.trys", target = "crossCountTryCount"),
			@Mapping(source = "finalOppoData.cross.ratio", target = "oppositeCrossCountSuccessRatio"),
			@Mapping(source = "finalOppoData.cross.success", target = "oppositeCrossCountSuccessCount"),
			@Mapping(source = "finalOppoData.cross.trys", target = "oppositeCrossCountTryCount"),
			@Mapping(source = "finalData.tackle.ratio", target = "tackleCountSuccessRatio"),
			@Mapping(source = "finalData.tackle.success", target = "tackleCountSuccessCount"),
			@Mapping(source = "finalData.tackle.trys", target = "tackleCountTryCount"),
			@Mapping(source = "finalOppoData.tackle.ratio", target = "oppositeTackleCountSuccessRatio"),
			@Mapping(source = "finalOppoData.tackle.success", target = "oppositeTackleCountSuccessCount"),
			@Mapping(source = "finalOppoData.tackle.trys", target = "oppositeTackleCountTryCount")
	})
	TeamMatchFinalStatsEntity mapHomeStruct(BookDataEntity book, FinalData finalData, FinalData finalOppoData,
			String ha, String score, String result);

	@Mappings({
			// 基本情報
			@Mapping(source = "book.awayTeamName", target = "teamName"),
			@Mapping(source = "book.homeTeamName", target = "versusTeamName"),
			@Mapping(constant = "ha", target = "ha"),
			@Mapping(constant = "score", target = "score"),
			@Mapping(constant = "result", target = "result"),
			@Mapping(source = "book.awayRank", target = "gameFinRank"),
			@Mapping(source = "book.homeRank", target = "oppositeGameFinRank"),
			// 期待値
			@Mapping(source = "book.awayExp", target = "exp"),
			@Mapping(source = "book.homeExp", target = "oppositeExp"),
			// ポゼッション（保持率）
			@Mapping(source = "finalData.possession", target = "donation"),
			@Mapping(source = "finalOppoData.possession", target = "oppositeDonation"),
			// シュート系
			@Mapping(source = "book.awayShootAll", target = "shootAll"),
			@Mapping(source = "book.homeShootAll", target = "oppositeShootAll"),
			@Mapping(source = "book.awayShootIn", target = "shootIn"),
			@Mapping(source = "book.homeShootIn", target = "oppositeShootIn"),
			@Mapping(source = "book.awayShootOut", target = "shootOut"),
			@Mapping(source = "book.homeShootOut", target = "oppositeShootOut"),
			// ブロックシュート
			@Mapping(source = "book.awayShootBlocked", target = "blockShoot"),
			@Mapping(source = "book.homeShootBlocked", target = "oppositeBlockShoot"),
			// ビッグチャンス
			@Mapping(source = "book.awayBigChance", target = "bigChance"),
			@Mapping(source = "book.homeBigChance", target = "oppositeBigChance"),
			// コーナーキック
			@Mapping(source = "book.awayCornerKick", target = "corner"),
			@Mapping(source = "book.homeCornerKick", target = "oppositeCorner"),
			// ボックス内シュート
			@Mapping(source = "book.awayBoxShootIn", target = "boxShootIn"),
			@Mapping(source = "book.homeBoxShootIn", target = "oppositeBoxShootIn"),
			// ボックス外シュート
			@Mapping(source = "book.awayBoxShootOut", target = "boxShootOut"),
			@Mapping(source = "book.homeBoxShootOut", target = "oppositeBoxShootOut"),
			// ゴールポスト
			@Mapping(source = "book.awayGoalPost", target = "goalPost"),
			@Mapping(source = "book.homeGoalPost", target = "oppositeGoalPost"),
			// ヘディングゴール
			@Mapping(source = "book.awayGoalHead", target = "goalHead"),
			@Mapping(source = "book.homeGoalHead", target = "oppositeGoalHead"),
			// キーパーセーブ
			@Mapping(source = "book.awayKeeperSave", target = "keeperSave"),
			@Mapping(source = "book.homeKeeperSave", target = "oppositeKeeperSave"),
			// フリーキック
			@Mapping(source = "book.awayFreeKick", target = "freeKick"),
			@Mapping(source = "book.homeFreeKick", target = "oppositeFreeKick"),
			// オフサイド
			@Mapping(source = "book.awayOffSide", target = "offside"),
			@Mapping(source = "book.homeOffSide", target = "oppositeOffside"),
			// ファウル
			@Mapping(source = "book.awayFoul", target = "foul"),
			@Mapping(source = "book.homeFoul", target = "oppositeFoul"),
			// イエローカード
			@Mapping(source = "book.awayYellowCard", target = "yellowCard"),
			@Mapping(source = "book.homeYellowCard", target = "oppositeYellowCard"),
			// レッドカード
			@Mapping(source = "book.awayRedCard", target = "redCard"),
			@Mapping(source = "book.homeRedCard", target = "oppositeRedCard"),
			// スローイン
			@Mapping(source = "book.awaySlowIn", target = "slowIn"),
			@Mapping(source = "book.homeSlowIn", target = "oppositeSlowIn"),
			// ボックスタッチ
			@Mapping(source = "book.awayBoxTouch", target = "boxTouch"),
			@Mapping(source = "book.homeBoxTouch", target = "oppositeBoxTouch"),
			// パス分割データ（例: 成功率、成功数、試行数）
			@Mapping(source = "finalData.pass.ratio", target = "passCountSuccessRatio"),
			@Mapping(source = "finalData.pass.success", target = "passCountSuccessCount"),
			@Mapping(source = "finalData.pass.trys", target = "passCountTryCount"),
			@Mapping(source = "finalOppoData.pass.ratio", target = "oppositePassCountSuccessRatio"),
			@Mapping(source = "finalOppoData.pass.success", target = "oppositePassCountSuccessCount"),
			@Mapping(source = "finalOppoData.pass.trys", target = "oppositePassCountTryCount"),
			@Mapping(source = "finalData.finalThirdPass.ratio", target = "finalThirdPassCountSuccessRatio"),
			@Mapping(source = "finalData.finalThirdPass.success", target = "finalThirdPassCountSuccessCount"),
			@Mapping(source = "finalData.finalThirdPass.trys", target = "finalThirdPassCountTryCount"),
			@Mapping(source = "finalOppoData.finalThirdPass.ratio", target = "oppositeFinalThirdPassCountSuccessRatio"),
			@Mapping(source = "finalOppoData.finalThirdPass.success", target = "oppositeFinalThirdPassCountSuccessCount"),
			@Mapping(source = "finalOppoData.finalThirdPass.trys", target = "oppositeFinalThirdPassCountTryCount"),
			@Mapping(source = "finalData.cross.ratio", target = "crossCountSuccessRatio"),
			@Mapping(source = "finalData.cross.success", target = "crossCountSuccessCount"),
			@Mapping(source = "finalData.cross.trys", target = "crossCountTryCount"),
			@Mapping(source = "finalOppoData.cross.ratio", target = "oppositeCrossCountSuccessRatio"),
			@Mapping(source = "finalOppoData.cross.success", target = "oppositeCrossCountSuccessCount"),
			@Mapping(source = "finalOppoData.cross.trys", target = "oppositeCrossCountTryCount"),
			@Mapping(source = "finalData.tackle.ratio", target = "tackleCountSuccessRatio"),
			@Mapping(source = "finalData.tackle.success", target = "tackleCountSuccessCount"),
			@Mapping(source = "finalData.tackle.trys", target = "tackleCountTryCount"),
			@Mapping(source = "finalOppoData.tackle.ratio", target = "oppositeTackleCountSuccessRatio"),
			@Mapping(source = "finalOppoData.tackle.success", target = "oppositeTackleCountSuccessCount"),
			@Mapping(source = "finalOppoData.tackle.trys", target = "oppositeTackleCountTryCount")
	})
	TeamMatchFinalStatsEntity mapAwayStruct(BookDataEntity book, FinalData finalData, FinalData finalOppoData,
			String ha, String score, String result);
}
