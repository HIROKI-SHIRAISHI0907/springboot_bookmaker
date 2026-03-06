package dev.batch.bm_b011;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.batch.bm_b011.ExportCsv;

@SpringBootTest
@ActiveProfiles("test")
public class ExportCsvItTest {

	@Autowired
	private ExportCsv target;

	@Test
	void test1() throws Exception {
		target.execute();
	}

}
