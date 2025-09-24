package dev.application.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.bm_m002.ConditionResultDataEntity;
import dev.application.analyze.bm_m003.TeamMonthlyScoreSummaryEntity;
import dev.application.analyze.bm_m004.TeamTimeSegmentShootingStatsEntity;
import dev.application.analyze.bm_m005.NoGoalMatchStatisticsEntity;
import dev.application.analyze.bm_m006.CountryLeagueSummaryEntity;
import dev.application.analyze.bm_m007_bm_m016.TimeRangeFeatureAllLeagueEntity;
import dev.application.analyze.bm_m007_bm_m016.TimeRangeFeatureEntity;
import dev.application.analyze.bm_m007_bm_m016.TimeRangeFeatureScoredEntity;
import dev.application.analyze.bm_m017_bm_m018.LeagueScoreTimeBandStatsEntity;
import dev.application.analyze.bm_m017_bm_m018.LeagueScoreTimeBandStatsSplitScoreEntity;
import dev.application.analyze.bm_m019_bm_m020.MatchClassificationResultCountEntity;
import dev.application.analyze.bm_m019_bm_m020.MatchClassificationResultEntity;
import dev.application.analyze.bm_m021.TeamMatchFinalStatsEntity;
import dev.application.analyze.bm_m023.ScoreBasedFeatureStatsEntity;
import dev.application.analyze.bm_m024.CalcCorrelationEntity;
import dev.application.analyze.bm_m026.EachTeamScoreBasedFeatureEntity;
import dev.application.domain.repository.BookDataRepository;
import dev.application.domain.repository.CalcCorrelationRepository;
import dev.application.domain.repository.ConditionResultDataRepository;
import dev.application.domain.repository.CountryLeagueSummaryRepository;
import dev.application.domain.repository.EachTeamScoreBasedFeatureStatsRepository;
import dev.application.domain.repository.FutureRepository;
import dev.application.domain.repository.LeagueScoreTimeBandStatsRepository;
import dev.application.domain.repository.LeagueScoreTimeBandStatsSplitScoreRepository;
import dev.application.domain.repository.MatchClassificationResultCountRepository;
import dev.application.domain.repository.MatchClassificationResultRepository;
import dev.application.domain.repository.NoGoalMatchStatsRepository;
import dev.application.domain.repository.ScoreBasedFeatureStatsRepository;
import dev.application.domain.repository.TeamMatchFinalStatsRepository;
import dev.application.domain.repository.TeamMonthlyScoreSummaryRepository;
import dev.application.domain.repository.TeamTimeSegmentShootingStatsRepository;
import dev.application.domain.repository.TimeRangeFeatureAllLeagueRepository;
import dev.application.domain.repository.TimeRangeFeatureRepository;
import dev.application.domain.repository.TimeRangeFeatureScoredRepository;
import dev.application.domain.repository.TimeRangeFeatureUpdateRepository;
import dev.common.entity.DataEntity;
import dev.common.entity.FutureEntity;

@SpringBootTest
@Transactional
class RepositoryTest {

	/** BM_M001 */
	@Autowired
	private BookDataRepository bookDataRepository;

	/** BM_M002 */
	@Autowired
	private ConditionResultDataRepository conditionResultDataRepository;

	/** BM_M003 */
	@Autowired
	private TeamMonthlyScoreSummaryRepository teamMonthlyScoreSummaryRepository;

	/** BM_M004 */
	@Autowired
	private TeamTimeSegmentShootingStatsRepository teamTimeSegmentShootingStatsRepository;

	/** BM_M005 */
	@Autowired
	private NoGoalMatchStatsRepository noGoalMatchStatisticsRepository;

	/** BM_M006 */
	@Autowired
	private CountryLeagueSummaryRepository countryLeagueSummaryRepository;

	/** BM_M007_BM_M016 */
	@Autowired
	private TimeRangeFeatureAllLeagueRepository timeRangeFeatureAllLeagueRepository;

	/** BM_M007_BM_M016 */
	@Autowired
	private TimeRangeFeatureRepository timeRangeFeatureRepository;

	/** BM_M007_BM_M016 */
	@Autowired
	private TimeRangeFeatureScoredRepository timeRangeFeatureScoredRepository;

	/** BM_M007_BM_M016 */
	@Autowired
	private TimeRangeFeatureUpdateRepository timeRamgeFeatureUpdateRepository;

	/** BM_M017_BM_M018 */
	@Autowired
	private LeagueScoreTimeBandStatsRepository leagueScoreTimeBandStatsRepository;

	/** BM_M017_BM_M018 */
	@Autowired
	private LeagueScoreTimeBandStatsSplitScoreRepository leagueScoreTimeBandStatsSplitScoreRepository;

	/** BM_M019_BM_M020 */
	@Autowired
	private MatchClassificationResultRepository matchClassificationResultRepository;

	/** BM_M019_BM_M020 */
	@Autowired
	private MatchClassificationResultCountRepository matchClassificationResultCountRepository;

	/** BM_M021 */
	@Autowired
	private TeamMatchFinalStatsRepository teamMatchFinalStatsRepository;

	/** BM_M023 */
	@Autowired
	private ScoreBasedFeatureStatsRepository scoreBasedFeatureStatsRepository;

	/** BM_M024 */
	@Autowired
	private CalcCorrelationRepository calcCorrelationRepository;

	/** BM_M026 */
	@Autowired
	private EachTeamScoreBasedFeatureStatsRepository eachTeamScoreBasedFeatureStatsRepository;

	/** BM_M022 */
	@Autowired
	private FutureRepository futureRepository;

	@Test
	void test1() {
		DataEntity entity = new DataEntity();
		entity.setSeq("1");
		entity.setDataCategory("Japan");

		int saved = bookDataRepository.insert(entity);
		assertEquals(1, saved);

		int cnt = bookDataRepository.findDataCount(entity);
		assertEquals(1, cnt);
	}

	@Test
	void test2() {
		ConditionResultDataEntity entity = new ConditionResultDataEntity();
		entity.setMailTargetCount("1");
		entity.setMailAnonymousTargetCount("3");
		entity.setExMailTargetToNoResultCount("4");
		entity.setGoalDelete("5");
		entity.setHash("AAA");

		int saved = conditionResultDataRepository.insert(entity);
		assertEquals(1, saved);

		List<ConditionResultDataEntity> list = conditionResultDataRepository.findByHash("AAA");
		assertFalse(list.isEmpty());
	}

	@Test
	void test3() {
		TeamMonthlyScoreSummaryEntity entity = new TeamMonthlyScoreSummaryEntity();
		entity.setCountry("Japan");
		entity.setLeague("J1");

		int saved = teamMonthlyScoreSummaryRepository.insertTeamMonthlyScore(entity);
		assertEquals(1, saved);

		List<TeamMonthlyScoreSummaryEntity> list = teamMonthlyScoreSummaryRepository.findByCount(entity);
		assertFalse(list.isEmpty());
	}

	@Test
	void test4() {
		TeamTimeSegmentShootingStatsEntity entity = new TeamTimeSegmentShootingStatsEntity();

		int saved = teamTimeSegmentShootingStatsRepository.insert(entity);
		assertEquals(1, saved);
	}

	@Test
	void test5() {
		NoGoalMatchStatisticsEntity entity = new NoGoalMatchStatisticsEntity();

		int saved = noGoalMatchStatisticsRepository.insert(entity);
		assertEquals(1, saved);
	}

	@Test
	void test6() {
		String country = "";
		String league = "";
		CountryLeagueSummaryEntity entity = new CountryLeagueSummaryEntity();
		entity.setCountry("Japan");
		entity.setLeague("J1");

		int saved = countryLeagueSummaryRepository.insert(entity);
		assertEquals(1, saved);

		List<CountryLeagueSummaryEntity> list = countryLeagueSummaryRepository.findByCountryLeague(
				country, league);
		assertFalse(list.isEmpty());

		int savedUpd = countryLeagueSummaryRepository.update(entity);
		assertEquals(1, savedUpd);
	}

	@Test
	void test7() {
		TimeRangeFeatureAllLeagueEntity entity = new TimeRangeFeatureAllLeagueEntity();
		entity.setTimeRange("20〜30");
		entity.setFeature("J1");
		entity.setThresHold("10%");
		entity.setTableName("Japan");

		int saved = timeRangeFeatureAllLeagueRepository.insert(entity);
		assertEquals(1, saved);

		List<TimeRangeFeatureAllLeagueEntity> list = timeRangeFeatureAllLeagueRepository.findData(
				"20〜30", "特徴量", "10%", "");
		assertFalse(list.isEmpty());
	}

	@Test
	void test8() {
		TimeRangeFeatureEntity entity = new TimeRangeFeatureEntity();

		int saved = timeRangeFeatureRepository.insert(entity);
		assertEquals(1, saved);
	}

	@Test
	void test9() {
		TimeRangeFeatureScoredEntity entity = new TimeRangeFeatureScoredEntity();

		int saved = timeRangeFeatureScoredRepository.insert(entity);
		assertEquals(1, saved);

		List<TimeRangeFeatureScoredEntity> list = timeRangeFeatureScoredRepository.findData(
				"日本", "J1リーグ",
				"20〜30", "特徴量", "10%", "");
		assertFalse(list.isEmpty());
	}

	@Test
	void test10() {
		int saved = timeRamgeFeatureUpdateRepository.update(
				"1", "25%", "30%", "");
		assertEquals(1, saved);
	}

	@Test
	void test11() {
		LeagueScoreTimeBandStatsEntity entity = new LeagueScoreTimeBandStatsEntity();

		int saved = leagueScoreTimeBandStatsRepository.insert(entity);
		assertEquals(1, saved);

		List<LeagueScoreTimeBandStatsEntity> list = leagueScoreTimeBandStatsRepository.findData(
				"日本", "J1リーグ", "2", "20〜30");
		assertFalse(list.isEmpty());
	}

	@Test
	void test12() {
		LeagueScoreTimeBandStatsEntity entity = new LeagueScoreTimeBandStatsEntity();

		int saved = leagueScoreTimeBandStatsRepository.insert(entity);
		assertEquals(1, saved);

		List<LeagueScoreTimeBandStatsEntity> list = leagueScoreTimeBandStatsRepository.findData(
				"日本", "J1リーグ", "2", "20〜30");
		assertFalse(list.isEmpty());
	}

	@Test
	void test13() {
		LeagueScoreTimeBandStatsSplitScoreEntity entity = new LeagueScoreTimeBandStatsSplitScoreEntity();

		int saved = leagueScoreTimeBandStatsSplitScoreRepository.insert(entity);
		assertEquals(1, saved);

		List<LeagueScoreTimeBandStatsSplitScoreEntity> list = leagueScoreTimeBandStatsSplitScoreRepository.
				findData("日本", "J1リーグ", "2", "2", "20〜30", "40〜50");
		assertFalse(list.isEmpty());
	}

	@Test
	void test14() {
		MatchClassificationResultEntity entity = new MatchClassificationResultEntity();

		int saved = matchClassificationResultRepository.insert(entity);
		assertEquals(1, saved);
	}

	@Test
	void test15() {
		String country = "";
		String league = "";
		MatchClassificationResultCountEntity entity = new MatchClassificationResultCountEntity();
		entity.setCountry("Japan");
		entity.setLeague("J1");

		int saved = matchClassificationResultCountRepository.insert(entity);
		assertEquals(1, saved);

		List<MatchClassificationResultCountEntity> list = matchClassificationResultCountRepository.
				findData(country, league, "1");
		assertFalse(list.isEmpty());

		int savedUpd = matchClassificationResultCountRepository.update("1", "4");
		assertEquals(1, savedUpd);
	}

	@Test
	void test16() {
		TeamMatchFinalStatsEntity entity = new TeamMatchFinalStatsEntity();
		entity.setTeamName("川崎フロンターレ");
		entity.setVersusTeamName("名古屋グランパス");

		int saved = teamMatchFinalStatsRepository.insert(entity);
		assertEquals(1, saved);
	}

	@Test
	void test17() {
		String country = "川崎フロンターレ";
		String league = "名古屋グランパス";
		ScoreBasedFeatureStatsEntity entity = new ScoreBasedFeatureStatsEntity();
		entity.setCountry("Japan");
		entity.setLeague("J1");

		int saved = scoreBasedFeatureStatsRepository.insert(entity);
		assertEquals(1, saved);

		List<ScoreBasedFeatureStatsEntity> list = scoreBasedFeatureStatsRepository.
				findStatData("1-0", "得点あり", country, league);
		assertFalse(list.isEmpty());

		int savedUpd = matchClassificationResultCountRepository.update("1", "4");
		assertEquals(1, savedUpd);
	}

	@Test
	void test18() {
		String home = "ジェフユナイテッド千葉";
		String away = "大宮アルディージャ";
		CalcCorrelationEntity entity = new CalcCorrelationEntity();
		entity.setCountry("Japan");
		entity.setLeague("J1");

		int saved = calcCorrelationRepository.insert(entity);
		assertEquals(1, saved);

		List<CalcCorrelationEntity> list = calcCorrelationRepository.
				findStatData("日本", "J2リーグ", home, away, "1-0", "pearson");
		assertFalse(list.isEmpty());

		int savedUpd = calcCorrelationRepository.updateStatValues(entity);
		assertEquals(1, savedUpd);
	}

	@Test
	void test19() {
		String team = "栃木SC";
		EachTeamScoreBasedFeatureEntity entity = new EachTeamScoreBasedFeatureEntity();
		entity.setCountry("Japan");
		entity.setLeague("J1");

		int saved = eachTeamScoreBasedFeatureStatsRepository.insert(entity);
		assertEquals(1, saved);

		List<EachTeamScoreBasedFeatureEntity> list = eachTeamScoreBasedFeatureStatsRepository.
				findStatData("2-0", "得点あり",
						"日本", "J3リーグ", team);
		assertFalse(list.isEmpty());

		int savedUpd = eachTeamScoreBasedFeatureStatsRepository.updateStatValues(entity);
		assertEquals(1, savedUpd);
	}

	@Test
	void test20() {
		String home = "高知ユナイテッド";
		String away = "鹿児島ユナイテッドFC";
		FutureEntity entity = new FutureEntity();
		entity.setHomeTeamName(home);
		entity.setAwayTeamName(away);

		int saved = futureRepository.insert(entity);
		assertEquals(1, saved);

		int savedUpd = futureRepository.findDataCount(entity);
		assertEquals(1, savedUpd);
	}

}
