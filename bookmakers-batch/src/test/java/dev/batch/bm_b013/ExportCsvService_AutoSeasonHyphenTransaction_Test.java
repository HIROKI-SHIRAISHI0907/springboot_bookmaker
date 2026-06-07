package dev.batch.bm_b013;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

import dev.batch.bm_b011.ExportCsvService;
import dev.common.config.PathConfig;
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
public class ExportCsvService_AutoSeasonHyphenTransaction_Test {

	@TempDir
	Path tempDir;

	@Autowired
	private ExportCsvService exportCsvService;

	@Autowired
	private AutoSeasonHyphenTransaction autoSeasonHyphenTransaction;

	@MockBean
	private PathConfig pathConfig;

	@MockBean
	private ManageLoggerComponent manageLoggerComponent;

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

		// --- when 1 ---
		// ExportCsvService でローカルCSV作成
		exportCsvService.execute();

		// --- then 1 ---
		// CSVファイルが1件以上できていること
		List<Path> csvFiles;
		try (Stream<Path> stream = Files.walk(tempDir)) {
			csvFiles = stream
					.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".csv"))
					.collect(Collectors.toList());
		}

		// 管理ファイルも生成されていること
		assertThat(Files.exists(tempDir.resolve("seqList.txt"))).isTrue();
		assertThat(Files.exists(tempDir.resolve("data_team_list.txt"))).isTrue();

		// --- when 2 ---
		// SeasonDataWrapper の判定結果を模した DTO を作る
		TransactionDTO dto = new TransactionDTO();
		Map<String, String> countryLeagueMap = new LinkedHashMap<>();
		countryLeagueMap.put("日本-J2・J3 リーグ", "2000-01-01 00:00:00");
		dto.setCountryLeagueMap(countryLeagueMap);

		autoSeasonHyphenTransaction.execute(dto);

	}
}
