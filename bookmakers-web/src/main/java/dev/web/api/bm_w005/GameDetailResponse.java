package dev.web.api.bm_w005;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * GameDetailAPI レスポンス
 * /api/{country}/{league}/{team}/games/detail/{seq}
 *
 *   { "detail": { ... } }
 *
 * の形で返却するためのラッパー。
 *
 * @author shiraishitoshio
 */
@Data
public class GameDetailResponse {

    /** 試合詳細 */
    @JsonProperty("detail")
    private GameDetailDTO detail;

    public GameDetailResponse(GameDetailDTO detail) {
        this.detail = detail;
    }
}
