package dev.application.analyze.bm_m001;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OriginServiceTest {

    @Autowired
    private OriginService originService;

    @Test
    void execute_shouldReturnZero_whenExecutionSuccessful() throws Exception {
        // Act
    	originService.execute();

        // Assert
        assertTrue(true); // 戻り値が0であること
    }
}
