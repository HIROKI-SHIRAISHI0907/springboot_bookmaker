package dev.batch.bm_b097;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TeamPairWithDataCategory {
	private String dataCategory;
	private String homeTeamName;
	private String awayTeamName;
}
