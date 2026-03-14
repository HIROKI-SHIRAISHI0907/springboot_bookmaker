package dev.batch.bm_b010;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
	    "MASTER_DB_URL=jdbc:postgresql://localhost:54320/soccer_bm_master",
	    "MASTER_DB_USER=postgres",
	    "MASTER_DB_PASS=sonic3717",

	    "BM_DB_URL=jdbc:postgresql://localhost:54320/soccer_bm",
	    "BM_DB_USER=postgres",
	    "BM_DB_PASS=sonic3717",
	})
@ActiveProfiles("prod")
public class FinGettingBatchTest {

	@Autowired
    private FinGettingBatch batch;

    @Test
    void execute_shouldReturnZero_whenExecutionSuccessful() throws Exception {
        // Act
    	int result = batch.execute();

        // Assert
        assertEquals(0, result); // 戻り値が0であること
    }

}
