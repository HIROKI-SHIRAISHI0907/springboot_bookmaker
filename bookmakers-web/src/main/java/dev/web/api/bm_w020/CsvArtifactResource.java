package dev.web.api.bm_w020;

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
