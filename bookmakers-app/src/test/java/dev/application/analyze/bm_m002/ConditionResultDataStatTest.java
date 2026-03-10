package dev.application.analyze.bm_m002;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.common.entity.BookDataEntity;
import dev.common.getinfo.GetStatInfo;

/**
 * BM_M002統計分析ロジックテスト
 * @author shiraishitoshio
 *
 */
@SpringBootTest
@ActiveProfiles("test")
class ConditionResultDataStatTest {

	@Autowired
	private GetStatInfo getStatInfo;

	@Autowired
	private ConditionResultDataStat conditionResultDataStat;

	@Test
	void test_calcStat_shouldUpdateCorrectly() {
		// Act
		String csvNumber = "4050";
		String csvNumberAfter = "4060";
		List<String> list = this.getStatInfo.listCsvKeysInRange(csvNumber, csvNumberAfter);
		Map<String, Map<String, List<BookDataEntity>>> entities =
                getStatInfo.getStatMapForSingleKey(list.get(0));

		this.conditionResultDataStat.calcStat(entities);
	}

}
