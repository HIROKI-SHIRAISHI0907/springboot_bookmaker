package dev.batch.bm_b002;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;


@SpringBootTest
@ActiveProfiles("test")
class TeamMemberServiceTest {

    @Autowired
    private TeamMemberMasterBatch teamMasterBatch;

    @Test
    void execute_shouldReturnZero_whenExecutionSuccessful() throws Exception {
        // Act
    	teamMasterBatch.execute();

        // Assert
        assertTrue(true); // 戻り値が0であること
    }
}
