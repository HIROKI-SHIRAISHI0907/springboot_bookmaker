package dev.application.analyze.bm_m097;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.application.general.CsvHeaderMaps;
import dev.application.general.CsvImport;
import dev.application.main.service.CoreStat;
import dev.common.entity.BookDataEntity;

/**
 * BM_M097統計分析ロジックテスト
 * @author shiraishitoshio
 *
 */
@SpringBootTest(properties = {
		"batch.mode=test",
		"spring.test.database.replace=none"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public class AnalyzeManualStatITTest {

	@Autowired
	private CoreStat coreStat;

	/**
	 * ZIP展開後のCSV格納先
	 * 例: src/test/resources/csv/data_csv_export
	 */
	private static final String CSV_DIR = "src/test/java/dev/application/analyze/bm_m097/data";

	@Test
	void calcStat() throws Exception {
		List<BookDataEntity> rows = loadBookDataEntities(CSV_DIR);

		Assertions.assertFalse(rows.isEmpty(), "CSVから1件も読み込めていません");

		Map<String, Map<String, List<BookDataEntity>>> entities = toAnalyzeEntities(rows);

		Assertions.assertFalse(entities.isEmpty(), "AnalyzeManualStat用のentitiesが空です");

		coreStat.execute(entities, true);
	}

	/**
	 * CSV群を BookDataEntity に読み込む
	 */
	private List<BookDataEntity> loadBookDataEntities(String csvDir) throws Exception {
		Path dir = Path.of(csvDir);
		if (!Files.exists(dir)) {
			throw new IllegalArgumentException("CSV directory not found: " + dir.toAbsolutePath());
		}

		List<BookDataEntity> all = new ArrayList<>();

		try (Stream<Path> stream = Files.list(dir)) {
			List<Path> csvFiles = stream
					.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".csv"))
					.sorted(Comparator.comparing(p -> p.getFileName().toString()))
					.collect(Collectors.toList());

			for (Path csvFile : csvFiles) {
				List<BookDataEntity> list = CsvImport.importCsv(
						csvFile.toString(),
						BookDataEntity.class,
						CsvHeaderMaps.BOOK_DATA,
						(entity, ctx) -> {
							entity.setFilePath(ctx.getCsvPath().toString());
							entity.setFileCount(1);
						}
				);
				all.addAll(list);
			}
		}

		return all;
	}

	/**
	 * AnalyzeManualStat.calcStat(...) 用の構造へ変換
	 *
	 * outer key : country,league
	 * inner key : match key
	 * value     : BookDataEntity list
	 */
	private Map<String, Map<String, List<BookDataEntity>>> toAnalyzeEntities(List<BookDataEntity> rows) {
		return rows.stream()
				.collect(Collectors.groupingBy(
						this::buildLeagueKey,
						LinkedHashMap::new,
						Collectors.groupingBy(
								this::buildMatchKey,
								LinkedHashMap::new,
								Collectors.collectingAndThen(
										Collectors.toList(),
										list -> list.stream()
												.sorted(Comparator.comparingLong(this::seqAsLong))
												.collect(Collectors.toList())
								)
						)
				));
	}

	/**
	 * gameTeamCategory 例:
	 * 日本: J1 リーグ - ラウンド 1
	 * -> 日本,J1 リーグ - ラウンド 1
	 */
	private String buildLeagueKey(BookDataEntity e) {
		String category = nvl(e.getGameTeamCategory());
		int idx = category.indexOf(':');
		if (idx >= 0) {
			String country = category.substring(0, idx).trim();
			String league = category.substring(idx + 1).trim();
			return country + "," + league;
		}
		return category;
	}

	/**
	 * match_id 優先でキー化
	 * 無い場合は home-away-recordTime で代替
	 */
	private String buildMatchKey(BookDataEntity e) {
		if (hasText(e.getMatchId())) {
			return "MID||" + e.getMatchId().trim();
		}
		return nvl(e.getHomeTeamName()) + "-"
				+ nvl(e.getAwayTeamName()) + "||"
				+ nvl(e.getRecordTime());
	}

	private long seqAsLong(BookDataEntity e) {
		try {
			return Long.parseLong(nvl(e.getSeq()));
		} catch (Exception ex) {
			return Long.MAX_VALUE;
		}
	}

	private boolean hasText(String s) {
		return s != null && !s.trim().isEmpty();
	}

	private String nvl(String s) {
		return s == null ? "" : s;
	}
}
