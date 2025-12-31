package dev.application.main.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dev.batch.bm_b003.MasterBatch;

@SpringBootTest
public class MasterServiceTest {

	@Autowired
    private MasterBatch masterMasterBatch;

    @Test
    void execute_shouldReturnZero_whenExecutionSuccessful() throws Exception {
        // Act
        int result = masterMasterBatch.execute();

        // Assert
        assertEquals(0, result); // 戻り値が0であること
    }

}
