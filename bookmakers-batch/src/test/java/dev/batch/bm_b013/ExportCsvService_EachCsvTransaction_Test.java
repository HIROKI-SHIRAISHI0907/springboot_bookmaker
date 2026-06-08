package dev.batch.bm_b013;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

import dev.batch.repository.bm.CsvDetailManageBatchRepository;
import dev.common.config.PathConfig;
import dev.common.logger.ManageLoggerComponent;

/**
 * ExportCsvService → EachCsvTransaction の結合テスト
 *
 * 前提:
 * - src/main/resources/schema.sql
 * - src/main/resources/data.sql
 * を SpringBootTest 起動時にそのまま読み込む
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(
    scripts = {
        "classpath:schema-bm.sql",
        "classpath:data-bm.sql"
    },
    config = @SqlConfig(
        dataSource = "bmDataSource",
        transactionManager = "bmTxManager",
        encoding = "UTF-8"
    )
)
@Sql(
    scripts = {
        "classpath:schema-master.sql",
        "classpath:data-master.sql"
    },
    config = @SqlConfig(
        dataSource = "masterDataSource",
        transactionManager = "masterTxManager",
        encoding = "UTF-8"
    )
)
public class ExportCsvService_EachCsvTransaction_Test {

	@TempDir
	Path tempDir;

	@Autowired
	private EachCsvTransaction eachCsvTransaction;

	@MockBean
	private PathConfig pathConfig;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	@Autowired
	private CsvDetailManageBatchRepository csvDetailManageBatchRepository;

	@BeforeEach
	void setUp() throws Exception {
	    Path testOutputDir = Paths.get("src", "test", "java", "dev", "batch", "bm_b013", "data");
	    Files.createDirectories(testOutputDir);

	    // ExportCsvService の CSV 出力先を固定フォルダへ切り替え
	    when(pathConfig.getCsvFolder()).thenReturn(testOutputDir.toAbsolutePath().toString());
	    when(pathConfig.getS3BucketsStats()).thenReturn(testOutputDir.toAbsolutePath().toString());
	}

	@Test
	@DisplayName("ExportCsvServiceでローカルCSV作成後、EachCsvTransactionチェック")
	void exportCsv_then_eachCsvTransaction() throws Exception {

		// --- when 2 ---
		// SeasonDataWrapper の判定結果を模した DTO を作る
		TransactionDTO dto = new TransactionDTO();
		List<String> countryLeague = new ArrayList<String>();
		countryLeague.add("日本-J2 リーグ");
		dto.setCountryLeague(countryLeague);
		Map<String, String> countryLeagueMap = new LinkedHashMap<>();
		countryLeagueMap.put("日本-J2 リーグ", "2026-06-01 12:00:00");
		dto.setCountryLeagueMap(countryLeagueMap);

		eachCsvTransaction.execute(dto);

		int result = csvDetailManageBatchRepository.check();
		assertEquals(2, result);

	}
}
