package dev.batch.bm_b013;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

import dev.batch.repository.master.CountryLeagueSeasonMasterBatchRepository;
import dev.common.config.PathConfig;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * ExportCsvService → AutoSeasonHyphenTransaction の結合テスト
 *
 * 前提:
 * - src/main/resources/schema.sql
 * - src/main/resources/data.sql
 * を SpringBootTest 起動時にそのまま読み込む
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = {
		"classpath:schema-bm.sql",
		"classpath:data-bm.sql"
}, config = @SqlConfig(dataSource = "bmDataSource", transactionManager = "bmTxManager", encoding = "UTF-8"))
@Sql(scripts = {
		"classpath:schema-master.sql",
		"classpath:data-master.sql"
}, config = @SqlConfig(dataSource = "masterDataSource", transactionManager = "masterTxManager", encoding = "UTF-8"))
public class ExportCsvService_AutoSeasonHyphenTransaction_Test {

	@TempDir
	Path tempDir;

	@Autowired
	private AutoSeasonHyphenTransaction autoSeasonHyphenTransaction;

	@Mock
	private PathConfig pathConfig;

	@Mock
	private ManageLoggerComponent manageLoggerComponent;

	/** シーズンバッチレポジトリ */
	@Autowired
	private CountryLeagueSeasonMasterBatchRepository countryLeagueSeasonMasterBatchRepository;

	@BeforeEach
	void setUp() throws Exception {
		Path testOutputDir = Paths.get("src", "test", "java", "dev", "batch", "bm_b013", "data");
		Files.createDirectories(testOutputDir);

		// ExportCsvService の CSV 出力先を固定フォルダへ切り替え
		when(pathConfig.getCsvFolder()).thenReturn(testOutputDir.toAbsolutePath().toString());
	}

	@Test
	@DisplayName("ExportCsvServiceでローカルCSV作成後、AutoSeasonHyphenTransactionでend_season_dateをNULL更新できる")
	void exportCsv_then_autoSeasonHyphenTransaction() throws Exception {

		// --- when 2 ---
		// SeasonDataWrapper の判定結果を模した DTO を作る
		TransactionDTO dto = new TransactionDTO();
		Map<String, String> countryLeagueMap = new LinkedHashMap<>();
		countryLeagueMap.put("日本-J2 リーグ", "2026-06-01 12:00:00");
		dto.setCountryLeagueMap(countryLeagueMap);

		autoSeasonHyphenTransaction.execute(dto);

		List<CountryLeagueSeasonMasterEntity> resultCountryLeagueSeasonMasterEntities = this.countryLeagueSeasonMasterBatchRepository
				.findWhereData("日本", "J2 リーグ");

		assertEquals(null, resultCountryLeagueSeasonMasterEntities.get(0).getEndSeasonDate());

	}
}
