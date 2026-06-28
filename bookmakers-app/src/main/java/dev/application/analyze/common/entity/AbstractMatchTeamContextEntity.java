package dev.application.analyze.common.entity;

import java.io.Serializable;

import lombok.Data;

/**
 * 共通メタデータ
 * @author shiraishitoshio
 *
 */
@Data
public class AbstractMatchTeamContextEntity implements Serializable {

	/** シリアライズID */
    private static final long serialVersionUID = 1L;

	/**
	 * 試合IDです
	 */
	private String matchId;

	/**
	 * シーズンです
	 */
	private String season;

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
