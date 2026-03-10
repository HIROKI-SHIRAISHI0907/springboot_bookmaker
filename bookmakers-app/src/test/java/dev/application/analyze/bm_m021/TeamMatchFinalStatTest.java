package dev.application.analyze.bm_m021;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.common.entity.BookDataEntity;
import dev.common.getinfo.GetStatInfo;

/**
 * BM_M021統計分析ロジックテスト
 * @author shiraishitoshio
 *
 */
@SpringBootTest
@ActiveProfiles("test")
public class TeamMatchFinalStatTest {

	@Autowired
	private GetStatInfo getStatInfo;

	@Autowired
	private TeamMatchFinalStat teamMatchFinalStat;

	/**
	 * 処理速度実験
	 */
	@Test
	void test_calcStat_shouldCorrectly_memory() {
		// Act
		String csvNumber = "0";
		String csvNumberAfter = "10";
		List<String> list = this.getStatInfo.listCsvKeysInRange(csvNumber, csvNumberAfter);
		Map<String, Map<String, List<BookDataEntity>>> entities =
                getStatInfo.getStatMapForSingleKey(list.get(0));

		this.teamMatchFinalStat.calcStat(entities);
	}

}
