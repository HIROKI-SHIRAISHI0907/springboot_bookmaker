package dev.web.api.bm_w005;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * GamesAPI レスポンスルート
 * /api/games/{country}/{league}/{team}
 *
 *   {
 *     "live": [...],
 *     "finished": [...]
 *   }
 *
 * フロント側:
 *   fetchTeamGames(...) => { live: GameMatch[], finished: GameMatch[] }
 *
 * に対応。
 *
 * @author shiraishitoshio
 */
@Data
public class GameMatchesResponse {

    /** 開催中試合一覧 */
    @JsonProperty("live")
    private List<GameMatchDTO> live;

    /** 試合終了試合一覧 */
    @JsonProperty("finished")
    private List<GameMatchDTO> finished;

    public GameMatchesResponse(List<GameMatchDTO> live, List<GameMatchDTO> finished) {
        this.live = live;
        this.finished = finished;
    }
}
