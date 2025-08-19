package dev.application.analyze.bm_m027;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * キーランキングデータ
 * @author shiraishitoshio
 *
 */
@Data
@AllArgsConstructor
public class KeyRanking {

	/** ID */
	private String id;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** チーム */
	private String team;

	/** スコア */
	private String score;

	/** フィールド名 */
	private String field;

	/** 最小値 */
	private String min;

	/** 平均値 */
	private String avg;

	/** 最大値 */
	private String max;

	/** 生データ */
	private String value;

	/** 順位 */
	private String rank;

}
