package dev.batch.bm_b004;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


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
