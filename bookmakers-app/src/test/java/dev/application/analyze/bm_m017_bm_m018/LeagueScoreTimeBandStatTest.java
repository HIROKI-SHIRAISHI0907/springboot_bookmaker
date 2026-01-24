package dev.application.analyze.bm_m017_bm_m018;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.common.entity.BookDataEntity;
import dev.common.getinfo.GetStatInfo;

/**
 * BM_M017_BM_M018統計分析ロジックテスト
 * @author shiraishitoshio
 *
 */
@SpringBootTest
@ActiveProfiles("test")
public class LeagueScoreTimeBandStatTest {

	@Autowired
	private GetStatInfo getStatInfo;

	@Autowired
	private LeagueScoreTimeBandStat leagueScoreTimeBandStat;

	/**
	 * 処理速度実験
	 */
	@Test
	void test_calcStat_shouldCorrectly_memory() {
		// Act
		String csvNumber = "5";
		String csvNumberAfter = "6";
		Map<String, Map<String, List<BookDataEntity>>> entities = this.getStatInfo.getData(csvNumber, csvNumberAfter);

		this.leagueScoreTimeBandStat.calcStat(entities);
	}

	/**
	 * 処理速度実験
	 */
	@Test
	void test_calcStat_shouldCorrectly_memory2() {
		// Act
		String csvNumber = "90";
		String csvNumberAfter = "91";
		Map<String, Map<String, List<BookDataEntity>>> entities = this.getStatInfo.getData(csvNumber, csvNumberAfter);

		this.leagueScoreTimeBandStat.calcStat(entities);
	}

	/**
	 * 処理速度実験
	 */
	@Test
	void test_calcStat_shouldCorrectly_memory3() {
		// Act
		String csvNumber = "0";
		String csvNumberAfter = "100";
		Map<String, Map<String, List<BookDataEntity>>> entities = this.getStatInfo.getData(csvNumber, csvNumberAfter);

		this.leagueScoreTimeBandStat.calcStat(entities);
	}

}
