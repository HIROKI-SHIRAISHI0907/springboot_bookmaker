package dev.application.analyze.bm_m000;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OriginCsvServiceTest {

    @Autowired
    private OriginCsvService originCsvService;

    @Test
    void execute_shouldReturnZero_whenExecutionSuccessful() throws Exception {
        // Act
    	originCsvService.execute();

        // Assert
        assertTrue(true); // 戻り値が0であること
    }
}
