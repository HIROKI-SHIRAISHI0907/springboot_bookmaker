package dev.web.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w001.FuturesAPIService;
import dev.web.api.bm_w001.FuturesResponseDTO;

@RestController
@RequestMapping("/api/future")
public class FuturesController {

	private final FuturesAPIService futuresAPIService;

    public FuturesController(FuturesAPIService futuresAPIService) {
        this.futuresAPIService = futuresAPIService;
    }

    /**
     * チーム指定
     * @param country
     * @param league
     * @param team
     * @return
     */
    @GetMapping("/{country}/{league}/{team}")
    public Map<String, Object> getFuture(
            @PathVariable String country,
            @PathVariable String league,
            @PathVariable String team
    ) {
        List<FuturesResponseDTO> matches = futuresAPIService.getFutureMatches(country, league, team);
        return Map.of("matches", matches);
    }

    /**
     * 管理画面用（今後の試合一覧）
     * - country/league/team を指定しなければ「直近の試合」を返す
     * - 指定すればフィルタして返す
     *
     * 例：
     * GET /api/future/admin/matches?country=jp&league=j1&team=鹿島
     * GET /api/future/admin/matches
     */
    @GetMapping("/admin/matches")
    public Map<String, Object> getFutureForAdmin(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String league,
            @RequestParam(defaultValue = "50") int limit
    ) {
        List<FuturesResponseDTO> matches = futuresAPIService.getFutureMatchesForAdmin(country, league, limit);
        return Map.of("matches", matches);
    }

}
