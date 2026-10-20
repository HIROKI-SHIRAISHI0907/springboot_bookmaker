package dev.web.api.headToHead.bm_w001;

import java.util.ArrayList;
import java.util.List;

import dev.web.api.headToHead.headToHeadDTO.HeadToHeadMatchDTO;
import dev.web.api.headToHead.headToHeadDTO.HeadToHeadRecordDTO;
import lombok.Data;

/**
 * 対戦成績（GET /v1/api/head-to-head）。team 視点
 * <p>ホーム / アウェーを問わず全シーズン・全大会（surface_overview_match に集計済みの試合）。</p>
 * @author shiraishitoshio
 */
@Data
public class HeadToHeadResponse {

	/** 指定したチーム */
	private String team;

	/** 対戦相手 */
	private String opponent;

	/** 通算 */
	private HeadToHeadRecordDTO total = new HeadToHeadRecordDTO();

	/** team がホームの試合 */
	private HeadToHeadRecordDTO home = new HeadToHeadRecordDTO();

	/** team がアウェーの試合 */
	private HeadToHeadRecordDTO away = new HeadToHeadRecordDTO();

	/** PK 勝ち・PK 負け（勝ち・負けの内数） */
	private int pkWins;

	private int pkLosses;

	/** 無失点・無得点・両チーム得点 */
	private int cleanSheets;

	private int failedToScore;

	private int bothScored;

	/** 先制した・先制された */
	private int scoredFirst;

	private int concededFirst;

	/** 対戦のあったシーズン数 */
	private int seasons;

	/** 最初・最後の対戦（日本時間。無ければ null） */
	private String firstMatchTime;

	private String lastMatchTime;

	/** 直近の対戦（新しい順。limit 件まで） */
	private List<HeadToHeadMatchDTO> matches = new ArrayList<>();
}
