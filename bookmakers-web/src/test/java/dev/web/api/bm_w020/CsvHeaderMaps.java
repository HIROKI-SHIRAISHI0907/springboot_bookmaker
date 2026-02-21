package dev.web.api.bm_w020;

import java.util.Map;

public class CsvHeaderMaps {

    // 既存: TEAM_MEMBER / TEAM / SEASON ... はそのまま

    /** 起源データ（日本語ヘッダ）→ DataEntity */
    public static final Map<String, String> DATA_ORIGIN = Map.ofEntries(
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

        // CSVヘッダが「相手ボックスタッチ」なので、DataEntityのBoxTouchに寄せる
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

        // CSVが「勝利数」表記だが、Entity側はDuelCountに入れる
        Map.entry("ホームデュエル勝利数", "homeDuelCount"),
        Map.entry("アウェーデュエル勝利数", "awayDuelCount"),

        Map.entry("ホームインターセプト", "homeInterceptCount"),
        Map.entry("アウェーインターセプト", "awayInterceptCount"),

        // ここはCSVの実値が "2026-02-16 19:31:43" のような日時なので recordTime へ
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
        Map.entry("アウェイチームスタイル", "awayTeamStyle"), // ヘッダは「アウェイ」
        Map.entry("ゴール確率", "probablity"),
        Map.entry("得点予想時間", "predictionScoreTime"),

        // 末尾3列（あなたの例だと「試合ID=2oMClXVN, 通番=1, ソート用秒=0」）
        Map.entry("試合ID", "matchId"),
        Map.entry("通番", "seq"),
        Map.entry("ソート用秒", "timeSortSeconds")
    );
}
