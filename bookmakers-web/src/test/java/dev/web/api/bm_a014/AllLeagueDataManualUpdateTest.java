package dev.web.api.bm_a014;


import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.common.entity.AllLeagueMasterEntity;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.web.api.bm_a010.TeamColorDTO;
import dev.web.api.bm_w020.CsvHeaderMaps;
import dev.web.api.bm_w020.CsvImport;


/**
 * BM_C001CSVロジックテスト
 * @author shiraishitoshio
 *
 */
@SpringBootTest
@ActiveProfiles("test")
public class AllLeagueDataManualUpdateTest {

	/**
	 * 処理速度実験
	 * @throws Exception
	 */
	@Test
	void test_calcStat_shouldUpdateCorrectly_memory() throws Exception {
		// Act
		List<AllLeagueMasterEntity> list1 = CsvImport.importCsv(
				"src/test/java/dev/web/api/"
						+ "bm_a014/data/soccer_bm_all_league_scrape_master.csv",
						AllLeagueMasterEntity.class,
				CsvHeaderMaps.ALL_LEAGUE_ORIGIN
				);

		CsvExport.exportCsv(
				"src/test/java/dev/web/api/"
						+ "bm_a014/data/soccer_bm_all_league_scrape_master_new.csv",
						list1,
						CsvHeaderMaps.ALL_LEAGUE_ORIGIN);

		List<CountryLeagueMasterEntity> list2 = CsvImport.importCsv(
				"src/test/java/dev/web/api/"
						+ "bm_a014/data/soccer_bm_country_league_master.csv",
						CountryLeagueMasterEntity.class,
				CsvHeaderMaps.COUNTRY_LEAGUE_ORIGIN
				);

		CsvExport.exportCsv(
				"src/test/java/dev/web/api/"
						+ "bm_a014/data/soccer_bm_country_league_master_new.csv",
						list2,
						CsvHeaderMaps.COUNTRY_LEAGUE_ORIGIN);

		List<TeamColorDTO> list3 = CsvImport.importCsv(
				"src/test/java/dev/web/api/"
						+ "bm_a014/data/soccer_bm_team_color_master.csv",
						TeamColorDTO.class,
				CsvHeaderMaps.TEAM_COLOR_ORIGIN
				);

		CsvExport.exportCsv(
				"src/test/java/dev/web/api/"
						+ "bm_a014/data/soccer_bm_team_color_master_new.csv",
						list3,
						CsvHeaderMaps.TEAM_COLOR_ORIGIN);

	}


}
