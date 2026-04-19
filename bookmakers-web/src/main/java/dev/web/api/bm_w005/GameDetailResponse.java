package dev.web.api.bm_w005;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * GameDetailAPI レスポンス
 * /api/games/detail/{country}/{league}/{team}/{seq}
 *
 *   { "detail": { ... } }
 *
 * の形で返却するためのラッパー。
 *
 * @author shiraishitoshio
 */
@Data
@AllArgsConstructor
public class GameDetailResponse {

    /** 試合詳細 */
    private GameDetailDTO detail;

}
