package dev.mng.csvmng;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dev.application.analyze.bm_m097.CsvMngInputDTO;

@SpringBootTest
public class ExportCsvTest {

	@Autowired
    private ExportCsv exportCsv;

    @Test
    void execute_shouldReturnZero_whenExecutionSuccessful() throws Exception {
        // Act
        this.exportCsv.execute(new CsvMngInputDTO());
    }

}
