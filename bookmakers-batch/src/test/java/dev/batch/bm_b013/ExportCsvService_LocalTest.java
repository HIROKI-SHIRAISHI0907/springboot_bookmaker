package dev.batch.bm_b013;

import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
public class ExportCsvService_LocalTest {

	@TempDir
	Path tempDir;

	@MockBean
	private PathConfig pathConfig;

	@MockBean
	private ManageLoggerComponent manageLoggerComponent;

	@Autowired
	private ExportCsvService exportCsvService;

	@BeforeEach
	void setUp() throws Exception {
	    Path testOutputDir = Paths.get("src", "test", "java", "dev", "batch", "bm_b013", "data");
	    Files.createDirectories(testOutputDir);

	    // ExportCsvService の CSV 出力先を固定フォルダへ切り替え
	    when(pathConfig.getCsvFolder()).thenReturn(testOutputDir.toAbsolutePath().toString());
	}

	@Test
	@DisplayName("ExportCsvServiceでローカルCSV作成")
	void exportCsv() throws Exception {

		// --- when 1 ---
		exportCsvService.execute();

	}
}
