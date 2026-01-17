package dev.batch.bm_b003;

import static dev.batch.general.CsvHeaderMaps.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import dev.batch.config.TestMyBatisH2Config;
import dev.batch.general.CsvImport;
import dev.batch.repository.master.CountryLeagueSeasonMasterBatchRepository;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * SeasonMasterBatchDBITTest
 * @author shiraishitoshio
 *
 */
@Tag("IT")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestMyBatisH2Config.class)
@TestPropertySource(properties = {
		// --- app ---
		"app.b001-json-folder=bookmakers-batch/src/test/resources/json/b001/",
		"app.csv-folder=bookmakers-batch/src/test/resources/csv/",
		"app.output-csv-folder=bookmakers-batch/src/test/resources/outputs/",
		"app.team-csv-folder=bookmakers-batch/src/test/resources/teams_by_league/",
		"app.future-csv-folder=bookmakers-batch/src/test/resources/future/",
		"app.team-csv-folder=bookmakers-batch/src/test/resources/teams_by_league/",
		"app.future-csv-folder=bookmakers-batch/src/test/resources/future/",

		// --- process.python ---
		"process.python.root=/Users/shiraishitoshio/bookmaker/team_master_python",
		"process.python.pythonBin=python3",

		// --- process.s3 ---
		"process.s3.region=ap-northeast-1",
		"process.s3.buckets.teamSeasonDateData=aws-s3-team-season-date-data",
		"process.s3.buckets.teamData=aws-s3-team-data",
		"process.s3.buckets.teamMemberData=aws-s3-team-member-data",
		"process.s3.buckets.outputs=aws-s3-outputs",

		// --- data.sqlを実行しない
		"spring.sql.init.mode=never"
})
@Sql(scripts = "classpath:empty.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class SeasonMasterBatchDBITTest {

	/** BM_M032統計分析ロジック */
	@Autowired
	private CountryLeagueSeasonMasterStat countryLeagueSeasonMasterStat;

	/** CountryLeagueSeasonDBPart */
	@Autowired
	private CountryLeagueSeasonDBPart countryLeagueSeasonDBPart;

	@Autowired
	private CountryLeagueSeasonMasterBatchRepository countryLeagueSeasonMasterRepository;

	/** バッチ共通ログ出力を行う。 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 試験データ登録確認
	 * @param testInfo
	 * @throws Exception
	 */
	@Test
	void execute_TC_TS_001(TestInfo testInfo) throws Exception {
		String testMethodName = testInfo.getTestMethod()
				.map(m -> m.getName())
				.orElse("unknown");

		List<CountryLeagueSeasonMasterEntity> insertList = CsvImport.importCsv(
				"src/test/java/dev/batch/"
						+ "bm_b003/data/" + testMethodName + ".csv",
				CountryLeagueSeasonMasterEntity.class,
				SEASON,
				null);
		this.countryLeagueSeasonMasterStat.seasonStat(insertList);
		List<CountryLeagueSeasonMasterEntity> data = this.countryLeagueSeasonMasterRepository.findData();
		assertEquals(44, data.size());
	}

	/**
	 * 試験データ登録確認
	 * @param testInfo
	 * @throws Exception
	 */
	@Test
	void execute_TC_TS_002(TestInfo testInfo) throws Exception {
		boolean result = this.countryLeagueSeasonDBPart.dbOperation(null);
		assertTrue(result);
	}

	/**
	 * 試験データ登録確認
	 * @param testInfo
	 * @throws Exception
	 */
	@Test
	void execute_TC_TS_003(TestInfo testInfo) throws Exception {
		boolean result = this.countryLeagueSeasonDBPart.dbOperation(
				new ArrayList<CountryLeagueSeasonMasterEntity>());
		assertTrue(result);
	}

	/**
	 * 試験データバッチキー不正
	 * @param testInfo
	 * @throws Exception
	 */
	@Test
	void execute_TC_TS_004(TestInfo testInfo) throws Exception {
		String testMethodName = testInfo.getTestMethod()
				.map(m -> m.getName())
				.orElse("unknown");

		List<CountryLeagueSeasonMasterEntity> insertList = CsvImport.importCsv(
				"src/test/java/dev/batch/"
						+ "bm_b003/data/" + testMethodName + ".csv",
				CountryLeagueSeasonMasterEntity.class,
				SEASON,
				null);
		boolean result = this.countryLeagueSeasonDBPart.dbOperation(insertList);
		assertTrue(result);
	}

	/**
	 * 試験データ混在チェック
	 * @param testInfo
	 * @throws Exception
	 */
	@Test
	void execute_TC_TS_005(TestInfo testInfo) throws Exception {
		String testMethodName = testInfo.getTestMethod()
				.map(m -> m.getName())
				.orElse("unknown");

		List<CountryLeagueSeasonMasterEntity> insertList = CsvImport.importCsv(
				"src/test/java/dev/batch/"
						+ "bm_b003/data/" + testMethodName + "_before.csv",
				CountryLeagueSeasonMasterEntity.class,
				SEASON,
				null);
		boolean result = this.countryLeagueSeasonDBPart.dbOperation(insertList);
		assertTrue(result);

		List<CountryLeagueSeasonMasterEntity> insertList2 = CsvImport.importCsv(
				"src/test/java/dev/batch/"
						+ "bm_b003/data/" + testMethodName + "_after.csv",
				CountryLeagueSeasonMasterEntity.class,
				SEASON,
				null);
		boolean result2 = this.countryLeagueSeasonDBPart.dbOperation(insertList2);
		assertTrue(result2);

		List<CountryLeagueSeasonMasterEntity> data = this.countryLeagueSeasonMasterRepository.findData();
		assertEquals(5, data.size());
		for (CountryLeagueSeasonMasterEntity entity : data) {
			switch (entity.getLeague()) {
			case "Liga Profesional": {
				assertion("アルゼンチン", entity.getLeague(), "2027",
						"2027-02-28 00:00:00+09", "2027-12-15 00:00:00+09",
						"16", "/soccer/argentina/liga-profesional/",
						"https://static.flashscore.com/res/image/data/MHE5g4CO-pSuU5XZj.png",
						"0", entity);
				break;
			}
			case "トルネオ・ベターノ": {
				assertion("アルゼンチン", entity.getLeague(), "2026",
						"2026-01-26 00:00:00+09", "2026-11-09 00:00:00+09",
						"16", "/soccer/argentina/liga-profesional/",
						"https://static.flashscore.com/res/image/data/MHE5g4CO-pSuU5XZj.png",
						"1", entity);
				break;
			}
			case "セリエ A": {
				if ("0".equals(entity.getDelFlg())) {
					assertion("イタリア", entity.getLeague(), "2027/2028",
							"2027-09-24 00:00:00+09", "2028-06-21 00:00:00+09",
							null, "/soccer/italy/serie-a-4/",
							"https://static.flashscore.com/res/image/data/rFHMayEO-G0cMYJZK.png",
							"0", entity);
					break;
				} else {
					assertion("イタリア", entity.getLeague(), "2025/2026",
							"2025-08-24 00:00:00+09", "2026-05-24 00:00:00+09",
							null, "/soccer/italy/serie-a/",
							"https://static.flashscore.com/res/image/data/rFHMayEO-G0cMYJZK.png",
							"1", entity);
					break;
				}
			}
			case "セリエ B": {
				assertion("イタリア", entity.getLeague(), "2025/2026",
						"2025-08-23 00:00:00+09", "2026-05-10 00:00:00+09",
						"9", "/soccer/italy/serie-b/",
						"https://static.flashscore.com/res/image/data/4U93rccc-KjZx5Ik4.png",
						"0", entity);
				break;
			}
			}
		}
	}

	/**
	 * アサーション
	 * @param entity
	 */
	private static void assertion(
			String country,
			String league,
			String seasonYear,
			String seasonStart,
			String seasonEnd,
			String round,
			String path,
			String icon,
			String delFlg,
			CountryLeagueSeasonMasterEntity entity) {
		System.out.println("[START] " + country + ": " + league + ": ");
		assertEquals("[COUNTRY]: " + country, "[COUNTRY]: " + entity.getCountry());
		assertEquals("[LEAGUE]: " + league, "[LEAGUE]: " + entity.getLeague());
		assertEquals("[SEASONYEAR]: " + seasonYear, "[SEASONYEAR]: " + entity.getSeasonYear());
		assertEquals("[SEASONSTART]: " + seasonStart, "[SEASONSTART]: " + entity.getStartSeasonDate());
		assertEquals("[SEASONEND]: " + seasonEnd, "[SEASONEND]: " + entity.getEndSeasonDate());
		assertEquals("[ROUND]: " + round, "[ROUND]: " + entity.getRound());
		assertEquals("[PATH]: " + path, "[PATH]: " + entity.getPath());
		assertEquals("[ICON]: " + icon, "[ICON]: " + entity.getIcon());
		assertEquals("[DELFLG]: " + delFlg, "[DELFLG]: " + entity.getDelFlg());
		System.out.println("[END] " + country + ": " + league + ": ");
	}
}
