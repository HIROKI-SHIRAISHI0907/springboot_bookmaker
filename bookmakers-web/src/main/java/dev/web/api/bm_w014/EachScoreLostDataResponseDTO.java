package dev.web.api.bm_w014;

import lombok.Data;

@Data
public class EachScoreLostDataResponseDTO {

	private Long seq;

	private String dataCategory;

	private String roundNo;

	private String recordTime;

	private String homeTeamName;

	private String awayTeamName;

	private Integer homeScore;

	private Integer awayScore;

	private String link;

	private String status;

}
