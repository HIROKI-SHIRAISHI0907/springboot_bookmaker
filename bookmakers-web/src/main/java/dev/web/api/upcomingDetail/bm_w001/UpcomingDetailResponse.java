package dev.web.api.upcomingDetail.bm_w001;

import java.util.ArrayList;
import java.util.List;

import dev.web.api.headToHead.bm_w001.HeadToHeadResponse;
import dev.web.api.matchDetail.matchVenueDTO.MatchVenueDTO;
import dev.web.api.upcomingDetail.upcomingDetailDTO.StandingRowDTO;
import dev.web.api.upcomingDetail.upcomingDetailDTO.TeamGoalStatsDTO;
import lombok.Data;

/**
 * これからの試合の詳細（GET /v1/api/upcoming-detail）
 * @author shiraishitoshio
 */
@Data
public class UpcomingDetailResponse {

	private String country;

	private String league;

	/** 「国 / リーグ」 */
	private String leagueLabel;

	private String home;

	private String away;

	/** 試合会場（ホームチームのホームスタジアム。team_location_master に無ければ null） */
	private MatchVenueDTO venue;

	/** 過去の対戦成績（ホームチーム視点。全シーズン・全大会） */
	private HeadToHeadResponse headToHead;

	/** 順位表のシーズン（リーグのデータが無ければ null） */
	private String standingSeason;

	/** 順位表（順位順。リーグのデータが無ければ空） */
	private List<StandingRowDTO> standings = new ArrayList<>();

	/** ゴール数などの統計 */
	private TeamGoalStatsDTO homeStats;

	private TeamGoalStatsDTO awayStats;
}
