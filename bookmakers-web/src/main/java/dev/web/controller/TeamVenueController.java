package dev.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.matchDetail.bm_w002.MatchVenueService;
import dev.web.api.matchDetail.matchVenueDTO.MatchVenueDTO;
import dev.web.api.team.bm_w003.TeamVenueResponse;
import lombok.RequiredArgsConstructor;

/**
 * チームページ（TeamResults.tsx）: ホームスタジアムの地理情報
 *
 * GET /v1/api/team-venue?country=&league=&team=
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TeamVenueController {

	private final MatchVenueService matchVenueService;

	@GetMapping("/team-venue")
	public TeamVenueResponse getTeamVenue(
			@RequestParam(name = "country", required = false) String country,
			@RequestParam(name = "league", required = false) String league,
			@RequestParam(name = "team") String team) {
		MatchVenueDTO venue = matchVenueService.findHomeVenue(team, country);
		TeamVenueResponse res = new TeamVenueResponse();
		res.setTeam(team);
		res.setFound(venue != null);
		res.setVenue(venue);
		return res;
	}
}
