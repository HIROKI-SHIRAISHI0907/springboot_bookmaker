package dev.web.api.bm_w020;

import lombok.Data;

@Data
public class SeqWithKey {

	/** データカテゴリ */
	private String dataCategory;

	/** ホームチーム */
	private String homeTeamName;

	/** アウェーチーム */
	private String awayTeamName;

	/** 途中時間 */
	private String times;

	/** 通番 */
	private Integer seq;
}
