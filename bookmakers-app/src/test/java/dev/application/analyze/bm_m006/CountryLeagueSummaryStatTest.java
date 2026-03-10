package dev.application.analyze.bm_m006;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.common.entity.BookDataEntity;
import dev.common.getinfo.GetStatInfo;

/**
 * BM_M006統計分析ロジックテスト
 * @author shiraishitoshio
 *
 */
@SpringBootTest
@ActiveProfiles("test")
public class CountryLeagueSummaryStatTest {

	@Autowired
	private GetStatInfo getStatInfo;

	@Autowired
	private CountryLeagueSummaryStat countryLeagueSummaryStat;

	/**
	 * 処理速度実験
	 */
	@Test
	void test_calcStat_shouldUpdateCorrectly_memory() {
		// Act
		String csvNumber = "1";
		String csvNumberAfter = "4060";
		List<String> list = this.getStatInfo.listCsvKeysInRange(csvNumber, csvNumberAfter);
		Map<String, Map<String, List<BookDataEntity>>> entities =
                getStatInfo.getStatMapForSingleKey(list.get(0));

		this.countryLeagueSummaryStat.calcStat(entities);
	}

	@Test
	void test_calcStat_shouldUpdateCorrectly() {
		// Act
		String csvNumber = "4060";
		String csvNumberAfter = "4280";
		List<String> list = this.getStatInfo.listCsvKeysInRange(csvNumber, csvNumberAfter);
		Map<String, Map<String, List<BookDataEntity>>> entities =
                getStatInfo.getStatMapForSingleKey(list.get(0));

		this.countryLeagueSummaryStat.calcStat(entities);
	}

}
