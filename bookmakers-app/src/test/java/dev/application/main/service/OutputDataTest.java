package dev.application.main.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OutputDataTest {

	@Autowired
	private OutputData outputData;

	@Test
	void test() throws IllegalArgumentException, IllegalAccessException {
		outputData.execute();
	}

}
