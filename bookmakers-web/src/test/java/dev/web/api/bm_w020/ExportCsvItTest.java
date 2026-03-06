package dev.web.api.bm_w020;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
