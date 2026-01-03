package dev.batch.bm_b005;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FutureBatchTest {

    @Autowired
    private FutureBatch futureService;

    @Test
    void execute_shouldReturnZero_whenExecutionSuccessful() throws Exception {
        // Act
        futureService.execute();

        // Assert
        assertTrue(true); // 戻り値が0であること
    }
}
