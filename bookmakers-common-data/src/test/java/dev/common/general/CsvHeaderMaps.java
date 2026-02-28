package dev.common.general;

import java.util.Map;

public class CsvHeaderMaps {

    public static final Map<String, String> TEAM_MEMBER = Map.ofEntries(
            Map.entry("国", "country"),
            Map.entry("リーグ", "league"),
            Map.entry("所属チーム", "team"),
            Map.entry("選手名", "member"),
            Map.entry("ポジション", "position"),
            Map.entry("背番号", "jersey"),
            Map.entry("得点数", "score"),
            Map.entry("年齢", "age"),
            Map.entry("誕生日", "birth"),
            Map.entry("市場価値", "marketValue"),
            Map.entry("ローン保有元", "loanBelong"),
            Map.entry("契約期限", "deadlineContractDate"),
            Map.entry("顔写真", "facePicPath"),
            Map.entry("故障情報", "injury"),
            // 「データ取得時間」をどこに入れるかはMetaEntity次第なので、
            // たとえば MetaEntity に registerTime があるならここを有効化:
            // Map.entry("データ取得時間", "registerTime")
            Map.entry("データ取得時間", "latestInfoDate") // 仮置き（必要なら変更）
    );

    public static final Map<String, String> TEAM = Map.ofEntries(
            Map.entry("国", "country"),
            Map.entry("リーグ", "league"),
            Map.entry("チーム", "team"),
            Map.entry("チームリンク", "link")
    );

    public static final Map<String, String> SEASON = Map.ofEntries(
            Map.entry("国", "country"),
            Map.entry("リーグ", "league"),
            Map.entry("シーズン年", "seasonYear"),
            Map.entry("シーズン開始", "startSeasonDate"),
            Map.entry("シーズン終了", "endSeasonDate"),
            Map.entry("ラウンド数", "round"),
            Map.entry("パス", "path"),
            Map.entry("リーグアイコン", "icon")
    );

    public static final Map<String, String> DATA = Map.ofEntries(
            Map.entry("ホーム順位", "homeRank"),
            Map.entry("試合国及びカテゴリ", "dataCategory"),
            Map.entry("試合時間", "times"),
            Map.entry("ホームチーム", "homeTeamName"),
            Map.entry("ホームスコア", "homeScore"),
            Map.entry("アウェー順位", "awayRank"),
            Map.entry("アウェーチーム", "awayTeamName"),
            Map.entry("アウェースコア", "awayScore"),

            Map.entry("ホーム期待値", "homeExp"),
            Map.entry("アウェー期待値", "awayExp"),
            Map.entry("ホーム枠内ゴール期待値", "homeInGoalExp"),
            Map.entry("アウェー枠内ゴール期待値", "awayInGoalExp"),

            // 表記は「ボール支配率」だが、Entityは Donation（possession）扱い
            Map.entry("ホームボール支配率", "homeDonation"),
            Map.entry("アウェーボール支配率", "awayDonation"),

            Map.entry("ホームシュート数", "homeShootAll"),
            Map.entry("アウェーシュート数", "awayShootAll"),
            Map.entry("ホーム枠内シュート数", "homeShootIn"),
            Map.entry("アウェー枠内シュート数", "awayShootIn"),
            Map.entry("ホーム枠外シュート数", "homeShootOut"),
            Map.entry("アウェー枠外シュート数", "awayShootOut"),
            Map.entry("ホームブロックシュート", "homeBlockShoot"),
            Map.entry("アウェーブロックシュート", "awayBlockShoot"),

            Map.entry("ホームビッグチャンス", "homeBigChance"),
            Map.entry("アウェービッグチャンス", "awayBigChance"),
            Map.entry("ホームコーナーキック", "homeCorner"),
            Map.entry("アウェーコーナーキック", "awayCorner"),

            // “ボックス内/外シュート” は Entity 側の命名に合わせる
            Map.entry("ホームボックス内シュート", "homeBoxShootIn"),
            Map.entry("アウェーボックス内シュート", "awayBoxShootIn"),
            Map.entry("ホームボックス外シュート", "homeBoxShootOut"),
            Map.entry("アウェーボックス外シュート", "awayBoxShootOut"),

            Map.entry("ホームゴールポスト", "homeGoalPost"),
            Map.entry("アウェーゴールポスト", "awayGoalPost"),
            Map.entry("ホームヘディングゴール", "homeGoalHead"),
            Map.entry("アウェーヘディングゴール", "awayGoalHead"),
            Map.entry("ホームキーパーセーブ", "homeKeeperSave"),
            Map.entry("アウェーキーパーセーブ", "awayKeeperSave"),

            Map.entry("ホームフリーキック", "homeFreeKick"),
            Map.entry("アウェーフリーキック", "awayFreeKick"),
            Map.entry("ホームオフサイド", "homeOffside"),
            Map.entry("アウェーオフサイド", "awayOffside"),
            Map.entry("ホームファウル", "homeFoul"),
            Map.entry("アウェーファウル", "awayFoul"),
            Map.entry("ホームイエローカード", "homeYellowCard"),
            Map.entry("アウェーイエローカード", "awayYellowCard"),
            Map.entry("ホームレッドカード", "homeRedCard"),
            Map.entry("アウェーレッドカード", "awayRedCard"),

            Map.entry("ホームスローイン", "homeSlowIn"),
            Map.entry("アウェースローイン", "awaySlowIn"),

            // ヘッダーは「相手ボックスタッチ」だが Entity は BoxTouch
            Map.entry("ホーム相手ボックスタッチ", "homeBoxTouch"),
            Map.entry("アウェー相手ボックスタッチ", "awayBoxTouch"),

            Map.entry("ホームパス", "homePassCount"),
            Map.entry("アウェーパス", "awayPassCount"),
            Map.entry("ホームロングパス", "homeLongPassCount"),
            Map.entry("アウェーロングパス", "awayLongPassCount"),
            Map.entry("ホームファイナルサードパス", "homeFinalThirdPassCount"),
            Map.entry("アウェーファイナルサードパス", "awayFinalThirdPassCount"),
            Map.entry("ホームクロス", "homeCrossCount"),
            Map.entry("アウェークロス", "awayCrossCount"),
            Map.entry("ホームタックル", "homeTackleCount"),
            Map.entry("アウェータックル", "awayTackleCount"),
            Map.entry("ホームクリア", "homeClearCount"),
            Map.entry("アウェークリア", "awayClearCount"),
            Map.entry("ホームデュエル勝利数", "homeDuelCount"),
            Map.entry("アウェーデュエル勝利数", "awayDuelCount"),
            Map.entry("ホームインターセプト", "homeInterceptCount"),
            Map.entry("アウェーインターセプト", "awayInterceptCount"),

            // サンプル行の日時が入っている列なので recordTime に対応させる
            Map.entry("スコア時間", "recordTime"),

            Map.entry("天気", "weather"),
            Map.entry("気温", "temparature"),
            Map.entry("湿度", "humid"),
            Map.entry("審判名", "judgeMember"),
            Map.entry("ホーム監督名", "homeManager"),
            Map.entry("アウェー監督名", "awayManager"),
            Map.entry("ホームフォーメーション", "homeFormation"),
            Map.entry("アウェーフォーメーション", "awayFormation"),
            Map.entry("スタジアム", "studium"),
            Map.entry("収容人数", "capacity"),
            Map.entry("観客数", "audience"),
            Map.entry("場所", "location"),

            Map.entry("ホームチーム最大得点取得者", "homeMaxGettingScorer"),
            Map.entry("アウェーチーム最大得点取得者", "awayMaxGettingScorer"),
            Map.entry("ホームチーム最大得点取得者出場状況", "homeMaxGettingScorerGameSituation"),
            Map.entry("アウェーチーム最大得点取得者出場状況", "awayMaxGettingScorerGameSituation"),

            Map.entry("ホームチームホーム得点", "homeTeamHomeScore"),
            Map.entry("ホームチームホーム失点", "homeTeamHomeLost"),
            Map.entry("アウェーチームホーム得点", "awayTeamHomeScore"),
            Map.entry("アウェーチームホーム失点", "awayTeamHomeLost"),
            Map.entry("ホームチームアウェー得点", "homeTeamAwayScore"),
            Map.entry("ホームチームアウェー失点", "homeTeamAwayLost"),
            Map.entry("アウェーチームアウェー得点", "awayTeamAwayScore"),
            Map.entry("アウェーチームアウェー失点", "awayTeamAwayLost"),

            Map.entry("通知フラグ", "noticeFlg"),
            Map.entry("試合リンク文字列", "gameLink"),

            Map.entry("ゴール時間", "goalTime"),
            Map.entry("選手名", "goalTeamMember"),
            Map.entry("判定結果", "judge"),
            Map.entry("ホームチームスタイル", "homeTeamStyle"),
            Map.entry("アウェイチームスタイル", "awayTeamStyle"),
            Map.entry("ゴール確率", "probablity"),
            Map.entry("得点予想時間", "predictionScoreTime"),

            // 末尾カラム
            Map.entry("試合ID", "matchId"),
            Map.entry("通番", "seq"),
            Map.entry("ソート用秒", "timeSortSeconds"),

            // ヘッダー側に将来追加されたときの保険
            Map.entry("マッチID", "matchId")
    );

}
