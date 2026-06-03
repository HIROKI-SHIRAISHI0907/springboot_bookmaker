package dev.batch.bm_b013;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
	    webEnvironment = SpringBootTest.WebEnvironment.NONE,
	    properties = {
	        "spring.datasource.master.url=jdbc:postgresql://localhost:54320/soccer_bm_master",
	        "spring.datasource.master.username=postgres",
	        "spring.datasource.master.password=sonic3717",
	        "spring.datasource.master.driver-class-name=org.postgresql.Driver",

	        "spring.datasource.bm.url=jdbc:postgresql://localhost:54320/soccer_bm",
	        "spring.datasource.bm.username=postgres",
	        "spring.datasource.bm.password=sonic3717",
	        "spring.datasource.bm.driver-class-name=org.postgresql.Driver"
	    }
	)
	@ActiveProfiles("prod")
public class AutoSeasonHyphenBatchTest {

	@Autowired
    private AutoSeasonHyphenBatch batch;

    @Test
    void execute_shouldReturnZero_whenExecutionSuccessful() throws Exception {
        // Act
    	int result = batch.execute();

        // Assert
        assertEquals(0, result); // 戻り値が0であること
    }

}
