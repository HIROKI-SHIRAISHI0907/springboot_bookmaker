package dev.application.analyze.bm_m023_bm_m026_bm_m027_bm_m030;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.application.analyze.bm_m026.EachTeamScoreBasedFeatureStat;
import dev.common.entity.BookDataEntity;
import dev.common.getinfo.GetStatInfo;

/**
 * BM_M023統計分析ロジックテスト
 * @author shiraishitoshio
 *
 */
@SpringBootTest
@ActiveProfiles("test")
class EachScoreBasedFeatureStatTest {

	@Autowired
	private GetStatInfo getStatInfo;

	@Autowired
	private EachTeamScoreBasedFeatureStat eachTeamScoreBasedFeatureStat;

	/**
	 * メモリ効率試験
	 */
	@Test
	void test_calcStat_shouldUpdateCorrectly() throws Exception {
		// Act
		String csvNumber = "3999";
		List<String> list = this.getStatInfo.listCsvKeysInRange(csvNumber, null);
		Map<String, Map<String, List<BookDataEntity>>> entities = getStatInfo.getStatMapForSingleKey(list.get(0));

		this.eachTeamScoreBasedFeatureStat.calcStat(entities);
	}

	/**
	 * XX% (XX/XX)の形式を持つデータのマップ
	 */
	@Test
	void test_calcStat_shouldInitData() throws Exception {
		String csvNumber = "174";
		String csvBackNumber = "175";
		List<String> list = this.getStatInfo.listCsvKeysInRange(csvNumber, csvBackNumber);
		Map<String, Map<String, List<BookDataEntity>>> entities = getStatInfo.getStatMapForSingleKey(list.get(0));

		this.eachTeamScoreBasedFeatureStat.calcStat(entities);
	}

	/**
	 * XX% (XX/XX)の形式を持つデータのマップかつ同一国,リーグデータ
	 */
	@Test
	void test_calcStat_shouldInitUpdateData1() throws Exception {
		// ビジャレアルvsバレンシア
		String csvNumber = "174";
		String csvBackNumber = "175";
		List<String> list = this.getStatInfo.listCsvKeysInRange(csvNumber, csvBackNumber);
		Map<String, Map<String, List<BookDataEntity>>> entities = getStatInfo.getStatMapForSingleKey(list.get(0));

		this.eachTeamScoreBasedFeatureStat.calcStat(entities);
	}

	/**
	 * XX% (XX/XX)の形式を持つデータのマップかつ同一国,リーグデータ
	 */
	@Test
	void test_calcStat_shouldInitUpdateData2() throws Exception {
		// レアル・マドリードvsバレンシア
		String csvNumber = "477";
		String csvBackNumber = "478";
		List<String> list = this.getStatInfo.listCsvKeysInRange(csvNumber, csvBackNumber);
		Map<String, Map<String, List<BookDataEntity>>> entities = getStatInfo.getStatMapForSingleKey(list.get(0));

		this.eachTeamScoreBasedFeatureStat.calcStat(entities);
	}

	/**
	 * XX% (XX/XX)の形式を持つデータのマップかつ同一国,リーグデータ
	 * @throws Exception
	 */
	@Test
	void test_calcStat_shouldInitUpdateData3() throws Exception {
		// バレンシアvsアトレティコ・マドリード
		String csvNumber = "596";
		String csvBackNumber = "597";
		List<String> list = this.getStatInfo.listCsvKeysInRange(csvNumber, csvBackNumber);
		Map<String, Map<String, List<BookDataEntity>>> entities = getStatInfo.getStatMapForSingleKey(list.get(0));

		this.eachTeamScoreBasedFeatureStat.calcStat(entities);
	}

	/**
	 * XX% (XX/XX)の形式を持つデータのマップかつ同一国,リーグデータ
	 */
	@Test
	void test_calcStat_shouldInitUpdateData4() throws Exception {
		// バレンシアvsアトレティコ・マドリード
		String csvNumber = "0";
		String csvBackNumber = "450";
		List<String> list = this.getStatInfo.listCsvKeysInRange(csvNumber, csvBackNumber);
		Map<String, Map<String, List<BookDataEntity>>> entities = getStatInfo.getStatMapForSingleKey(list.get(0));

		this.eachTeamScoreBasedFeatureStat.calcStat(entities);
	}

}
