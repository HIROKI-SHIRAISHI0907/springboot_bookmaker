package dev.web.api.upcomingDetail.bm_w001;

import java.util.ArrayList;
import java.util.List;

import dev.web.api.upcomingDetail.upcomingDetailDTO.TeamBadgeDTO;
import lombok.Data;

/**
 * GET /v1/api/upcoming-detail/badges の応答（2026-11-27 追加）
 * @author shiraishitoshio
 */
@Data
public class UpcomingBadgeResponse {

	/** 集計したシーズン（データが無ければ null） */
	private String season;

	/** 予定の試合のラウンド番号（分からなければ null） */
	private Integer round;

	private String home;

	private String away;

	private List<TeamBadgeDTO> homeBadges = new ArrayList<>();

	private List<TeamBadgeDTO> awayBadges = new ArrayList<>();

	/** 表示しなかった理由など（例: 前節が未反映のため連続記録を出していません） */
	private List<String> notes = new ArrayList<>();
}
