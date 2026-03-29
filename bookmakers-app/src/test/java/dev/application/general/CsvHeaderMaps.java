package dev.application.general;

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
            Map.entry("データ取得時間", "latestInfoDate")
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

    /**
     * public.data CSV -> BookDataEntity 用
     * 一致しない列名だけ定義
     */
    public static final Map<String, String> BOOK_DATA = Map.ofEntries(
            Map.entry("data_category", "gameTeamCategory"),
            Map.entry("times", "time"),

            Map.entry("home_donation", "homeBallPossesion"),
            Map.entry("away_donation", "awayBallPossesion"),

            Map.entry("home_block_shoot", "homeShootBlocked"),
            Map.entry("away_block_shoot", "awayShootBlocked"),

            Map.entry("home_corner", "homeCornerKick"),
            Map.entry("away_corner", "awayCornerKick"),

            Map.entry("home_offside", "homeOffSide"),
            Map.entry("away_offside", "awayOffSide"),

            Map.entry("temparature", "temperature"),

            Map.entry("game_link", "gameLink"),
            Map.entry("location", "location"),

            Map.entry("game_id", "gameId"),
            Map.entry("match_id", "matchId"),
            Map.entry("time_sort_seconds", "timeSortSeconds"),

            Map.entry("add_manual_flg", "addManualFlg"),
            Map.entry("logic_flg", "logicFlg"),

            Map.entry("register_id", "registerId"),
            Map.entry("register_time", "registerTime"),
            Map.entry("update_id", "updateId"),
            Map.entry("update_time", "updateTime")
    );
}
