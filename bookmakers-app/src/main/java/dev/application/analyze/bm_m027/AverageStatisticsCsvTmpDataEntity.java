package dev.application.analyze.bm_m027;

import lombok.Data;

/**
 * 各スコア状況における平均必要特徴量(標準偏差含む)を導出するEntity
 * @author shiraishitoshio
 *
 */
@Data
public class AverageStatisticsCsvTmpDataEntity {

	/** ID */
	private String id;

	/** スコア(X-Xの方式) */
	private String score;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** チーム */
	private String team;

	/** 件数 */
	private String gameCount;

}
