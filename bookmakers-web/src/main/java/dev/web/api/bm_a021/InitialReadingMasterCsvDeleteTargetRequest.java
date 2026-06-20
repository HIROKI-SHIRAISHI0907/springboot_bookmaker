package dev.web.api.bm_a021;

import java.util.List;

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

}
