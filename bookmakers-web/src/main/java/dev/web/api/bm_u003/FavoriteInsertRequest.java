package dev.web.api.bm_u003;

import java.util.List;

import lombok.Data;

/**
 * FavoriteRequest APIリクエスト
 * userId / operatorId は後方互換のため残すが、
 * backend では Authorization ヘッダーの JWT から解決する。
 *
 * @author shiraishitoshio
 */
@Data
public class FavoriteInsertRequest {

    /** 後方互換用（backendでは未使用） */
    private Long userId;

    /** 後方互換用（backendでは未使用） */
    private String operatorId;

    /** まとめて登録する項目 */
    private List<FavoriteItem> items;

}
