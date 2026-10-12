package dev.web.api.matchDetail.matchDetailDTO;

import lombok.Data;

/**
 * 試合情報（各項目は空でない一番新しい値）
 * @author shiraishitoshio
 */
@Data
public class MatchInfoDTO {

	private String stadium;

	private String location;

	private String capacity;

	private String audience;

	private String referee;

	private String weather;

	private String temperature;

	private String humidity;

	private String homeManager;

	private String awayManager;

	private String homeFormation;

	private String awayFormation;

	/** ゴールの記録（DB の値そのまま） */
	private String goalTime;

	private String goalMember;
}
