package dev.web.api.bm_a021;

import java.util.List;

import dev.common.entity.AllLeagueMasterEntity;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import lombok.Data;

@Data
public class InitialReadingMasterCsvDeleteTargetRequest {

	/** マスタ名 */
	private String masterName;

	/** CountryLeagueSeasonMasterEntity */
	private List<CountryLeagueSeasonMasterEntity> seasonMasterEntities;

	/** CountryLeagueSeasonMasterEntity */
	private List<CountryLeagueMasterEntity> masterEntities;


	/** allLeagueMasterEntities（all_league_scrape_master。2026-11-20 追加） */
	private List<AllLeagueMasterEntity> allLeagueMasterEntities;
}
