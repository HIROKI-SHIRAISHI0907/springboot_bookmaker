package dev.application.analyze.bm_m031;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.application.domain.repository.SurfaceOverviewRepository;
import dev.common.entity.BookDataEntity;
import dev.common.getstatinfo.GetStatInfo;

/**
 * BM_M025統計分析ロジックテスト
 * @author shiraishitoshio
 *
 */
@SpringBootTest
@ActiveProfiles("test")
public class SurfaceOverviewStatTest {

	@Autowired
	private GetStatInfo getStatInfo;

	@Autowired
	private SurfaceOverviewStat surfaceOverviewStat;

	@Autowired
	private SurfaceOverviewRepository repository;

	/**
	 * 処理速度実験
	 */
	@Test
	void test_calcStat_shouldCorrectly_memory() {
		// Act
		String csvNumber = "0";
		String csvNumberAfter = "5";
		Map<String, Map<String, List<BookDataEntity>>> entities = this.getStatInfo.getData(csvNumber, csvNumberAfter);

		this.surfaceOverviewStat.calcStat(entities);
	}

	/**
	 * 単一試合(ホーム0-1で敗戦)の最小データで entity への反映を検証する
	 * - 勝敗/試合数/勝ち点
	 * - ホーム/アウェー別カウンタ
	 * - 月の算出(先頭ゼロ除去)
	 * - 序盤カウント・表示
	 * - クリーンシート、無敗継続、得点不能カウント
	 */
	@Test
	void test_basedMain_setsExpectedFields_simpleAwayWin() throws Exception {
		// Act
		String csvNumber = "1";
		String csvNumberAfter = "2";
		Map<String, Map<String, List<BookDataEntity>>> entities = this.getStatInfo.getData(csvNumber, csvNumberAfter);
		this.surfaceOverviewStat.calcStat(entities);

		csvNumber = "25";
		csvNumberAfter = "26";
		entities = this.getStatInfo.getData(csvNumber, csvNumberAfter);
		this.surfaceOverviewStat.calcStat(entities);

		String country = "アルゼンチン";
		String league = "トルネオ・ベターノ";
		String game_year = "2025";
		String game_month = "3";
		String team = "リーベル・プレート";
		SurfaceOverviewEntity homeE = this.repository.
				select(country, league, game_year, game_month, team).get(0);

		String countryA = "エクアドル";
		String leagueA = "リーガ・プロ";
		String game_yearA = "2025";
		String game_monthA = "8";
		String teamA = "ムシュク・ルナ";
		SurfaceOverviewEntity awayE = this.repository.
				select(countryA, leagueA, game_yearA, game_monthA, teamA).get(0);

		// 共通メタ
		assertEquals(country, homeE.getCountry());
		assertEquals(league, homeE.getLeague());
		assertEquals("2025", homeE.getGameYear());
		assertEquals("3", homeE.getGameMonth()); // "03" -> "3" に変換されているか

		assertEquals(countryA, awayE.getCountry());
		assertEquals(leagueA, awayE.getLeague());
		assertEquals("2025", awayE.getGameYear());
		assertEquals("8", awayE.getGameMonth());

		// ホームは 2-2 で引き分け
		assertEquals("0", homeE.getWin());
		assertEquals("0", homeE.getLose());
		assertEquals("1", homeE.getDraw());
		assertEquals("1", homeE.getGames());
		assertEquals("1", homeE.getWinningPoints());

		// アウェーは1-2で敗北
		assertEquals("0", awayE.getWin());
		assertEquals("1", awayE.getLose());
		assertEquals("0", awayE.getDraw());
		assertEquals("1", awayE.getGames());
		assertEquals("0", awayE.getWinningPoints());

		// venue 別
		// ホームは 2-2 で引き分け
		assertEquals("0", homeE.getHomeWinCount());
		assertEquals("0", homeE.getHomeLoseCount());
		assertEquals("0", homeE.getAwayWinCount());
		assertEquals("0", homeE.getAwayLoseCount());

		// アウェーで1-2で敗北
		assertEquals("0", awayE.getHomeWinCount());
		assertEquals("0", awayE.getHomeLoseCount());
		assertEquals("0", awayE.getAwayWinCount());
		assertEquals("1", awayE.getAwayLoseCount());

		// クリーンシート / 得点不能 / 無敗
		// ホームチーム 2-2 で引き分け
		assertEquals("0", homeE.getHomeCleanSheet());
		assertEquals("0", homeE.getFailToScoreGameCount()); // 0得点
		assertEquals("1", homeE.getUnbeatenStreakCount()); // 敗戦でリセット
		assertEquals("無敗継続中", homeE.getUnbeatenStreakDisp());
		// アウェーチーム 1-2で敗北
		assertEquals("0", awayE.getAwayCleanSheet()); // 相手(ホーム)が0得点
		assertEquals("0", awayE.getFailToScoreGameCount());
		assertEquals("0", awayE.getUnbeatenStreakCount()); // 敗戦したので0のまま
		assertNull(awayE.getUnbeatenStreakDisp());

		// 直近表示（このケースでは閾値未満なので null）
		assertNull(homeE.getConsecutiveWinDisp());
		assertNull(homeE.getConsecutiveLoseDisp());
		assertNull(awayE.getConsecutiveWinDisp());
		assertNull(awayE.getConsecutiveLoseDisp());

		// 先制・逆転系（ホームチーム 先制されたが引き分けに持ち込んだ, アウェーチーム 先制された、逆転は発生せず）
		assertEquals("0", homeE.getHomeFirstGoalCount());
		assertEquals("0", awayE.getAwayFirstGoalCount());
		assertEquals("0", homeE.getHomeWinBehindCount()); // 終了済が2-2なので逆転勝利ではない
		assertEquals("0", homeE.getHomeLoseBehindCount());
		assertEquals("0", awayE.getAwayWinBehindCount());
		assertEquals("0", awayE.getAwayLoseBehindCount());

		// 序盤/中盤/終盤カウントと表示
		assertEquals("0", homeE.getFirstWeekGameWinCount());
		assertEquals("0", homeE.getFirstWeekGameLostCount());
		assertEquals("0", homeE.getMidWeekGameWinCount());
		assertEquals("0", homeE.getMidWeekGameLostCount());
		assertEquals("0", homeE.getLastWeekGameWinCount());
		assertEquals("0", homeE.getLastWeekGameLostCount());
		assertNull(homeE.getFirstWeekGameWinDisp()); // 引き分けなので表示なし
		assertNull(homeE.getMidWeekGameWinDisp()); // 引き分けなので表示なし
		assertNull(homeE.getLastWeekGameWinDisp()); // 引き分けなので表示なし

		assertEquals("0", awayE.getFirstWeekGameWinCount());
		assertEquals("0", awayE.getFirstWeekGameLostCount());
		assertEquals("0", awayE.getMidWeekGameWinCount());
		assertEquals("0", awayE.getMidWeekGameLostCount());
		assertEquals("0", awayE.getLastWeekGameWinCount());
		assertEquals("1", awayE.getLastWeekGameLostCount());
		assertNull(awayE.getFirstWeekGameWinDisp()); // 敗戦したので表示なし
		assertNull(awayE.getMidWeekGameWinDisp()); // 敗戦したので表示なし
		assertNull(awayE.getLastWeekGameWinDisp()); // 敗戦したので表示なし

		// 逆境表示（いずれも閾値未達なので null）
		assertNull(homeE.getHomeAdversityDisp());
		assertNull(homeE.getAwayAdversityDisp());
		assertNull(awayE.getHomeAdversityDisp());
		assertNull(awayE.getAwayAdversityDisp());

		// NOT NULL 対策の 0 埋めが効いているか（代表的にいくつか）
		assertEquals("0", homeE.getAwayWinBehindOtherCount());
		assertEquals("0", awayE.getHomeWinBehindOtherCount());
	}

}
