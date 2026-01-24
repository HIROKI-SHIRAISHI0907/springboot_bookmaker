package dev.application.analyze.bm_m024;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.common.entity.BookDataEntity;
import dev.common.getinfo.GetStatInfo;

/**
 * BM_M024統計分析ロジックテスト
 * @author shiraishitoshio
 *
 */
@SpringBootTest
@ActiveProfiles("test")
public class CalcCorrelationStatTest {

	@Autowired
	private GetStatInfo getStatInfo;

	@Autowired
	private CalcCorrelationStat calcCorrelationStat;

	/**
	 * 処理速度実験
	 */
	@Test
	void test_calcStat_shouldCorrectly_memory() {
		// Act
		String csvNumber = "915";
		String csvNumberAfter = "916";
		Map<String, Map<String, List<BookDataEntity>>> entities = this.getStatInfo.getData(csvNumber, csvNumberAfter);

		this.calcCorrelationStat.calcStat(entities);
	}

}
