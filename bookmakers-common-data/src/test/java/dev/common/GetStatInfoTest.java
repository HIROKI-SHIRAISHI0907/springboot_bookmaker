package dev.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dev.common.entity.BookDataEntity;
import dev.common.getstatinfo.GetStatInfo;

@SpringBootTest
class GetStatInfoTest {

	@Autowired
	private GetStatInfo getStatInfo;

	@Test
	void getData_shouldReturnMap_whenAllDataIsValid() throws Exception {
		// Act
		String csvNumber = "3000";
		Map<String, Map<String, List<BookDataEntity>>> result = this.getStatInfo.getData(csvNumber, null);

		// Assert
		assertNotNull(result);
		assertTrue(result.containsKey("JPN-J1"));

		Map<String, List<BookDataEntity>> innerMap = result.get("JPN-J1");
		assertTrue(innerMap.containsKey("Kawasaki-Tokyo"));
		assertEquals(2, innerMap.get("Kawasaki-Tokyo").size());
	}
}
