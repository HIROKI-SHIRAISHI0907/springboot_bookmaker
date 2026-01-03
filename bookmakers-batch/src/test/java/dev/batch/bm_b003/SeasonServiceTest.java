package dev.batch.bm_b003;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;


@SpringBootTest
@ActiveProfiles("test")
public class SeasonServiceTest {

	@Autowired
    private SeasonMasterBatch seasonMasterBatch;

    @Test
    void execute_shouldReturnZero_whenExecutionSuccessful() throws Exception {
        // Act
        int result = seasonMasterBatch.execute();

        // Assert
        assertEquals(0, result); // 戻り値が0であること
    }

}
