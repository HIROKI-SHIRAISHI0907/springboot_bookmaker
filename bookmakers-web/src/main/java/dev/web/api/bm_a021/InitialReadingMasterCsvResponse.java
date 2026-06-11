package dev.web.api.bm_a021;

import java.util.List;

import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import lombok.Data;

@Data
public class InitialReadingMasterCsvResponse {

	/** マスタ名 */
	private String masterName;

	/** countryLeagueSeasonMasterEntityList */
	private List<CountryLeagueSeasonMasterEntity> countryLeagueSeasonMasterEntityList;

	/** countryLeagueMasterEntityList */
	private List<CountryLeagueMasterEntity> countryLeagueMasterEntityList;

}
