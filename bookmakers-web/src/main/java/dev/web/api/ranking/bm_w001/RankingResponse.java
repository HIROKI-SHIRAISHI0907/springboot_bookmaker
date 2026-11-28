package dev.web.api.ranking.bm_w001;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** GET /api/rankings（2026-11-28 追加） */
@Data
public class RankingResponse {
	private String country;
	private String league;
	private String season;
	/** STATS = 統計 / STATIC = 終了済の試合から */
	private String source;
	private String metric;
	private String label;
	private String unit;
	private boolean higherBetter;
	/** 小数の桁数 */
	private int scale;
	/** 横軸のラウンド（1 〜 最大） */
	private List<Integer> rounds = new ArrayList<>();
	/** 現在の順位順（値が無いチームは最後） */
	private List<RankingTeamDTO> teams = new ArrayList<>();
	/** 画面に出す注意書き */
	private List<String> notes = new ArrayList<>();
}
