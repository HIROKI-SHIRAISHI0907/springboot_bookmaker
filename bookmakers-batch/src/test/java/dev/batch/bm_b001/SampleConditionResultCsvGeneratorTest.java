package dev.batch.bm_b001;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

public class SampleConditionResultCsvGeneratorTest {

	private static final String HEADER =
			"seq,condition_result_data_seq_id,data_category,times,home_rank,home_team_name,home_score,away_rank,away_team_name,away_score,"
			+ "home_exp,away_exp,home_in_goal_exp,away_in_goal_exp,home_donation,away_donation,home_shoot_all,away_shoot_all,"
			+ "home_shoot_in,away_shoot_in,home_shoot_out,away_shoot_out,home_block_shoot,away_block_shoot,home_big_chance,away_big_chance,"
			+ "home_corner,away_corner,home_box_shoot_in,away_box_shoot_in,home_box_shoot_out,away_box_shoot_out,home_goal_post,away_goal_post,"
			+ "home_goal_head,away_goal_head,home_keeper_save,away_keeper_save,home_free_kick,away_free_kick,home_offside,away_offside,"
			+ "home_foul,away_foul,home_yellow_card,away_yellow_card,home_red_card,away_red_card,home_slow_in,away_slow_in,home_box_touch,away_box_touch,"
			+ "home_pass_count,away_pass_count,home_long_pass_count,away_long_pass_count,home_final_third_pass_count,away_final_third_pass_count,"
			+ "home_cross_count,away_cross_count,home_tackle_count,away_tackle_count,home_clear_count,away_clear_count,home_duel_count,away_duel_count,"
			+ "home_intercept_count,away_intercept_count,record_time,weather,temparature,humid,judge_member,home_manager,away_manager,home_formation,"
			+ "away_formation,studium,capacity,audience,location,home_max_getting_scorer,away_max_getting_scorer,home_max_getting_scorer_game_situation,"
			+ "away_max_getting_scorer_game_situation,home_team_home_score,home_team_home_lost,away_team_home_score,away_team_home_lost,home_team_away_score,"
			+ "home_team_away_lost,away_team_away_score,away_team_away_lost,notice_flg,game_link,goal_time,goal_team_member,judge,home_team_style,"
			+ "away_team_style,probablity,prediction_score_time,game_id,match_id,time_sort_seconds,add_manual_flg,logic_flg,register_id,register_time,update_id,update_time";

	private static final List<String> HEADERS = Arrays.asList(HEADER.split(","));

	private static final DateTimeFormatter OFFSET_DATE_TIME_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssx");

	private static final int MATCH_COUNT = 10;
	private static final int ROUND_START = 15;

	@Test
	void サンプルCSVを10試合作成する() throws Exception {
		Random masterRandom = new Random(20260706L); // 再現性あり
		List<TimePoint> timeline = buildTimeline(masterRandom);

		List<String> lines = new ArrayList<>();
		lines.add(HEADER);

		long seq = 900001L;
		Set<String> usedTeamPairs = new HashSet<>();

		for (int i = 0; i < MATCH_COUNT; i++) {
			int roundNo = ROUND_START + i;

			// 試合ごとに乱数系列を分ける
			Random matchRandom = new Random(20260706L + roundNo * 1000L);

			MatchContext ctx = MatchContext.create(matchRandom, roundNo, usedTeamPairs);
			MatchState state = new MatchState();

			int lastSimulatedSecond = 0;

			for (TimePoint point : timeline) {
				if (point.playProgress) {
					advanceMatchState(state, lastSimulatedSecond, point.timeSortSeconds, matchRandom, ctx, point.originalTimeText);
					lastSimulatedSecond = point.timeSortSeconds;
				} else {
					lastSimulatedSecond = point.timeSortSeconds;
				}

				Map<String, String> row = buildRow(seq++, point, state, ctx);
				lines.add(toCsvLine(row));
			}
		}

		Path outDir = Paths.get("target/generated-test-data");
		Files.createDirectories(outDir);

		Path outFile = outDir.resolve("sample_condition_result_data_10matches.csv");
		Files.write(outFile, lines, StandardCharsets.UTF_8);

		assertTrue(Files.exists(outFile));
		assertFalse(Files.readAllLines(outFile, StandardCharsets.UTF_8).isEmpty());

		System.out.println("生成完了: " + outFile.toAbsolutePath());
	}

	private List<TimePoint> buildTimeline(Random random) {
		List<TimePoint> list = new ArrayList<>();

		for (int minute = 3; minute < 45; minute += 3) {
			int second = randInt(random, 0, 59);
			int sortSec = minute * 60 + second;
			String time = formatMatchTime(minute, second);
			list.add(TimePoint.play(time, sortSec));
		}

		int htSecond = randInt(random, 0, 59);
		list.add(TimePoint.special("ハーフタイム", formatMatchTime(45, htSecond), 45 * 60 + htSecond));

		for (int minute = 46; minute < 90; minute += 3) {
			int second = randInt(random, 0, 59);
			int sortSec = minute * 60 + second;
			String time = formatMatchTime(minute, second);
			list.add(TimePoint.play(time, sortSec));
		}

		int ftSecond = randInt(random, 0, 59);
		list.add(TimePoint.special("終了済", formatMatchTime(90, ftSecond), 90 * 60 + ftSecond));

		return list;
	}

	private void advanceMatchState(
			MatchState state,
			int fromSecond,
			int toSecond,
			Random random,
			MatchContext ctx,
			String currentTimeText) {

		int deltaMinutes = Math.max(1, (toSecond - fromSecond + 59) / 60);

		for (int i = 0; i < deltaMinutes; i++) {
			simulateOneMinute(state, random, ctx, currentTimeText);
		}

		state.homeShootAll = state.homeShootIn + state.homeShootOut + state.homeBlockShoot;
		state.awayShootAll = state.awayShootIn + state.awayShootOut + state.awayBlockShoot;

		state.homeKeeperSave = Math.max(0, state.awayShootIn - state.awayScore);
		state.awayKeeperSave = Math.max(0, state.homeShootIn - state.homeScore);

		state.homeFreeKick = state.awayFoul;
		state.awayFreeKick = state.homeFoul;
	}

	private void simulateOneMinute(
			MatchState state,
			Random random,
			MatchContext ctx,
			String currentTimeText) {

		double homeStrength = 1.08 + homeRankBonus(ctx.homeRank) + 0.08;
		double awayStrength = 1.00 + homeRankBonus(ctx.awayRank);

		addPassProgress(state.homePass, random, 6, 14, 0.72, 0.89);
		addPassProgress(state.awayPass, random, 6, 14, 0.70, 0.88);

		addPassProgress(state.homeLongPass, random, 1, 5, 0.28, 0.55);
		addPassProgress(state.awayLongPass, random, 1, 5, 0.28, 0.55);

		addPassProgress(state.homeFinalThirdPass, random, 1, 5, 0.35, 0.68);
		addPassProgress(state.awayFinalThirdPass, random, 1, 5, 0.35, 0.68);

		addPassProgress(state.homeCross, random, 0, 2, 0.18, 0.48);
		addPassProgress(state.awayCross, random, 0, 2, 0.18, 0.48);

		addPassProgress(state.homeTackle, random, 0, 2, 0.45, 0.92);
		addPassProgress(state.awayTackle, random, 0, 2, 0.45, 0.92);

		state.homeBoxTouch += randInt(random, 0, 2) + (random.nextDouble() < homeStrength * 0.25 ? 1 : 0);
		state.awayBoxTouch += randInt(random, 0, 2) + (random.nextDouble() < awayStrength * 0.23 ? 1 : 0);

		state.homeSlowIn += randInt(random, 0, 1);
		state.awaySlowIn += randInt(random, 0, 1);

		state.homeClearCount += randInt(random, 0, 1);
		state.awayClearCount += randInt(random, 0, 1);

		state.homeDuelCount += randInt(random, 1, 3);
		state.awayDuelCount += randInt(random, 1, 3);

		state.homeInterceptCount += randInt(random, 0, 1);
		state.awayInterceptCount += randInt(random, 0, 1);

		if (random.nextDouble() < 0.10) state.homeFoul++;
		if (random.nextDouble() < 0.10) state.awayFoul++;

		if (random.nextDouble() < 0.03) state.homeYellowCard++;
		if (random.nextDouble() < 0.03) state.awayYellowCard++;

		if (random.nextDouble() < 0.003) state.homeRedCard++;
		if (random.nextDouble() < 0.003) state.awayRedCard++;

		if (random.nextDouble() < 0.04) state.homeOffside++;
		if (random.nextDouble() < 0.04) state.awayOffside++;

		simulateAttack(random, ctx, state, true, homeStrength, currentTimeText);
		simulateAttack(random, ctx, state, false, awayStrength, currentTimeText);

		int homeControl = state.homePass.attempt + state.homeBoxTouch * 2 + state.homeCorner * 3;
		int awayControl = state.awayPass.attempt + state.awayBoxTouch * 2 + state.awayCorner * 3;
		int totalControl = Math.max(1, homeControl + awayControl);

		int homeDonation = (int) Math.round(homeControl * 100.0 / totalControl);
		int awayDonation = 100 - homeDonation;

		state.homeDonation = homeDonation + "%";
		state.awayDonation = awayDonation + "%";
	}

	private void simulateAttack(
			Random random,
			MatchContext ctx,
			MatchState state,
			boolean home,
			double strength,
			String currentTimeText) {

		double chance = 0.16 * strength;

		if (random.nextDouble() > chance) {
			return;
		}

		double shotRoll = random.nextDouble();

		if (shotRoll < 0.33) {
			if (home) {
				state.homeShootIn++;
				state.homeBoxShootIn += random.nextDouble() < 0.72 ? 1 : 0;
			} else {
				state.awayShootIn++;
				state.awayBoxShootIn += random.nextDouble() < 0.72 ? 1 : 0;
			}

			double xg = randDouble(random, 0.08, 0.42);
			if (home) {
				state.homeExp += xg;
				state.homeInGoalExp += xg;
			} else {
				state.awayExp += xg;
				state.awayInGoalExp += xg;
			}

			boolean bigChance = xg >= 0.25 || random.nextDouble() < 0.15;
			if (bigChance) {
				if (home) state.homeBigChance++;
				else state.awayBigChance++;
			}

			double goalProb = bigChance ? 0.40 : 0.22;
			if (random.nextDouble() < goalProb) {
				recordGoal(state, ctx, home, currentTimeText, random);
			}
			return;
		}

		if (shotRoll < 0.72) {
			if (home) {
				state.homeShootOut++;
				state.homeBoxShootOut += random.nextDouble() < 0.45 ? 1 : 0;
			} else {
				state.awayShootOut++;
				state.awayBoxShootOut += random.nextDouble() < 0.45 ? 1 : 0;
			}

			double xg = randDouble(random, 0.02, 0.16);
			if (home) {
				state.homeExp += xg;
				if (random.nextDouble() < 0.08) state.homeGoalPost++;
			} else {
				state.awayExp += xg;
				if (random.nextDouble() < 0.08) state.awayGoalPost++;
			}
			return;
		}

		if (home) {
			state.homeBlockShoot++;
			state.homeCorner += random.nextDouble() < 0.35 ? 1 : 0;
			state.homeExp += randDouble(random, 0.01, 0.10);
		} else {
			state.awayBlockShoot++;
			state.awayCorner += random.nextDouble() < 0.35 ? 1 : 0;
			state.awayExp += randDouble(random, 0.01, 0.10);
		}
	}

	private void recordGoal(
			MatchState state,
			MatchContext ctx,
			boolean home,
			String currentTimeText,
			Random random) {

		if (home) {
			state.homeScore++;
			if (random.nextDouble() < 0.18) {
				state.homeGoalHead++;
			}
			String scorer = ctx.homePlayers.get(randInt(random, 0, ctx.homePlayers.size() - 1));
			state.homeScorerCount.merge(scorer, 1, Integer::sum);
			state.goalTimeList.add(currentTimeText);
			state.goalTeamMemberList.add(scorer);
		} else {
			state.awayScore++;
			if (random.nextDouble() < 0.18) {
				state.awayGoalHead++;
			}
			String scorer = ctx.awayPlayers.get(randInt(random, 0, ctx.awayPlayers.size() - 1));
			state.awayScorerCount.merge(scorer, 1, Integer::sum);
			state.goalTimeList.add(currentTimeText);
			state.goalTeamMemberList.add(scorer);
		}
	}

	private Map<String, String> buildRow(
			long seq,
			TimePoint point,
			MatchState state,
			MatchContext ctx) {

		Map<String, String> row = emptyRow();

		OffsetDateTime recordTime = ctx.baseRecordTime.plusSeconds(point.timeSortSeconds);

		row.put("seq", String.valueOf(seq));
		row.put("condition_result_data_seq_id", "");
		row.put("data_category", ctx.dataCategory);
		row.put("times", point.displayTime);

		row.put("home_rank", String.valueOf(ctx.homeRank));
		row.put("home_team_name", ctx.homeTeamName);
		row.put("home_score", String.valueOf(state.homeScore));

		row.put("away_rank", String.valueOf(ctx.awayRank));
		row.put("away_team_name", ctx.awayTeamName);
		row.put("away_score", String.valueOf(state.awayScore));

		row.put("home_exp", formatDouble(state.homeExp));
		row.put("away_exp", formatDouble(state.awayExp));
		row.put("home_in_goal_exp", formatDouble(state.homeInGoalExp));
		row.put("away_in_goal_exp", formatDouble(state.awayInGoalExp));

		row.put("home_donation", state.homeDonation);
		row.put("away_donation", state.awayDonation);

		row.put("home_shoot_all", String.valueOf(state.homeShootAll));
		row.put("away_shoot_all", String.valueOf(state.awayShootAll));
		row.put("home_shoot_in", String.valueOf(state.homeShootIn));
		row.put("away_shoot_in", String.valueOf(state.awayShootIn));
		row.put("home_shoot_out", String.valueOf(state.homeShootOut));
		row.put("away_shoot_out", String.valueOf(state.awayShootOut));
		row.put("home_block_shoot", String.valueOf(state.homeBlockShoot));
		row.put("away_block_shoot", String.valueOf(state.awayBlockShoot));
		row.put("home_big_chance", String.valueOf(state.homeBigChance));
		row.put("away_big_chance", String.valueOf(state.awayBigChance));
		row.put("home_corner", String.valueOf(state.homeCorner));
		row.put("away_corner", String.valueOf(state.awayCorner));
		row.put("home_box_shoot_in", String.valueOf(state.homeBoxShootIn));
		row.put("away_box_shoot_in", String.valueOf(state.awayBoxShootIn));
		row.put("home_box_shoot_out", String.valueOf(state.homeBoxShootOut));
		row.put("away_box_shoot_out", String.valueOf(state.awayBoxShootOut));
		row.put("home_goal_post", String.valueOf(state.homeGoalPost));
		row.put("away_goal_post", String.valueOf(state.awayGoalPost));
		row.put("home_goal_head", String.valueOf(state.homeGoalHead));
		row.put("away_goal_head", String.valueOf(state.awayGoalHead));
		row.put("home_keeper_save", String.valueOf(state.homeKeeperSave));
		row.put("away_keeper_save", String.valueOf(state.awayKeeperSave));
		row.put("home_free_kick", String.valueOf(state.homeFreeKick));
		row.put("away_free_kick", String.valueOf(state.awayFreeKick));
		row.put("home_offside", String.valueOf(state.homeOffside));
		row.put("away_offside", String.valueOf(state.awayOffside));
		row.put("home_foul", String.valueOf(state.homeFoul));
		row.put("away_foul", String.valueOf(state.awayFoul));
		row.put("home_yellow_card", String.valueOf(state.homeYellowCard));
		row.put("away_yellow_card", String.valueOf(state.awayYellowCard));
		row.put("home_red_card", String.valueOf(state.homeRedCard));
		row.put("away_red_card", String.valueOf(state.awayRedCard));
		row.put("home_slow_in", String.valueOf(state.homeSlowIn));
		row.put("away_slow_in", String.valueOf(state.awaySlowIn));
		row.put("home_box_touch", String.valueOf(state.homeBoxTouch));
		row.put("away_box_touch", String.valueOf(state.awayBoxTouch));

		row.put("home_pass_count", state.homePass.format());
		row.put("away_pass_count", state.awayPass.format());
		row.put("home_long_pass_count", state.homeLongPass.format());
		row.put("away_long_pass_count", state.awayLongPass.format());
		row.put("home_final_third_pass_count", state.homeFinalThirdPass.format());
		row.put("away_final_third_pass_count", state.awayFinalThirdPass.format());
		row.put("home_cross_count", state.homeCross.format());
		row.put("away_cross_count", state.awayCross.format());
		row.put("home_tackle_count", state.homeTackle.format());
		row.put("away_tackle_count", state.awayTackle.format());

		row.put("home_clear_count", String.valueOf(state.homeClearCount));
		row.put("away_clear_count", String.valueOf(state.awayClearCount));
		row.put("home_duel_count", String.valueOf(state.homeDuelCount));
		row.put("away_duel_count", String.valueOf(state.awayDuelCount));
		row.put("home_intercept_count", String.valueOf(state.homeInterceptCount));
		row.put("away_intercept_count", String.valueOf(state.awayInterceptCount));

		row.put("record_time", OFFSET_DATE_TIME_FORMAT.format(recordTime));
		row.put("weather", ctx.weather);
		row.put("temparature", ctx.temperature);
		row.put("humid", ctx.humid);
		row.put("judge_member", ctx.judgeMember);
		row.put("home_manager", ctx.homeManager);
		row.put("away_manager", ctx.awayManager);
		row.put("home_formation", ctx.homeFormation);
		row.put("away_formation", ctx.awayFormation);
		row.put("studium", ctx.stadium);
		row.put("capacity", String.valueOf(ctx.capacity));
		row.put("audience", String.valueOf(ctx.audience));
		row.put("location", ctx.location);

		row.put("home_max_getting_scorer", getTopScorer(state.homeScorerCount));
		row.put("away_max_getting_scorer", getTopScorer(state.awayScorerCount));
		row.put("home_max_getting_scorer_game_situation", state.homeScore > 0 ? "試合中" : "");
		row.put("away_max_getting_scorer_game_situation", state.awayScore > 0 ? "試合中" : "");

		row.put("home_team_home_score", String.valueOf(ctx.homeTeamHomeScore));
		row.put("home_team_home_lost", String.valueOf(ctx.homeTeamHomeLost));
		row.put("away_team_home_score", String.valueOf(ctx.awayTeamHomeScore));
		row.put("away_team_home_lost", String.valueOf(ctx.awayTeamHomeLost));
		row.put("home_team_away_score", String.valueOf(ctx.homeTeamAwayScore));
		row.put("home_team_away_lost", String.valueOf(ctx.homeTeamAwayLost));
		row.put("away_team_away_score", String.valueOf(ctx.awayTeamAwayScore));
		row.put("away_team_away_lost", String.valueOf(ctx.awayTeamAwayLost));

		row.put("notice_flg", "");
		row.put("game_link", ctx.gameLink);
		row.put("goal_time", String.join(";", state.goalTimeList));
		row.put("goal_team_member", String.join(";", state.goalTeamMemberList));
		row.put("judge", "");
		row.put("home_team_style", "ポゼッション");
		row.put("away_team_style", "ショートカウンター");
		row.put("probablity", "");
		row.put("prediction_score_time", "");
		row.put("game_id", ctx.gameId);
		row.put("match_id", ctx.matchId);
		row.put("time_sort_seconds", String.valueOf(point.timeSortSeconds));
		row.put("add_manual_flg", "");
		row.put("logic_flg", "0");
		row.put("register_id", "junit");
		row.put("register_time", OFFSET_DATE_TIME_FORMAT.format(ctx.baseRecordTime));
		row.put("update_id", "junit");
		row.put("update_time", OFFSET_DATE_TIME_FORMAT.format(recordTime));

		return row;
	}

	private Map<String, String> emptyRow() {
		Map<String, String> row = new LinkedHashMap<>();
		for (String h : HEADERS) {
			row.put(h, "");
		}
		return row;
	}

	private String toCsvLine(Map<String, String> row) {
		return HEADERS.stream()
				.map(h -> csvEscape(row.getOrDefault(h, "")))
				.collect(Collectors.joining(","));
	}

	private String csvEscape(String value) {
		String v = value == null ? "" : value;
		if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
			return "\"" + v.replace("\"", "\"\"") + "\"";
		}
		return v;
	}

	private void addPassProgress(
			AccuracyCounter counter,
			Random random,
			int minAttempt,
			int maxAttempt,
			double minRate,
			double maxRate) {

		int addAttempt = randInt(random, minAttempt, maxAttempt);
		if (addAttempt <= 0) {
			return;
		}
		double rate = randDouble(random, minRate, maxRate);
		int addSuccess = (int) Math.round(addAttempt * rate);
		addSuccess = Math.max(0, Math.min(addAttempt, addSuccess));
		counter.success += addSuccess;
		counter.attempt += addAttempt;
	}

	private String getTopScorer(Map<String, Integer> scorerMap) {
		return scorerMap.entrySet().stream()
				.sorted((a, b) -> {
					int cmp = Integer.compare(b.getValue(), a.getValue());
					return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
				})
				.map(Map.Entry::getKey)
				.findFirst()
				.orElse("");
	}

	private String formatDouble(double value) {
		return String.format(java.util.Locale.ROOT, "%.2f", value);
	}

	private String formatMatchTime(int minute, int second) {
		return minute + ":" + String.format("%02d", second);
	}

	private double homeRankBonus(int rank) {
		return Math.max(-0.10, (11 - rank) * 0.015);
	}

	private static int randInt(Random random, int min, int max) {
		if (max <= min) {
			return min;
		}
		return min + random.nextInt(max - min + 1);
	}

	private static double randDouble(Random random, double min, double max) {
		return min + (max - min) * random.nextDouble();
	}

	private static String pick(Random random, String... values) {
		return values[random.nextInt(values.length)];
	}

	private static String randomTeamName(Random random) {
		String[] prefix = {
				"FC", "SC", "AC", "ユナイテッド", "アスレティック", "レアル",
				"シティ", "スター", "フェニックス", "ヴォルテックス", "オリオン", "グラン"
		};
		String[] core = {
				"サンプル", "ノヴァ", "アルファ", "ゼニス", "ルミナ", "ストーム",
				"リバティ", "クレスト", "フロンティア", "ブレイズ", "エクリプス", "シグマ"
		};
		String[] suffix = {
				"FC", "SC", "ユース", "タウン", "クラブ", "レギオン",
				"ローヴァーズ", "アスレ", "スポルティング", "ユナイテッド"
		};

		String p = pick(random, prefix);
		String c = pick(random, core);
		String s = pick(random, suffix);

		if (random.nextBoolean()) {
			return p + " " + c;
		}
		return c + " " + s;
	}

	private static class TimePoint {
		private final String displayTime;
		private final String originalTimeText;
		private final int timeSortSeconds;
		private final boolean playProgress;

		private TimePoint(String displayTime, String originalTimeText, int timeSortSeconds, boolean playProgress) {
			this.displayTime = displayTime;
			this.originalTimeText = originalTimeText;
			this.timeSortSeconds = timeSortSeconds;
			this.playProgress = playProgress;
		}

		private static TimePoint play(String timeText, int timeSortSeconds) {
			return new TimePoint(timeText, timeText, timeSortSeconds, true);
		}

		private static TimePoint special(String label, String originalTimeText, int timeSortSeconds) {
			return new TimePoint(label, originalTimeText, timeSortSeconds, false);
		}
	}

	private static class AccuracyCounter {
		private int success;
		private int attempt;

		private String format() {
			if (attempt <= 0) {
				return "";
			}
			int percent = (int) Math.round(success * 100.0 / attempt);
			return percent + "% (" + success + "/" + attempt + ")";
		}
	}

	private static class MatchState {
		private int homeScore;
		private int awayScore;

		private double homeExp;
		private double awayExp;
		private double homeInGoalExp;
		private double awayInGoalExp;

		private String homeDonation = "50%";
		private String awayDonation = "50%";

		private int homeShootAll;
		private int awayShootAll;
		private int homeShootIn;
		private int awayShootIn;
		private int homeShootOut;
		private int awayShootOut;
		private int homeBlockShoot;
		private int awayBlockShoot;
		private int homeBigChance;
		private int awayBigChance;
		private int homeCorner;
		private int awayCorner;
		private int homeBoxShootIn;
		private int awayBoxShootIn;
		private int homeBoxShootOut;
		private int awayBoxShootOut;
		private int homeGoalPost;
		private int awayGoalPost;
		private int homeGoalHead;
		private int awayGoalHead;
		private int homeKeeperSave;
		private int awayKeeperSave;
		private int homeFreeKick;
		private int awayFreeKick;
		private int homeOffside;
		private int awayOffside;
		private int homeFoul;
		private int awayFoul;
		private int homeYellowCard;
		private int awayYellowCard;
		private int homeRedCard;
		private int awayRedCard;
		private int homeSlowIn;
		private int awaySlowIn;
		private int homeBoxTouch;
		private int awayBoxTouch;

		private AccuracyCounter homePass = new AccuracyCounter();
		private AccuracyCounter awayPass = new AccuracyCounter();
		private AccuracyCounter homeLongPass = new AccuracyCounter();
		private AccuracyCounter awayLongPass = new AccuracyCounter();
		private AccuracyCounter homeFinalThirdPass = new AccuracyCounter();
		private AccuracyCounter awayFinalThirdPass = new AccuracyCounter();
		private AccuracyCounter homeCross = new AccuracyCounter();
		private AccuracyCounter awayCross = new AccuracyCounter();
		private AccuracyCounter homeTackle = new AccuracyCounter();
		private AccuracyCounter awayTackle = new AccuracyCounter();

		private int homeClearCount;
		private int awayClearCount;
		private int homeDuelCount;
		private int awayDuelCount;
		private int homeInterceptCount;
		private int awayInterceptCount;

		private List<String> goalTimeList = new ArrayList<>();
		private List<String> goalTeamMemberList = new ArrayList<>();
		private Map<String, Integer> homeScorerCount = new HashMap<>();
		private Map<String, Integer> awayScorerCount = new HashMap<>();
	}

	private static class MatchContext {
		private String dataCategory;
		private int homeRank;
		private int awayRank;
		private String homeTeamName;
		private String awayTeamName;
		private String weather;
		private String temperature;
		private String humid;
		private String judgeMember;
		private String homeManager;
		private String awayManager;
		private String homeFormation;
		private String awayFormation;
		private String stadium;
		private int capacity;
		private int audience;
		private String location;
		private int homeTeamHomeScore;
		private int homeTeamHomeLost;
		private int awayTeamHomeScore;
		private int awayTeamHomeLost;
		private int homeTeamAwayScore;
		private int homeTeamAwayLost;
		private int awayTeamAwayScore;
		private int awayTeamAwayLost;
		private String gameLink;
		private String gameId;
		private String matchId;
		private OffsetDateTime baseRecordTime;
		private List<String> homePlayers;
		private List<String> awayPlayers;

		private static MatchContext create(Random random, int roundNo, Set<String> usedTeamPairs) {
			MatchContext ctx = new MatchContext();

			String homeTeam;
			String awayTeam;
			String pairKey;

			do {
				homeTeam = randomTeamName(random);
				awayTeam = randomTeamName(random);
				pairKey = homeTeam + " vs " + awayTeam;
			} while (homeTeam.equals(awayTeam) || usedTeamPairs.contains(pairKey));

			usedTeamPairs.add(pairKey);

			ctx.dataCategory = "サンプル国: サンプルリーグ - ラウンド " + roundNo;
			ctx.homeRank = randInt(random, 1, 8);
			ctx.awayRank = randInt(random, 3, 15);
			ctx.homeTeamName = homeTeam;
			ctx.awayTeamName = awayTeam;
			ctx.weather = pick(random, "晴れ", "曇り", "小雨");
			ctx.temperature = randInt(random, 14, 28) + "℃";
			ctx.humid = randInt(random, 35, 78) + "%";
			ctx.judgeMember = "サンプル主審" + roundNo;
			ctx.homeManager = "ホーム監督" + roundNo;
			ctx.awayManager = "アウェイ監督" + roundNo;
			ctx.homeFormation = pick(random, "4-2-3-1", "4-3-3", "3-4-2-1");
			ctx.awayFormation = pick(random, "4-4-2", "4-3-3", "5-3-2");
			ctx.stadium = "サンプルスタジアム" + roundNo;
			ctx.capacity = randInt(random, 18000, 42000);
			ctx.audience = randInt(random, (int) (ctx.capacity * 0.45), (int) (ctx.capacity * 0.95));
			ctx.location = "サンプルシティ" + roundNo;
			ctx.homeTeamHomeScore = randInt(random, 10, 32);
			ctx.homeTeamHomeLost = randInt(random, 5, 20);
			ctx.awayTeamHomeScore = randInt(random, 8, 28);
			ctx.awayTeamHomeLost = randInt(random, 6, 22);
			ctx.homeTeamAwayScore = randInt(random, 8, 25);
			ctx.homeTeamAwayLost = randInt(random, 7, 24);
			ctx.awayTeamAwayScore = randInt(random, 6, 24);
			ctx.awayTeamAwayLost = randInt(random, 8, 26);
			ctx.gameId = "JUNIT_GAME_" + String.format("%04d", roundNo);
			ctx.matchId = "JUNIT_MATCH_" + String.format("%04d", roundNo);
			ctx.gameLink = "https://www.flashscore.co.jp/match/soccer/sample-home/sample-away/?mid=" + ctx.matchId;
			ctx.baseRecordTime = OffsetDateTime.of(2026, 7, 6, 1, 0, 0, 0, ZoneOffset.UTC).plusDays(roundNo - ROUND_START);

			ctx.homePlayers = List.of(
					ctx.homeTeamName + "_9",
					ctx.homeTeamName + "_10",
					ctx.homeTeamName + "_11",
					ctx.homeTeamName + "_7",
					ctx.homeTeamName + "_8",
					ctx.homeTeamName + "_18"
			);

			ctx.awayPlayers = List.of(
					ctx.awayTeamName + "_9",
					ctx.awayTeamName + "_10",
					ctx.awayTeamName + "_11",
					ctx.awayTeamName + "_7",
					ctx.awayTeamName + "_14",
					ctx.awayTeamName + "_20"
			);

			return ctx;
		}
	}
}
