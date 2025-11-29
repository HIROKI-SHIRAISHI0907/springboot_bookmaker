// src/main/java/dev/web/api/bm_w006/StandingRowDTO.java
package dev.web.api.bm_w006;

import lombok.Data;

/**
 * 順位表の1行分DTO
 * /api/{country}/{league}/standings
 * の rows 要素1件分に相当する。
 *
 * フロント側 StandingRow 型に対応:
 *  - position
 *  - teamName
 *  - teamEnglish
 *  - game
 *  - win
 *  - draw
 *  - lose
 *  - winningPoints
 *  - goalDiff
 *
 * @author shiraishitoshio
 */
@Data
public class StandingRowDTO {

    /** 順位（1位から） */
    private int position;

    /** チーム名（表示用、日本語名） */
    private String teamName;

    /** 英語スラッグ（ルーティング用。例: "tokyo-fc"） */
    private String teamEnglish;

    /** 試合数（win + draw + lose） */
    private int game;

    /** 勝ち数 */
    private int win;

    /** 引き分け数 */
    private int draw;

    /** 負け数 */
    private int lose;

    /** 勝ち点 */
    private int winningPoints;

    /** 得失点差（得点 - 失点。マイナス可） */
    private int goalDiff;
}
