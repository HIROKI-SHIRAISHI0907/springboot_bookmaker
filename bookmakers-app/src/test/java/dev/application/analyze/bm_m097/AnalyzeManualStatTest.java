package dev.application.analyze.bm_m097;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
* BM_M097統計分析ロジックテスト
* @author shiraishitoshio
*
*/
@SpringBootTest(properties = {
		"batch.mode=test",
		"spring.test.database.replace=none"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public class AnalyzeManualStatTest {

	@Autowired
	private AnalyzeManualStat analyzeManualStat;

	@Test
	void calcStat() throws Exception {
		// --- when ---
		analyzeManualStat.manualStat();
	}

}
