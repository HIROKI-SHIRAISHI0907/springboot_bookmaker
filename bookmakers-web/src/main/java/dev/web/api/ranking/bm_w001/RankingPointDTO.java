package dev.web.api.ranking.bm_w001;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 変遷の 1 点（そのラウンドの試合が無い・取れていないときは rank・value とも null。グラフをつなげない） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RankingPointDTO {
	private int round;
	private Integer rank;
	private Double value;
}
