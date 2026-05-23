package dev.web.api.bm_a019;

import java.util.List;

import lombok.Data;

/**
 * DBコネクション監視一覧レスポンスDTO。
 *
 * <p>
 * 複数データソースの監視結果一覧を保持する。
 * </p>
 */
@Data
public class DbConnectionStatusListResponse {

    /**
     * 監視結果件数。
     */
    private int count;

    /**
     * 監視結果一覧。
     */
    private List<DbConnectionStatusResponse> items;
}
