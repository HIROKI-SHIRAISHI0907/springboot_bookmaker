package dev.web.api.ranking.bm_w001;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** GET /api/rankings/metrics（2026-11-28 追加） */
@Data
public class RankingMetricsResponse {
	private String country;
	private String league;
	/** 統計のシーズン（統計が無ければ null） */
	private String season;
	/** STATS = 統計 / STATIC = 終了済の試合から（結果だけ） */
	private String source;
	private List<RankingMetricDTO> metrics = new ArrayList<>();
}
