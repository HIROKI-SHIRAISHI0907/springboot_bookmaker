package dev.web.api.bm_w011;

import lombok.Data;

@Data
public class SubLeagueInfoDTO {
    // DB上の元の値
    private String rawName;

    // 表示用: "▶︎EAST" など
    private String name;

    // subLeague選択時の遷移先
    private String routingPath;

    // チーム件数
    private Integer teamCount;

    /** シーズン終了済みか */
    private boolean seasonEnded;

    /** リンク活性可否 */
    private boolean linkEnabled;

    /** 画面表示用ラベル */
    private String seasonEndedLabel;

}
