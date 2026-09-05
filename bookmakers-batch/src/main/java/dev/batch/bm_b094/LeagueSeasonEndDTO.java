package dev.batch.bm_b094;

import java.sql.Timestamp;

import dev.batch.repository.master.CountryLeagueSeasonMasterBatchRepository;
import lombok.Data;

/**
 * LeagueSeasonEndDTO
 *
 * country_league_season_master から取得する、シーズン終了予定日が近いリーグの情報。
 * bm-mail-006（シーズン終了間近のお知らせ）のbikou組み立て用に、
 * 表示形式に整形済みの文字列として保持する。
 *
 * ※ country・leagueは個別に保持しつつ、メール表示用の結合済み文字列は
 *   {@link #getLeagueName()} として本クラスで提供する。
 *
 * @see CountryLeagueSeasonMasterBatchRepository#findLeaguesEndingBetween
 *
 * @author shiraishitoshio
 */
@Data
public class LeagueSeasonEndDTO {

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** シーズン終了予定日（DB値そのまま、timestamptz/UTC） */
	private Timestamp endSeasonDate;

	/**
	 * リーグ表示名（country + league）を返す。
	 * bm-mail-006の{{LEAGUE_NAME}}プレースホルダーに使用する。
	 *
	 * @return リーグ表示名
	 */
	public String getLeagueName() {
		return country + ": " + league;
	}
}