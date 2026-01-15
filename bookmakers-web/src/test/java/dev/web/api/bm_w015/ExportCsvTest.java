package dev.web.api.bm_w015;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.web.api.bm_w015.ExportCsv;

@SpringBootTest
@ActiveProfiles("test")
public class ExportCsvTest {

	@Autowired
    private ExportCsv exportCsv;

    @Test
    void execute_shouldReturnZero_whenExecutionSuccessful() throws Exception {
        // Act
        this.exportCsv.execute();
    }

}
