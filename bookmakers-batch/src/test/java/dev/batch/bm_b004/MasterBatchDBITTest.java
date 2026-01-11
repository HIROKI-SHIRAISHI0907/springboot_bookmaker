package dev.batch.bm_b004;

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
@Sql(scripts = "classpath:empty.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
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

		List<List<CountryLeagueMasterEntity>> insertList =
				new ArrayList<List<CountryLeagueMasterEntity>>();
		insertList.add(CsvImport.importCsv(
				"src/test/java/dev/batch/"
						+ "bm_b004/data/" + testMethodName + "_J1.csv",
				CountryLeagueMasterEntity.class,
				TEAM,
				null));
		insertList.add(CsvImport.importCsv(
				"src/test/java/dev/batch/"
						+ "bm_b004/data/" + testMethodName + "_J2.csv",
				CountryLeagueMasterEntity.class,
				TEAM,
				null));
		insertList.add(CsvImport.importCsv(
				"src/test/java/dev/batch/"
						+ "bm_b004/data/" + testMethodName + "_J3.csv",
				CountryLeagueMasterEntity.class,
				TEAM,
				null));
        this.countryLeagueMasterStat.masterStat(insertList);
        List<CountryLeagueMasterEntity> data = this.countryLeagueMasterRepository.findData();
        assertEquals(60, data.size());
	}

	/**
	 * 試験データ登録確認
	 * @param testInfo
	 * @throws Exception
	 */
	@Test
	void execute_TC_TS_002(TestInfo testInfo) throws Exception {
        boolean result = this.countryLeagueDBPart.dbOperation(null);
        assertTrue(result);
	}

	/**
	 * 試験データ登録確認
	 * @param testInfo
	 * @throws Exception
	 */
	@Test
	void execute_TC_TS_003(TestInfo testInfo) throws Exception {
        boolean result = this.countryLeagueDBPart.dbOperation(
        		new ArrayList<CountryLeagueMasterEntity>());
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

		List<CountryLeagueMasterEntity> csvRows =
				CsvImport.importCsv(
				"src/test/java/dev/batch/"
						+ "bm_b004/data/" + testMethodName + ".csv",
				CountryLeagueMasterEntity.class,
				TEAM,
				null);
        boolean result = this.countryLeagueDBPart.dbOperation(csvRows);
        assertFalse(result);
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

		List<CountryLeagueMasterEntity> csvRows =
				CsvImport.importCsv(
				"src/test/java/dev/batch/"
						+ "bm_b004/data/" + testMethodName + ".csv",
				CountryLeagueMasterEntity.class,
				TEAM,
				null);
        boolean result = this.countryLeagueDBPart.dbOperation(csvRows);
        assertFalse(result);
	}

}
