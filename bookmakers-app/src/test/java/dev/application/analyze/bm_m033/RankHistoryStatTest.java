package dev.application.analyze.bm_m033;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.BookDataEntity;
import dev.common.getinfo.GetStatInfo;

/**
 * BM_M033統計分析ロジックテスト
 * @author shiraishitoshio
 *
 */
@SpringBootTest
@ActiveProfiles("test")
class RankHistoryStatTest {

	@Autowired
	private GetStatInfo getStatInfo;

	@Autowired
	private RankHistoryStat rankHistoryStat;

	// -----------------------------
	// rankTeams のテスト
	// -----------------------------
	@Test
	void rankTeams_basicOrdering() {
		// given
		TeamPoints t1 = new TeamPoints();
		t1.setTeam("TeamA");
		t1.setPoints(30);
		t1.setGf(20);
		t1.setGa(10); // +10

		TeamPoints t2 = new TeamPoints();
		t2.setTeam("TeamB");
		t2.setPoints(29);
		t2.setGf(25);
		t2.setGa(20); // +5

		TeamPoints t3 = new TeamPoints();
		t3.setTeam("TeamC");
		t3.setPoints(10);
		t3.setGf(10);
		t3.setGa(5); // +5

		List<TeamPoints> list = Arrays.asList(t3, t2, t1); // わざとバラバラ順

		// when
		// rankTeams は private なので、テストしやすいように
		// RankHistoryStat と同一パッケージにこのテストクラスを置き、
		// rankTeams を package-private に変更しておくとベストです。
		List<RankedTeamPoints> ranked = invokeRankTeams(list);

		// then
		assertThat(ranked).hasSize(3);
		assertThat(ranked.get(0).getTeam()).isEqualTo("TeamA");
		assertThat(ranked.get(0).getRank()).isEqualTo(1);
		assertThat(ranked.get(1).getTeam()).isEqualTo("TeamB");
		assertThat(ranked.get(1).getRank()).isEqualTo(2);
		assertThat(ranked.get(2).getTeam()).isEqualTo("TeamC");
		assertThat(ranked.get(2).getRank()).isEqualTo(3);
	}

	@Test
	void rankTeams_samePointsGoalDiffAndGf_getSameRank() {
		// given
		TeamPoints t1 = new TeamPoints();
		t1.setTeam("TeamA");
		t1.setPoints(30);
		t1.setGf(20);
		t1.setGa(10); // +10

		TeamPoints t2 = new TeamPoints();
		t2.setTeam("TeamB");
		t2.setPoints(30);
		t2.setGf(20);
		t2.setGa(10); // +10

		TeamPoints t3 = new TeamPoints();
		t3.setTeam("TeamC");
		t3.setPoints(25);
		t3.setGf(15);
		t3.setGa(10); // +5

		List<TeamPoints> list = Arrays.asList(t3, t2, t1);

		// when
		List<RankedTeamPoints> ranked = invokeRankTeams(list);

		// then
		assertThat(ranked).hasSize(3);
		// A, B は同順位
		assertThat(ranked.get(0).getRank()).isEqualTo(1);
		assertThat(ranked.get(1).getRank()).isEqualTo(1);
		// C は 3位
		assertThat(ranked.get(2).getRank()).isEqualTo(3);
	}

	@Test
	void rankTeams_handlesNullValuesAsZero() {
		// given
		TeamPoints t1 = new TeamPoints();
		t1.setTeam("TeamA");
		t1.setPoints(null);
		t1.setGf(null);
		t1.setGa(null);

		TeamPoints t2 = new TeamPoints();
		t2.setTeam("TeamB");
		t2.setPoints(1);
		t2.setGf(1);
		t2.setGa(0);

		List<TeamPoints> list = Arrays.asList(t1, t2);

		// when
		List<RankedTeamPoints> ranked = invokeRankTeams(list);

		// then
		assertThat(ranked).hasSize(2);
		assertThat(ranked.get(0).getTeam()).isEqualTo("TeamB");
		assertThat(ranked.get(0).getRank()).isEqualTo(1);
		assertThat(ranked.get(1).getTeam()).isEqualTo("TeamA");
		assertThat(ranked.get(1).getRank()).isEqualTo(2);
	}

	// rankTeams は private なので、ここではリフレクションで呼び出し例を示します。
	@SuppressWarnings("unchecked")
	private List<RankedTeamPoints> invokeRankTeams(List<TeamPoints> list) {
		try {
			var m = RankHistoryStat.class.getDeclaredMethod("rankTeams", List.class);
			m.setAccessible(true);
			return (List<RankedTeamPoints>) m.invoke(rankHistoryStat, list);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// -----------------------------
	// calcStat のテスト（代表ケース）
	// -----------------------------

	@Test
	void calcStat_whenOnlyHomeRankExists_computesAndStoresRanks() throws Exception {
		// given
		// 1試合分のデータ（homeRankあり / awayRankなし）
		String csvNumber = "0";
		String csvNumberAfter = "1";
		Map<String, Map<String, List<BookDataEntity>>> entities = this.getStatInfo.getData(csvNumber, csvNumberAfter);

		// when
		rankHistoryStat.calcStat(entities);
	}

	@Test
	void calcStat_whenOnlyHomeRankExists_computesAndStoresRanks_empty() throws Exception {
		// --- given 手動で BookDataEntity を作る（rank が入っていない版）---

		BookDataEntity e = new BookDataEntity();
		e.setGameTeamCategory("日本: J3 リーグ - ラウンド 31");
		e.setTime(BookMakersCommonConst.FIN);

		// ★ home_rank / away_rank を入れない（null または 空文字）
		e.setHomeRank(null); // ← または ""
		e.setAwayRank(null); // ← または ""

		e.setHomeTeamName("松本山雅FC");
		e.setAwayTeamName("SC相模原");
		e.setHomeScore("2");
		e.setAwayScore("1");

		// RankHistoryStat.calcStat() が要求する Map 構造にする
		Map<String, List<BookDataEntity>> leagueMap = new HashMap<>();
		leagueMap.put("日本-J3 リーグ", List.of(e));

		Map<String, Map<String, List<BookDataEntity>>> entities = new HashMap<>();
		entities.put("松本山雅FC-SC相模原", leagueMap);

		// --- when ---
		rankHistoryStat.calcStat(entities);
	}

	@Test
	void calcStat_whenOnlyHomeRankExists_computesAndStoresRanks_All() throws Exception {
		// Act
		String csvNumber = "0";
		String csvNumberAfter = null;
		Map<String, Map<String, List<BookDataEntity>>> entities = this.getStatInfo.getData(csvNumber, csvNumberAfter);
		// --- when ---
		rankHistoryStat.calcStat(entities);
	}

}
