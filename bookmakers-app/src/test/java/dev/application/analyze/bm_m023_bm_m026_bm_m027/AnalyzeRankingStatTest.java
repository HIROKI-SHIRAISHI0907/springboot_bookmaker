package dev.application.analyze.bm_m023_bm_m026_bm_m027;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.application.analyze.bm_m027.AnalyzeRankingStat;
import dev.common.entity.BookDataEntity;
import dev.common.getstatinfo.GetStatInfo;

/**
 * BM_M027統計分析ロジックテスト
 * @author shiraishitoshio
 *
 */
@SpringBootTest
@ActiveProfiles("test")
public class AnalyzeRankingStatTest {

	@Autowired
	private GetStatInfo getStatInfo;

	@Autowired
	private AnalyzeRankingStat analyzeRankingStat;

	/**
	 * 処理速度実験
	 */
	@Test
	void test_calcStat_shouldCorrectly_memory() {
		// Act
		String csvNumber = "915";
		String csvNumberAfter = "916";
		Map<String, Map<String, List<BookDataEntity>>> entities = this.getStatInfo.getData(csvNumber, csvNumberAfter);

		// Act
		this.analyzeRankingStat.calcStat(entities);
	}
}
