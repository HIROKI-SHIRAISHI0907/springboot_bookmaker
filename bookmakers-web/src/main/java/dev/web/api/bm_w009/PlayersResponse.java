package dev.web.api.bm_w009;

import java.util.List;

import lombok.Data;

/**
 * 選手APIレスポンス
 * /api/{country}/{league}/{team}/players
 *
 * @author shiraishitoshio
 */
@Data
public class PlayersResponse {
    private List<PlayerDTO> players;
}
