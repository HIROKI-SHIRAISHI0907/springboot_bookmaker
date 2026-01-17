package dev.web.api.bm_u003;

import java.util.List;

import lombok.Data;

/**
 * FavoriteRequestAPIリクエスト
 * @author shiraishitoshio
 *
 */
@Data
public class FavoriteRequest {

	/** ユーザーID（共通） */
    private Long userId;

    /** 登録者ID（共通） */
    private String operatorId;

    /** まとめて登録する項目 */
    private List<FavoriteItem> items;

}
