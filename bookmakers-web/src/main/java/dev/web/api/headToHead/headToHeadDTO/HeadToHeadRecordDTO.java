package dev.web.api.headToHead.headToHeadDTO;

import lombok.Data;

/**
 * 対戦成績の勝敗・得失点（通算 / ホーム時 / アウェー時 のどれか 1 つ）。team 視点
 * @author shiraishitoshio
 */
@Data
public class HeadToHeadRecordDTO {

	/** 試合数 */
	private int matches;

	/** 勝ち（PK 勝ちを含む） */
	private int wins;

	/** 引分 */
	private int draws;

	/** 負け（PK 負けを含む） */
	private int losses;

	/** 得点（PK 戦の得点は含めない） */
	private int goalsFor;

	/** 失点 */
	private int goalsAgainst;
}
