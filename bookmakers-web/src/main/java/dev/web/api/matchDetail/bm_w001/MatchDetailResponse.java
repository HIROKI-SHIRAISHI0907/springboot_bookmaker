package dev.web.api.matchDetail.bm_w001;

import java.util.ArrayList;
import java.util.List;

import dev.web.api.dashboard.bm_w002.DashboardForecastDTO;
import dev.web.api.matchDetail.matchDetailDTO.MatchGoalDTO;
import dev.web.api.matchDetail.matchDetailDTO.MatchInfoDTO;
import dev.web.api.matchDetail.matchDetailDTO.MatchRankChangeDTO;
import dev.web.api.matchDetail.matchDetailDTO.MatchStatDTO;
import dev.web.api.matchDetail.matchDetailDTO.MatchTimelinePointDTO;
import lombok.Data;

/**
 * GET /v1/api/match-detail?seqKey= のレスポンス
 * @author shiraishitoshio
 */
@Data
public class MatchDetailResponse {

	private boolean found;

	private boolean loggedIn;

	private String seqKey;

	private String matchId;

	private String country;

	private String league;

	private String leagueLabel;

	private String roundLabel;

	/** LIVE / HT / FINISHED / OTHER */
	private String status;

	/** 最新の試合時間（例: 93'、終了済） */
	private String minuteLabel;

	private String homeTeam;

	private String awayTeam;

	private Integer homeScore;

	private Integer awayScore;

	/** 最新の順位 */
	private Integer homeRank;

	private Integer awayRank;

	/** 最新の記録時刻 */
	private String recordTime;

	/** ライブ中だけ（このままなら）。それ以外は null */
	private DashboardForecastDTO forecast;

	private List<MatchStatDTO> stats = new ArrayList<>();

	private List<MatchTimelinePointDTO> timeline = new ArrayList<>();

	private List<MatchGoalDTO> goals = new ArrayList<>();

	private List<MatchRankChangeDTO> rankChanges = new ArrayList<>();

	private MatchInfoDTO info = new MatchInfoDTO();
}
