package dev.web.api.bm_u003;

import lombok.Data;

/**
 * FavoriteItem
 * @author shiraishitoshio
 *
 */
@Data
public class FavoriteItem {

    /** 国（必須） */
    private String country;

    /** リーグ（国リーグ or チーム登録時に必須） */
    private String league; // null OK

    /** チーム（チーム登録時に必須） */
    private String team;   // null OK

}
