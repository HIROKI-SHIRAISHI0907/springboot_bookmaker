package dev.application.analyze.common.entity;

import lombok.Data;

@Data
public class StatMetaEntity {

	/**
	 * 試合IDです
	 */
	private String matchId;

	/**
	 * 国です
	 */
	private String country;

	/**
	 * リーグIDです
	 */
	private String leagueId;

	/**
	 * リーグ名です
	 */
	private String leagueName;

	/**
	 * チームIDです
	 */
	private String teamId;

	/**
	 * チーム名です
	 */
	private String teamName;

	/**
	 * 対戦相手チームIDです
	 */
	private String opponentTeamId;

	/**
	 * 対戦相手チーム名です
	 */
	private String opponentTeamName;

}
