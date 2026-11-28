package dev.web.api.ranking.bm_w001;

import lombok.Data;

/** 選べるデータ 1 件（2026-11-28 追加） */
@Data
public class RankingMetricDTO {
	/** キー（RankingMetric の名前） */
	private String key;
	private String label;
	/** RESULT（結果） / STAT（スタッツ） */
	private String type;
	private String unit;
	private boolean higherBetter;
	/** このリーグで出せるか（STAT は統計ができたリーグだけ） */
	private boolean available;
}
