// src/main/java/dev/web/api/bm_w005/GameMatchDTO.java
package dev.web.api.bm_w005;

import lombok.Data;

/**
 * GamesAPI（当日開催中/終了試合一覧）の 1 試合分
 * /api/{country}/{league}/{team}/games
 *
 * @author shiraishitoshio
 */
@Data
public class GameMatchDTO {

    /** future_master.seq （文字列→long に変換済み） */
    private long seq;

    /** 試合カテゴリ（例: "Japan: J1 League Round 12"） */
    private String gameTeamCategory;

    /** キックオフ予定日時（ISO文字列） */
    private String futureTime;

    /** ホームチーム名 */
    private String homeTeam;

    /** アウェーチーム名 */
    private String awayTeam;

    /** 外部サイトへのリンク（無ければ null） */
    private String link;

    /** ラウンド番号（取れなければ null） */
    private Integer roundNo;

    /** public.data での最新 times（例: "68:09" / "45+2'" / "終了" など） */
    private String latestTimes;

    /** public.data での最新 seq（詳細遷移用） */
    private Long latestSeq;

    /** ホームスコア（public.data.latest 行の値。無ければ null） */
    private Integer homeScore;

    /** アウェースコア（public.data.latest 行の値。無ければ null） */
    private Integer awayScore;

    /** 試合状態（LIVE or FINISHED） */
    private String status;
}
