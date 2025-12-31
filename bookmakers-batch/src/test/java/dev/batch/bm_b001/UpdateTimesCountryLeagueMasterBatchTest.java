package dev.batch.bm_b001;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "masterTxManager")
class UpdateTimesCountryLeagueMasterBatchTest {

	@Autowired
    private TestDataInserter inserter;

	@Autowired
	private UpdateTimesCountryLeagueMasterBatch target;

	@Test
	void execute_success_with_real_inserted_data() throws Exception {
		inserter.insertSeed();
		// ===== act =====
		int result = target.execute();
	}
}
