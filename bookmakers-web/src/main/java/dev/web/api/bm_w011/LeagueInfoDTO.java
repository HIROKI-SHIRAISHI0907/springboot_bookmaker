package dev.web.api.bm_w011;

import lombok.Data;

/**
 * リーグ情報データ
 */
@Data
public class LeagueInfoDTO {

    /** 表示名（親メニューなら leagueGroup、子一覧なら leagueFull） */
    private String name;

    /** 親リーグ名（例: "J2・J3リーグ"） */
    private String leagueGroup;

    /** フルリーグ名（例: "J2・J3リーグ - WEST A"） */
    private String leagueFull;

    /**
     * サブリーグ数（親集約行のみ意味がある）
     * - 親行: 2（WEST A/B）
     * - 子行: null か 1（どちらでもOK、運用で統一）
     */
    private Integer variantCount;

    /** シーズン年 */
    private String seasonYear;

    /** シーズン開始日 */
    private String startSeasonDate;

    /** シーズン終了日 */
    private String endSeasonDate;

    /** チーム件数 */
    private int teamCount;

    /** アプリ内パス（例: "/日本/J2・J3リーグ" や "/日本/J2・J3リーグ - WEST A"） */
    private String path;

    /** 外部ルーティングパス（Flashscoreのpath等。例: "/soccer/japan/j2-league/"） */
    private String routingPath;
}
