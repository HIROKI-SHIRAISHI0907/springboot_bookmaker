package dev.application.analyze.bm_m031;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.application.common.SurfaceOverviewTestData;
import dev.application.domain.repository.SurfaceOverviewRepository;
import dev.common.constant.BookMakersCommonConst;
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
		//		String csvNumber = "1";
		//		String csvNumberAfter = "2";
		//		Map<String, Map<String, List<BookDataEntity>>> entities = this.getStatInfo.getData(csvNumber, csvNumberAfter);
		//		this.surfaceOverviewStat.calcStat(entities);
		//
		//		csvNumber = "25";
		//		csvNumberAfter = "26";
		//		entities = this.getStatInfo.getData(csvNumber, csvNumberAfter);
		//		this.surfaceOverviewStat.calcStat(entities);

		String country = "アルゼンチン";
		String league = "トルネオ・ベターノ";
		String game_year = "2025";
		String game_month = "3";
		String team = "リーベル・プレート";
		SurfaceOverviewEntity homeE = this.repository.select(country, league, game_year, game_month, team).get(0);

		String countryA = "エクアドル";
		String leagueA = "リーガ・プロ";
		String game_yearA = "2025";
		String game_monthA = "8";
		String teamA = "ムシュク・ルナ";
		SurfaceOverviewEntity awayE = this.repository.select(countryA, leagueA, game_yearA, game_monthA, teamA).get(0);

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

		// winFlg/loseFlg は setTeamMainData で「今回の試合結果で増えたか」で判定
		// ホームは引き分け → どちらも false、アウェーは敗戦 → loseFlg=true
		assertFalse(homeE.isWinFlg());
		assertFalse(homeE.isLoseFlg());
		assertFalse(awayE.isWinFlg()); //途中データのため比較なし
		assertFalse(awayE.isLoseFlg()); //途中データのため比較なし

		// id / rank はこのロジックでは未設定想定
		assertEquals("1", homeE.getId());
		assertEquals("3", awayE.getId());
		assertNull(homeE.getRank());
		assertNull(awayE.getRank());

		// --- 得点サマリと前後半内訳の整合 ---
		// 合計（homeは2点, awayは1点）は既に検証済み。内訳はデータ依存なので整合性のみ検証。
		if (homeE.getHome1stHalfScore() != null && homeE.getHome2ndHalfScore() != null) {
			int h1 = Integer.parseInt(homeE.getHome1stHalfScore());
			int h2 = Integer.parseInt(homeE.getHome2ndHalfScore());
			int hs = Integer.parseInt(homeE.getHomeSumScore());
			assertEquals(hs, h1 + h2, "home 1st+2nd half must equal homeSumScore");
		}
		if (awayE.getAway1stHalfScore() != null && awayE.getAway2ndHalfScore() != null) {
			int a1 = Integer.parseInt(awayE.getAway1stHalfScore());
			int a2 = Integer.parseInt(awayE.getAway2ndHalfScore());
			int as = Integer.parseInt(awayE.getAwaySumScore());
			assertEquals(as, a1 + a2, "away 1st+2nd half must equal awaySumScore");
		}

		// 割合は "NN%" 文字列。0〜100%の範囲とフォーマットのみ緩く検証。
		if (homeE.getHome1stHalfScoreRatio() != null && homeE.getHome2ndHalfScoreRatio() != null) {
			assertTrue(homeE.getHome1stHalfScoreRatio().endsWith("%"));
			assertTrue(homeE.getHome2ndHalfScoreRatio().endsWith("%"));
			int r1 = Integer.parseInt(homeE.getHome1stHalfScoreRatio().replace("%", ""));
			int r2 = Integer.parseInt(homeE.getHome2ndHalfScoreRatio().replace("%", ""));
			assertTrue(0 <= r1 && r1 <= 100);
			assertTrue(0 <= r2 && r2 <= 100);
		}
		if (awayE.getAway1stHalfScoreRatio() != null && awayE.getAway2ndHalfScoreRatio() != null) {
			assertTrue(awayE.getAway1stHalfScoreRatio().endsWith("%"));
			assertTrue(awayE.getAway2ndHalfScoreRatio().endsWith("%"));
			int r1 = Integer.parseInt(awayE.getAway1stHalfScoreRatio().replace("%", ""));
			int r2 = Integer.parseInt(awayE.getAway2ndHalfScoreRatio().replace("%", ""));
			assertTrue(0 <= r1 && r1 <= 100);
			assertTrue(0 <= r2 && r2 <= 100);
		}

		// --- 連続得点（今回どちらも得点しているので 1、表示はしきい値未満で null） ---
		assertEquals("1", homeE.getConsecutiveScoreCount());
		assertNull(homeE.getConsecutiveScoreCountDisp());
		assertEquals("1", awayE.getConsecutiveScoreCount());
		assertNull(awayE.getConsecutiveScoreCountDisp());

		// --- 逆転内訳は今回のケースではすべて 0（逆転発生なし） ---
		assertEquals("0", homeE.getHomeWinBehind0vs1Count());
		assertEquals("0", homeE.getHomeLoseBehind1vs0Count());
		assertEquals("0", homeE.getHomeWinBehind0vs2Count());
		assertEquals("0", homeE.getHomeLoseBehind2vs0Count());
		assertEquals("0", homeE.getHomeWinBehindOtherCount());
		assertEquals("0", homeE.getHomeLoseBehindOtherCount());

		assertEquals("0", awayE.getAwayWinBehind1vs0Count());
		assertEquals("0", awayE.getAwayLoseBehind0vs1Count());
		assertEquals("0", awayE.getAwayWinBehind2vs0Count());
		assertEquals("0", awayE.getAwayLoseBehind0vs2Count());
		assertEquals("0", awayE.getAwayWinBehindOtherCount());
		assertEquals("0", awayE.getAwayLoseBehindOtherCount());

		// --- 昇降格/特殊表示 ---
		// 実装では「勝ち数が0なら初勝利モチベ」を付与するため、両チームとも非 null を期待。
		assertNotNull(homeE.getFirstWinDisp());
		assertNotNull(awayE.getFirstWinDisp());
		// 降格/昇格/負け込みは今回条件未充足で null
		assertNull(homeE.getPromoteDisp());
		assertNull(homeE.getDescendDisp());
		assertNull(homeE.getLoseStreakDisp());
		assertNull(awayE.getPromoteDisp());
		assertNull(awayE.getDescendDisp());
		assertNull(awayE.getLoseStreakDisp());
	}

	/**
	 * 連敗(横浜・F・マリノス)
	 */
	@Test
	void test_calcStat_shouldCorrectly_concective() {
		// Act
		Map<String, Map<String, List<BookDataEntity>>> entities = SurfaceOverviewTestData.builder()
				.addMatchSnapshots("日本", "J1 リーグ", "横浜・F・マリノス", "東京ヴェルディ",
						new int[] { 0, 1 }, new int[] { 0, 1 }, new int[] { 0, 3 },
						2, "2025-02-06 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN,
						"cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
		entities = SurfaceOverviewTestData.builder().addMatchSnapshots("日本", "J1 リーグ", "横浜・F・マリノス", "名古屋グランパス",
				new int[] { 1, 0 }, new int[] { 1, 1 }, new int[] { 1, 2 },
				3, "2025-02-13 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN, "cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
		entities = SurfaceOverviewTestData.builder().addMatchSnapshots("日本", "J1 リーグ", "浦和レッズ", "横浜・F・マリノス",
				new int[] { 0, 0 }, new int[] { 0, 0 }, new int[] { 4, 2 },
				4, "2025-02-20 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN, "cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
		entities = SurfaceOverviewTestData.builder().addMatchSnapshots("日本", "J1 リーグ", "ガンバ大阪", "横浜・F・マリノス",
				new int[] { 0, 0 }, new int[] { 1, 0 }, new int[] { 5, 1 },
				5, "2025-02-27 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN, "cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
	}

	/**
	 * 連敗(ザスパクサツ群馬)
	 */
	@Test
	void test_calcStat_shouldCorrectly_concective2() {
		// Act
		Map<String, Map<String, List<BookDataEntity>>> entities = SurfaceOverviewTestData.builder()
				.addMatchSnapshots("日本", "J3 リーグ", "栃木シティ", "ザスパクサツ群馬",
						new int[] { 2, 1 }, new int[] { 3, 1 }, new int[] { 5, 2 },
						2, "2025-02-06 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN,
						"cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
		entities = SurfaceOverviewTestData.builder().addMatchSnapshots("日本", "J3 リーグ", "ザスパクサツ群馬", "テゲバジャーロ宮崎",
				new int[] { 1, 0 }, new int[] { 2, 1 }, new int[] { 2, 3 },
				6, "2025-02-13 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN, "cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
		entities = SurfaceOverviewTestData.builder().addMatchSnapshots("日本", "J3 リーグ", "鹿児島ユナイテッドFC", "ザスパクサツ群馬",
				new int[] { 0, 0 }, new int[] { 0, 0 }, new int[] { 1, 0 },
				7, "2025-02-20 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN, "cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
		entities = SurfaceOverviewTestData.builder().addMatchSnapshots("日本", "J3 リーグ", "ザスパクサツ群馬", "SC相模原",
				new int[] { 0, 0 }, new int[] { 0, 0 }, new int[] { 0, 2 },
				8, "2025-02-27 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN, "cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
	}

	/**
	 * 連敗(ザスパクサツ群馬)
	 */
	@Test
	void test_calcStat_shouldCorrectly_concective3() {
		// Act
		Map<String, Map<String, List<BookDataEntity>>> entities = SurfaceOverviewTestData.builder()
				.addMatchSnapshots("日本", "J3 リーグ", "高知ユナイテッド", "ザスパクサツ群馬",
						new int[] { 0, 1 }, new int[] { 0, 1 }, new int[] { 2, 1 },
						2, "2025-03-06 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN,
						"cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
		entities = SurfaceOverviewTestData.builder().addMatchSnapshots("日本", "J3 リーグ", "ザスパクサツ群馬", "FC大阪",
				new int[] { 0, 0 }, new int[] { 0, 1 }, new int[] { 0, 4 },
				6, "2025-03-13 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN, "cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
	}

	/**
	 * 連敗(ザスパクサツ群馬)
	 */
	@Test
	void test_calcStat_shouldCorrectly_concective4() {
		// Act
		Map<String, Map<String, List<BookDataEntity>>> entities = SurfaceOverviewTestData.builder()
				.addMatchSnapshots("日本", "J3 リーグ", "ギラヴァンツ北九州", "ザスパクサツ群馬",
						new int[] { 1, 1 }, new int[] { 1, 1 }, new int[] { 3, 1 },
						21, "2025-03-20 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN,
						"cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
		entities = SurfaceOverviewTestData.builder().addMatchSnapshots("日本", "J3 リーグ", "ザスパクサツ群馬", "栃木SC",
				new int[] { 0, 0 }, new int[] { 0, 1 }, new int[] { 0, 2 },
				22, "2025-03-27 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN, "cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
	}

	/**
	 * 連敗(ザスパクサツ群馬)
	 */
	@Test
	void test_calcStat_shouldCorrectly_concective5() {
		// Act
		Map<String, Map<String, List<BookDataEntity>>> entities = SurfaceOverviewTestData.builder()
				.addMatchSnapshots("日本", "J3 リーグ", "奈良クラブ", "ザスパクサツ群馬",
						new int[] { 0, 1 }, new int[] { 0, 1 }, new int[] { 2, 1 },
						20, "2025-03-06 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN,
						"cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
		entities = SurfaceOverviewTestData.builder().addMatchSnapshots("日本", "J3 リーグ", "ザスパクサツ群馬", "FC琉球",
				new int[] { 0, 0 }, new int[] { 0, 1 }, new int[] { 0, 4 },
				26, "2025-03-13 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN, "cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
	}

	/**
	 * 連敗(ザスパクサツ群馬)
	 */
	@Test
	void test_calcStat_shouldCorrectly_concective6() {
		// Act
		Map<String, Map<String, List<BookDataEntity>>> entities = SurfaceOverviewTestData.builder()
				.addMatchSnapshots("日本", "J3 リーグ", "カマタマーレ讃岐", "ザスパクサツ群馬",
						new int[] { 2, 1 }, new int[] { 3, 1 }, new int[] { 5, 2 },
						3, "2025-02-06 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN,
						"cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
		entities = SurfaceOverviewTestData.builder().addMatchSnapshots("日本", "J3 リーグ", "ザスパクサツ群馬", "ツエーゲン金沢",
				new int[] { 1, 0 }, new int[] { 2, 1 }, new int[] { 2, 3 },
				4, "2025-02-13 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN, "cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
	}

	/**
	 * 連敗(ザスパクサツ群馬)
	 */
	@Test
	void test_calcStat_shouldCorrectly_concective7() {
		// Act
		Map<String, Map<String, List<BookDataEntity>>> entities = SurfaceOverviewTestData.builder()
				.addMatchSnapshots("日本", "J3 リーグ", "福島ユナイテッドFC", "ザスパクサツ群馬",
						new int[] { 2, 1 }, new int[] { 3, 1 }, new int[] { 3, 2 },
						5, "2025-02-06 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN,
						"cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
	}

	/**
	 * 連敗(ザスパクサツ群馬)
	 */
	@Test
	void test_calcStat_shouldCorrectly_concective8() {
		// Act
		Map<String, Map<String, List<BookDataEntity>>> entities = SurfaceOverviewTestData.builder()
				.addMatchSnapshots("日本", "J3 リーグ", "AC長野パルセイロ", "ザスパクサツ群馬",
						new int[] { 2, 1 }, new int[] { 3, 1 }, new int[] { 3, 2 },
						9, "2025-03-11 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN,
						"cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
		entities = SurfaceOverviewTestData.builder()
				.addMatchSnapshots("日本", "J3 リーグ", "ザスパクサツ群馬", "鹿児島ユナイテッドFC",
						new int[] { 2, 1 }, new int[] { 3, 1 }, new int[] { 3, 3 },
						10, "2025-03-17 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN,
						"cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
		entities = SurfaceOverviewTestData.builder()
				.addMatchSnapshots("日本", "J3 リーグ", "松本山雅FC", "ザスパクサツ群馬",
						new int[] { 0, 1 }, new int[] { 1, 1 }, new int[] { 2, 1 },
						12, "2025-03-17 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN,
						"cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
	}

	/**
	 * 連敗(ザスパクサツ群馬)
	 */
	@Test
	void test_calcStat_shouldCorrectly_concective9() {
		// Act
		Map<String, Map<String, List<BookDataEntity>>> entities = SurfaceOverviewTestData.builder()
				.addMatchSnapshots("日本", "J3 リーグ", "ザスパクサツ群馬", "ヴァンラーレ八戸",
						new int[] { 0, 1 }, new int[] { 0, 1 }, new int[] { 0, 2 },
						11, "2025-03-11 07:25:58", "23:48", BookMakersCommonConst.HALF_TIME, BookMakersCommonConst.FIN,
						"cases/A.csv")
				.build();
		this.surfaceOverviewStat.calcStat(entities);
	}


}
