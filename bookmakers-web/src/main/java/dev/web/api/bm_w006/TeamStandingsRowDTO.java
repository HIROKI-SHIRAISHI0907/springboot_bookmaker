package dev.web.api.bm_w006;

import lombok.Data;

@Data
public class TeamStandingsRowDTO {
	private String country;
	private String league;
	private String seasonYear;
	private Integer match;          // 節（グラフにも使う）
	private Integer rank;           // 順位（SQLで算出）
	private String team;
	private Integer win;
	private Integer lose;
	private Integer draw;
	private Integer winningPoints;
	private boolean currentTeam;      // 太字用
}
