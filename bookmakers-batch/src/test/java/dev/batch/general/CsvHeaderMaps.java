package dev.batch.general;

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
}
