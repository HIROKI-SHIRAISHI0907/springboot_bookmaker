package dev.application.main.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StatServiceTest {

    @Autowired
    private StatService statService;

    @Test
    void execute_shouldReturnZero_whenExecutionSuccessful() throws Exception {
        // Act
        int result = statService.execute(null);

        // Assert
        assertEquals(0, result); // 戻り値が0であること
    }
}
