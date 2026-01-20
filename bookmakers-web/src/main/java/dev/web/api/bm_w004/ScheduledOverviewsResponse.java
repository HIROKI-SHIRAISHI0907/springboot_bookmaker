package dev.web.api.bm_w004;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * ScheduledOverviewsAPI レスポンス
 * /api/scheduled-overviews/{国}/{リーグ}/{seq}
 * で返却されるルートオブジェクト
 *
 *   {
 *     "match": { ... },
 *     "surfaces": [ ... ]
 *   }
 *
 * @author shiraishitoshio
 *
 */
@Data
public class ScheduledOverviewsResponse {

	/** 試合情報（ホーム/アウェー + 集計対象年月など） */
	@JsonProperty("match")
	private ScheduledOverviewsMatchDTO match;

	/** チーム側のスナップショット一覧（通常は [HOME, AWAY] の2件） */
	@JsonProperty("surfaces")
	private List<ScheduledSurfaceSnapshotDTO> surfaces;

	public ScheduledOverviewsResponse(
			ScheduledOverviewsMatchDTO match,
			List<ScheduledSurfaceSnapshotDTO> surfaces
	) {
		this.match = match;
		this.surfaces = surfaces;
	}
}
