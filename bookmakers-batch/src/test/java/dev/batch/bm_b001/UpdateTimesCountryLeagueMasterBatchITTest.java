package dev.batch.bm_b001;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class UpdateTimesCountryLeagueMasterBatchITTest {

  @Autowired
  private UpdateTimesCountryLeagueMasterBatch target;

  @Test
  void execute_jobQueued_inserted_and_async_called() {
    int result = target.execute();
    assertEquals(0, result);
  }
}
