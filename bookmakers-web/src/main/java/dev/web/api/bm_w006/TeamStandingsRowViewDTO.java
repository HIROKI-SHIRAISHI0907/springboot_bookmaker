package dev.web.api.bm_w006;

import lombok.Data;

@Data
public class TeamStandingsRowViewDTO {
    private Integer rank;
    private Integer match; // このチームの最新節（=消化節）
    private String team;
    private Integer win;
    private Integer lose;
    private Integer draw;
    private Integer winningPoints;
    private boolean currentTeam;
}
