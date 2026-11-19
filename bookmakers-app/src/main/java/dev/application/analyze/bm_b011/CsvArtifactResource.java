package dev.application.analyze.bm_b011;

// 2026-11-20 bookmakers-batch（dev.batch.bm_b011.CsvArtifactResource）から移動（B011 を統計用の bookmakers-app で動かすため）

import java.util.List;

import lombok.Data;

/**
 * CSV構成Resource
 * @author shiraishitoshio
 *
 */
@Data
public class CsvArtifactResource {

	/** 通番リスト */
	private List<Integer> seqList;

	/** 条件 */
	private String homeScore;

	/** 条件 */
	private String awayScore;

	/** 国 */
	private List<String> country;

	/** リーグ */
	private List<String> league;

}
