package dev.batch.bm_b004;

import static dev.batch.general.CsvHeaderMaps.*;
import static org.junit.jupiter.api.Assertions.*;

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
import dev.batch.repository.master.CountryLeagueMasterRepository;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * MasterBatchDBITTest
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
@Sql(
		scripts = "classpath:empty.sql",
		executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
public class MasterBatchDBITTest {

	/** BM_M032統計分析ロジック */
	@Autowired
	private CountryLeagueMasterStat countryLeagueMasterStat;

	/** CountryLeagueDBPart */
	@Autowired
	private CountryLeagueDBPart countryLeagueDBPart;

	@Autowired
	private CountryLeagueMasterRepository countryLeagueMasterRepository;

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

		String f1 = "src/test/java/dev/batch/bm_b004/data/" + testMethodName + "_J1.csv";
		String f2 = "src/test/java/dev/batch/bm_b004/data/" + testMethodName + "_J2.csv";
		String f3 = "src/test/java/dev/batch/bm_b004/data/" + testMethodName + "_J3.csv";

		List<CountryLeagueMasterEntity> rows1 = CsvImport.importCsv(
				f1, CountryLeagueMasterEntity.class, TEAM, null);
		List<CountryLeagueMasterEntity> rows2 = CsvImport.importCsv(
				f2, CountryLeagueMasterEntity.class, TEAM, null);
		List<CountryLeagueMasterEntity> rows3 = CsvImport.importCsv(
				f3, CountryLeagueMasterEntity.class, TEAM, null);

		this.countryLeagueMasterStat.masterStat(f1, rows1);
		this.countryLeagueMasterStat.masterStat(f2, rows2);
		this.countryLeagueMasterStat.masterStat(f3, rows3);

		List<CountryLeagueMasterEntity> data = this.countryLeagueMasterRepository.findData();
		assertEquals(60, data.size());
	}

	@Test
	void execute_TC_TS_002(TestInfo testInfo) throws Exception {
		boolean result = this.countryLeagueDBPart.dbOperation(null);
		assertTrue(result);
	}

	@Test
	void execute_TC_TS_003(TestInfo testInfo) throws Exception {
		boolean result = this.countryLeagueDBPart.dbOperation(
				new java.util.ArrayList<CountryLeagueMasterEntity>());
		assertTrue(result);
	}

	@Test
	void execute_TC_TS_004(TestInfo testInfo) throws Exception {
		String testMethodName = testInfo.getTestMethod()
				.map(m -> m.getName())
				.orElse("unknown");

		List<CountryLeagueMasterEntity> csvRows = CsvImport.importCsv(
				"src/test/java/dev/batch/"
						+ "bm_b004/data/" + testMethodName + ".csv",
				CountryLeagueMasterEntity.class,
				TEAM,
				null);
		boolean result = this.countryLeagueDBPart.dbOperation(csvRows);
		assertFalse(result);
	}

	@Test
	void execute_TC_TS_005(TestInfo testInfo) throws Exception {
		String testMethodName = testInfo.getTestMethod()
				.map(m -> m.getName())
				.orElse("unknown");

		List<CountryLeagueMasterEntity> csvRows = CsvImport.importCsv(
				"src/test/java/dev/batch/"
						+ "bm_b004/data/" + testMethodName + ".csv",
				CountryLeagueMasterEntity.class,
				TEAM,
				null);
		boolean result = this.countryLeagueDBPart.dbOperation(csvRows);
		assertFalse(result);
	}

	@Test
	void execute_TC_TS_006(TestInfo testInfo) throws Exception {
		String testMethodName = testInfo.getTestMethod()
				.map(m -> m.getName())
				.orElse("unknown");

		String f1 = "src/test/java/dev/batch/bm_b004/data/" + testMethodName + ".csv";
		List<CountryLeagueMasterEntity> rows = CsvImport.importCsv(
				f1, CountryLeagueMasterEntity.class, TEAM, null);

		this.countryLeagueMasterStat.masterStat(f1, rows);

		List<CountryLeagueMasterEntity> data = this.countryLeagueMasterRepository.findData();
		assertEquals(6, data.size());
	}

	@Test
	void execute_TC_TS_007(TestInfo testInfo) throws Exception {
		String testMethodName = testInfo.getTestMethod()
				.map(m -> m.getName())
				.orElse("unknown");

		String before = "src/test/java/dev/batch/bm_b004/data/" + testMethodName + "_before.csv";
		List<CountryLeagueMasterEntity> beforeRows = CsvImport.importCsv(
				before, CountryLeagueMasterEntity.class, TEAM, null);

		this.countryLeagueMasterStat.masterStat(before, beforeRows);

		List<CountryLeagueMasterEntity> data = this.countryLeagueMasterRepository.findData();
		assertEquals(4, data.size());

		List<CountryLeagueMasterEntity> updateList = CsvImport.importCsv(
				"src/test/java/dev/batch/"
						+ "bm_b004/data/" + testMethodName + "_after.csv",
				CountryLeagueMasterEntity.class,
				TEAM,
				null);
		boolean result = this.countryLeagueDBPart.dbOperation(updateList);
		assertTrue(result);

		List<CountryLeagueMasterEntity> dataRows = this.countryLeagueMasterRepository.findData();
		assertEquals(4, dataRows.size());

		for (CountryLeagueMasterEntity entity : dataRows) {
			switch (entity.getTeam()) {
			case "いわきFC": {
				assertion("J2 リーグ", entity.getTeam(),
						"/team/iwaki/Mi3WmBrD/", "0", entity);
				break;
			}
			case "モンテディオ山形": {
				assertion("J2 リーグ", entity.getTeam(),
						"/team/montedio-yamagata/2uKzonBU/", "0", entity);
				break;
			}
			case "FC今治": {
				assertion("J2 リーグ", entity.getTeam(),
						"/team/imabari/0fQDWIvJ/", "0", entity);
				break;
			}
			case "北海道ｺﾝｻﾄﾞｰﾚ札幌": {
				assertion("J2 リーグ", entity.getTeam(),
						"/team/sapporo/Ak9yLKWF/", "0", entity);
				break;
			}
			}
		}
	}

	@Test
	void execute_TC_TS_008(TestInfo testInfo) throws Exception {
		String testMethodName = testInfo.getTestMethod()
				.map(m -> m.getName())
				.orElse("unknown");

		String before = "src/test/java/dev/batch/bm_b004/data/" + testMethodName + "_before.csv";
		List<CountryLeagueMasterEntity> beforeRows = CsvImport.importCsv(
				before, CountryLeagueMasterEntity.class, TEAM, null);

		this.countryLeagueMasterStat.masterStat(before, beforeRows);

		List<CountryLeagueMasterEntity> data = this.countryLeagueMasterRepository.findData();
		assertEquals(4, data.size());

		List<CountryLeagueMasterEntity> updateList = CsvImport.importCsv(
				"src/test/java/dev/batch/"
						+ "bm_b004/data/" + testMethodName + "_after.csv",
				CountryLeagueMasterEntity.class,
				TEAM,
				null);
		boolean result = this.countryLeagueDBPart.dbOperation(updateList);
		assertTrue(result);

		List<CountryLeagueMasterEntity> updateList2 = CsvImport.importCsv(
				"src/test/java/dev/batch/"
						+ "bm_b004/data/" + testMethodName + "_after2.csv",
				CountryLeagueMasterEntity.class,
				TEAM,
				null);
		boolean result2 = this.countryLeagueDBPart.dbOperation(updateList2);
		assertTrue(result2);

		List<CountryLeagueMasterEntity> dataRows = this.countryLeagueMasterRepository.findData();
		assertEquals(5, dataRows.size());

		for (CountryLeagueMasterEntity entity : dataRows) {
			String t = entity.getTeam();
			if (!List.of("いわきFC","モンテディオ山形","FC今治","北海道ｺﾝｻﾄﾞｰﾚ札幌").contains(t)) {
				System.out.println("[UNMATCH] league=" + entity.getLeague()
						+ " team=[" + t + "] link=" + entity.getLink()
						+ " delFlg=" + entity.getDelFlg());
			}
			switch (t) {
			case "いわきFC": {
				assertion("J2 リーグ", entity.getTeam(),
						"/team/iwaki/Mi3WmBrD/", "1", entity);
				break;
			}
			case "モンテディオ山形": {
				assertion("J2 リーグ", entity.getTeam(),
						"/team/montedioyamagata/2uKzonBU/", "0", entity);
				break;
			}
			case "FC今治": {
				if ("J1 リーグ".equals(entity.getLeague())) {
					assertion("J1 リーグ", entity.getTeam(),
							"/team/imabari/0fQDWIvJ/", "0", entity);
				} else {
					assertion("J2 リーグ", entity.getTeam(),
							"/team/imabari/0fQDWIvJ/", "1", entity);
				}
				break;
			}
			case "北海道ｺﾝｻﾄﾞｰﾚ札幌": {
				assertion("J2 リーグ", entity.getTeam(),
						"/team/sapporo/Ak9yLKWF/", "0", entity);
				break;
			}
			}
		}
	}

	private static void assertion(
			String league,
			String team,
			String link,
			String delFlg,
			CountryLeagueMasterEntity entity) {
		System.out.println("[START] 日本: " + league + ": " + team);
		assertEquals("[COUNTRY]: " + "日本", "[COUNTRY]: " + entity.getCountry());
		assertEquals("[LEAGUE]: " + league, "[LEAGUE]: " + entity.getLeague());
		assertEquals("[LINK]: " + link, "[LINK]: " + entity.getLink());
		assertEquals("[DELFLG]: " + delFlg, "[DELFLG]: " + entity.getDelFlg());
		System.out.println("[END] 日本: " + league + ": " + team);
	}
}
