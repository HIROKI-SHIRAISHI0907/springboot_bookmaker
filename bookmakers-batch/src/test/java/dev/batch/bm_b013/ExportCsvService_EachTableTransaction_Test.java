package dev.batch.bm_b013;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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

import dev.batch.repository.bm.BookDataRepository;
import dev.batch.repository.master.CountryLeagueMasterBatchRepository;
import dev.common.config.PathConfig;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * ExportCsvService_EachTableTransaction間のテスト
 * @author shiraishitoshio
 *
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
public class ExportCsvService_EachTableTransaction_Test {

	@TempDir
	Path tempDir;

	@Autowired
	private EachTableTransaction eachTableTransaction;

	@Mock
	private PathConfig pathConfig;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/** チームバッチレポジトリ */
	@Autowired
	private CountryLeagueMasterBatchRepository countryLeagueMasterBatchRepository;

	/** bookレポジトリ */
	@Autowired
	private BookDataRepository bookDataRepository;

	@BeforeEach
	void setUp() throws Exception {
	    Path testOutputDir = Paths.get("src", "test", "java", "dev", "batch", "bm_b013", "data");
	    Files.createDirectories(testOutputDir);

	    // ExportCsvService の CSV 出力先を固定フォルダへ切り替え
	    when(pathConfig.getCsvFolder()).thenReturn(testOutputDir.toAbsolutePath().toString());
	}

	@Test
	@DisplayName("ExportCsvServiceでローカルCSV作成後、EachTableTransactionの処理が通る")
	void exportCsv_then_eachTableTransaction() throws Exception {

		// --- when 2 ---
		// SeasonDataWrapper の判定結果を模した DTO を作る
		TransactionDTO dto = new TransactionDTO();
		List<String> countryLeague = new ArrayList<String>();
		countryLeague.add("日本-J2 リーグ");
		dto.setCountryLeague(countryLeague);

		eachTableTransaction.execute(dto);

		List<CountryLeagueMasterEntity> resultCountryLeagueMasterEntities =
				this.countryLeagueMasterBatchRepository.findDelete("日本", "J2 リーグ");
		for (CountryLeagueMasterEntity entity : resultCountryLeagueMasterEntities) {
			assertEquals("1", entity.getDelFlg());
		}

		int result = this.bookDataRepository.findChk();
		assertEquals(5, result);

	}
}
