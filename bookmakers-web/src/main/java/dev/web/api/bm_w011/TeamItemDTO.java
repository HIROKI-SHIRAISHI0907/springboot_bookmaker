// dev/web/api/bm_w011/TeamItemDTO.java
package dev.web.api.bm_w011;

import lombok.Data;

/**
 * TeamsInLeagueResponse 内の 1 チーム。（チーム一覧・チーム詳細）
 */
@Data
public class TeamItemDTO {

	/** 名前 */
    private String name;      // 表示名

    /** 英語名 */
    private String english;   // 英語スラッグ

    /** ハッシュ */
    private String hash;

    /** リンク */
    private String link;      // /team/<english>/<hash>

    /** パス */
    private String path;      // /<country>/<league>

    /** APIパス */
    private String apiPath;   // /api/leagues/<country>/<league>/<english>
}
