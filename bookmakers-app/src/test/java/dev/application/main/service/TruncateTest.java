package dev.application.main.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.application.domain.repository.bm.TruncateRepository;

/**
 * Truncate削除テスト
 * @author shiraishitoshio
 *
 */
@SpringBootTest
@ActiveProfiles("test")
public class TruncateTest {

	/** TruncateRepository */
	@Autowired
	private TruncateRepository truncateRepository;

	@Test
	void execute_shouldReturnZero_whenExecutionSuccessful() throws Exception {
		for (int i = 1; i <= TruncateUtil.TABLE_MAP.size(); i++) {
			// Act
			truncateRepository.truncate(TruncateUtil.getTableMap(i));
		}
	}
}
