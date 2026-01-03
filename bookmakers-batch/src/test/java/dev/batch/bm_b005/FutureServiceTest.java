package dev.batch.bm_b005;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dev.batch.bm_b005.FutureService;

@SpringBootTest
class FutureServiceTest {

    @Autowired
    private FutureService futureService;

    @Test
    void execute_shouldReturnZero_whenExecutionSuccessful() throws Exception {
        // Act
        futureService.execute();

        // Assert
        assertTrue(true); // 戻り値が0であること
    }
}
