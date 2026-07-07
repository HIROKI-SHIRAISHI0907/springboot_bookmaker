package dev.batch.bm_b001;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

public class SampleFutureCsvGeneratorTest {

	private static final String HEADER =
			"file,seq,gameTeamCategory,futureTime,homeRank,awayRank,homeTeamName,awayTeamName,gameLink,startFlg,dataTime";

	private static final String OUTPUT_FILE_NAME = "future_1.csv";
	private static final int MATCH_COUNT = 10;
	private static final int ROUND_START = 15;

	private static final DateTimeFormatter DATE_TIME_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	@Test
	void future_1_csv形式のサンプルを10試合作成する() throws Exception {
		Random random = new Random(20260706L); // 再現性あり

		List<String> lines = new ArrayList<>();
		lines.add(HEADER);

		List<FutureMatchRow> matches = buildSampleMatches(random, MATCH_COUNT);
		for (FutureMatchRow match : matches) {
			lines.add(toCsvLine(match));
		}

		Path outDir = Paths.get("target/generated-test-data");
		Files.createDirectories(outDir);

		Path outFile = outDir.resolve(OUTPUT_FILE_NAME);
		Files.write(outFile, lines, StandardCharsets.UTF_8);

		assertTrue(Files.exists(outFile));
		assertFalse(Files.readAllLines(outFile, StandardCharsets.UTF_8).isEmpty());

		System.out.println("生成完了: " + outFile.toAbsolutePath());
	}

	private List<FutureMatchRow> buildSampleMatches(Random random, int count) {
		List<FutureMatchRow> rows = new ArrayList<>();
		Set<String> usedPairSet = new HashSet<>();
		Set<LocalDateTime> usedFutureTimes = new HashSet<>();

		LocalDate baseDate = LocalDate.of(2026, 7, 5);
		LocalDateTime dataTime = LocalDateTime.of(2026, 7, 5, 15, 4, 59);

		for (int i = 0; i < count; i++) {
			int seq = i + 1;
			int roundNo = ROUND_START + i;

			String homeTeam;
			String awayTeam;
			String pairKey;

			do {
				homeTeam = randomTeamName(random);
				awayTeam = randomTeamName(random);
				pairKey = homeTeam + " vs " + awayTeam;
			} while (homeTeam.equals(awayTeam) || usedPairSet.contains(pairKey));

			usedPairSet.add(pairKey);

			LocalDateTime futureTime;
			do {
				int hour = randInt(random, 12, 23);
				int minuteUnit = randInt(random, 0, 3); // 00, 15, 30, 45
				int minute = minuteUnit * 15;
				futureTime = LocalDateTime.of(baseDate, LocalTime.of(hour, minute));
			} while (usedFutureTimes.contains(futureTime));
			usedFutureTimes.add(futureTime);

			FutureMatchRow row = new FutureMatchRow();
			row.file = OUTPUT_FILE_NAME;
			row.seq = String.valueOf(seq);

			// サンプル本番形式に寄せて country は含めず league のみ
			row.gameTeamCategory = "サンプル国: サンプルリーグ - ラウンド " + roundNo;

			row.futureTime = DATE_TIME_FORMAT.format(futureTime);
			row.homeRank = String.valueOf(randInt(random, 1, 12));
			row.awayRank = String.valueOf(randInt(random, 1, 12));
			row.homeTeamName = homeTeam;
			row.awayTeamName = awayTeam;
			row.gameLink = buildDummyGameLink(homeTeam, awayTeam, roundNo);
			row.startFlg = "0";
			row.dataTime = DATE_TIME_FORMAT.format(dataTime);

			rows.add(row);
		}

		return rows.stream()
				.sorted((a, b) -> {
					int cmp = a.futureTime.compareTo(b.futureTime);
					if (cmp != 0) {
						return cmp;
					}
					return Integer.compare(Integer.parseInt(a.seq), Integer.parseInt(b.seq));
				})
				.collect(Collectors.toList());
	}

	private String buildDummyGameLink(String homeTeam, String awayTeam, int roundNo) {
		String homeSlug = slugify(homeTeam);
		String awaySlug = slugify(awayTeam);
		return "https://www.flashscore.co.jp/match/soccer/"
				+ homeSlug + "/"
				+ awaySlug + "/?mid=SAMPLE"
				+ String.format("%04d", roundNo);
	}

	private String slugify(String value) {
		String s = value == null ? "" : value.trim().toLowerCase();
		s = s.replace(" ", "-");
		s = s.replace("　", "-");
		s = s.replaceAll("[^a-z0-9ぁ-んァ-ヶ一-龠ー-]", "");
		if (s.isBlank()) {
			return "sample-team";
		}
		return s;
	}

	private String randomTeamName(Random random) {
		String[] prefix = {
				"FC", "SC", "AC", "ユナイテッド", "シティ", "アスレティック",
				"レアル", "オリオン", "スター", "フェニックス", "ヴォルテックス", "グラン"
		};

		String[] core = {
				"サンプル", "ノヴァ", "アルファ", "ゼニス", "ルミナ", "ストーム",
				"クレスト", "ブレイズ", "フロンティア", "シグマ", "エクリプス", "リバティ"
		};

		String[] suffix = {
				"FC", "SC", "タウン", "クラブ", "ローヴァーズ", "ユナイテッド",
				"スポルティング", "レギオン", "アスレ", "シティ"
		};

		if (random.nextBoolean()) {
			return pick(random, prefix) + " " + pick(random, core);
		}
		return pick(random, core) + " " + pick(random, suffix);
	}

	private String pick(Random random, String... values) {
		return values[random.nextInt(values.length)];
	}

	private int randInt(Random random, int min, int max) {
		if (max <= min) {
			return min;
		}
		return min + random.nextInt(max - min + 1);
	}

	private String toCsvLine(FutureMatchRow row) {
		List<String> values = List.of(
				row.file,
				row.seq,
				row.gameTeamCategory,
				row.futureTime,
				row.homeRank,
				row.awayRank,
				row.homeTeamName,
				row.awayTeamName,
				row.gameLink,
				row.startFlg,
				row.dataTime
		);

		return values.stream()
				.map(this::csvEscape)
				.collect(Collectors.joining(","));
	}

	private String csvEscape(String value) {
		String v = value == null ? "" : value;
		if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
			return "\"" + v.replace("\"", "\"\"") + "\"";
		}
		return v;
	}

	private static class FutureMatchRow {
		private String file;
		private String seq;
		private String gameTeamCategory;
		private String futureTime;
		private String homeRank;
		private String awayRank;
		private String homeTeamName;
		private String awayTeamName;
		private String gameLink;
		private String startFlg;
		private String dataTime;
	}
}
