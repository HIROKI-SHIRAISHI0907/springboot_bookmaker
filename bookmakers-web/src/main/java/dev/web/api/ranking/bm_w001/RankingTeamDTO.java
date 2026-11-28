package dev.web.api.ranking.bm_w001;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** ランキングの 1 チーム（2026-11-28 追加） */
@Data
public class RankingTeamDTO {
	/** 現在の順位（値が無ければ null） */
	private Integer rank;
	private String team;
	/** 現在の値 */
	private Double value;
	/** 集計した試合数 */
	private int games;
	/** 最後に集計したラウンド */
	private Integer lastRound;
	/** チームカラー（team_color_master。無ければ null） */
	private String colorMain;
	private String colorSub;
	/** ラウンドごとの順位・値（rounds と同じ並び） */
	private List<RankingPointDTO> series = new ArrayList<>();
}
