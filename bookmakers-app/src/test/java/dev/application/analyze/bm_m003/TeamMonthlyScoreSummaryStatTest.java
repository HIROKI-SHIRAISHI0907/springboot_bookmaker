package dev.application.analyze.bm_m003;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.common.entity.BookDataEntity;
import dev.common.getinfo.GetStatInfo;

/**
 * BM_M003統計分析ロジックテスト
 * @author shiraishitoshio
 *
 */
@SpringBootTest
@ActiveProfiles("test")
public class TeamMonthlyScoreSummaryStatTest {

	@Autowired
	private GetStatInfo getStatInfo;

	@Autowired
	private TeamMonthlyScoreSummaryStat teamMonthlyScoreSummaryStat;

	@Test
	void test_calcStat_shouldCorrectly() {
		// Act
		String csvNumber = "1";
		String csvNumberAfter = "5";
		Map<String, Map<String, List<BookDataEntity>>> entities = this.getStatInfo.getData(csvNumber, csvNumberAfter);

		this.teamMonthlyScoreSummaryStat.calcStat(entities);
	}

}
