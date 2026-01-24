package dev.application.analyze.bm_m019_bm_m020;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.common.entity.BookDataEntity;
import dev.common.getinfo.GetStatInfo;

/**
 * BM_M019_BM_M20統計分析ロジックテスト
 * @author shiraishitoshio
 *
 */
@SpringBootTest
@ActiveProfiles("test")
public class MatchClassificationResultStatTest {

	@Autowired
	private GetStatInfo getStatInfo;

	@Autowired
	private MatchClassificationResultStat matchClassificationResultStat;

	/**
	 * 処理速度実験
	 */
	@Test
	void test_calcStat_shouldCorrectly_memory() {
		// Act
		String csvNumber = "0";
		String csvNumberAfter = "1";
		Map<String, Map<String, List<BookDataEntity>>> entities = this.getStatInfo.getData(csvNumber, csvNumberAfter);

		this.matchClassificationResultStat.calcStat(entities);
	}

}
