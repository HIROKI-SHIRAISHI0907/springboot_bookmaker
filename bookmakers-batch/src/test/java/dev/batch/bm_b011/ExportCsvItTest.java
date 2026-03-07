package dev.batch.bm_b011;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import dev.batch.repository.bm.BookCsvDataRepository;
import dev.common.config.PathConfig;
import dev.common.entity.DataEntity;
import dev.common.filemng.FileMngWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;

class ExportCsvItTest {

	private static final Pattern ROOT_CSV = Pattern.compile("^\\d+\\.csv$");

	@TempDir
	Path tempDir;

	@Captor
	ArgumentCaptor<String> srcKeyCap;
	@Captor
	ArgumentCaptor<String> dstBucketCap;
	@Captor
	ArgumentCaptor<String> dstKeyCap;

	@Test
	void execute_S3直下の最大番号から連番でCSVが作られ_commitで直下へcopyされる() throws Exception {
		// ===== mocks =====
		S3Operator s3Operator = mock(S3Operator.class);
		PathConfig config = mock(PathConfig.class);
		ReaderCurrentCsvInfoBean bean = mock(ReaderCurrentCsvInfoBean.class);
		CsvArtifactHelper helper = mock(CsvArtifactHelper.class);
		BookCsvDataRepository repo = mock(BookCsvDataRepository.class);
		ManageLoggerComponent logger = mock(ManageLoggerComponent.class);

		// ===== target with reflection injection =====
		ExportCsv target = new ExportCsv();
		ReflectionTestUtils.setField(target, "s3Operator", s3Operator);
		ReflectionTestUtils.setField(target, "config", config);
		ReflectionTestUtils.setField(target, "bean", bean);
		ReflectionTestUtils.setField(target, "helper", helper);
		ReflectionTestUtils.setField(target, "bookCsvDataRepository", repo);
		ReflectionTestUtils.setField(target, "manageLoggerComponent", logger);
		ReflectionTestUtils.setField(target, "localOnly", false);

		// ===== config =====
		String bucket = "aws-s3-stat-csv";
		when(config.getCsvFolder()).thenReturn(tempDir.toString());
		when(config.getS3BucketsStats()).thenReturn(bucket);

		// ===== S3 download: seqList/teamList をローカルに「存在する形」で作る =====
		doAnswer(inv -> {
			String key = inv.getArgument(1, String.class);
			Path out = inv.getArgument(2, Path.class);
			Files.createDirectories(out.getParent());
			if ("seqList.txt".equals(key)) {
				Files.writeString(out, "[]", StandardCharsets.UTF_8);
			} else if ("data_team_list.txt".equals(key)) {
				Files.writeString(out, "", StandardCharsets.UTF_8);
			}
			return null;
		}).when(s3Operator).downloadToFile(eq(bucket), anyString(), any(Path.class));

		// ===== 既存CSV情報（空にして差分判定で全部作らせる）=====
		doNothing().when(bean).init();
		when(bean.getCsvInfo()).thenReturn(Collections.emptyMap());

		// ===== DB（Repository）: グループ2つ作る =====
		// sortSeqs は home/away が変わるとグループを切るので、2グループになる行を返す
		List<SeqWithKey> rows = new ArrayList<>();
		rows.add(seqRow("A", "B", 1));
		rows.add(seqRow("A", "B", 2));
		rows.add(seqRow("C", "D", 10));
		rows.add(seqRow("C", "D", 11));
		when(repo.findAllSeqsWithKey()).thenReturn(rows);

		// findByData はグループごとに最低1件返す（空だとCSV作られない）
		when(repo.findByData(anyList())).thenAnswer(inv -> {
			@SuppressWarnings("unchecked")
			List<Integer> ids = (List<Integer>) inv.getArgument(0);

			DataEntity e = new DataEntity();
			e.setSeq(String.valueOf(ids.get(0)));
			e.setDataCategory("ラウンド1");
			e.setHomeTeamName("HOME");
			e.setAwayTeamName("AWAY");
			e.setRecordTime("2020-01-01T00:00:00");
			return List.of(e);
		});

		// ===== helper =====
		CsvArtifactResource res = mock(CsvArtifactResource.class);
		when(helper.getData()).thenReturn(res);
		when(helper.csvCondition(anyList(), eq(res))).thenReturn(true);
		when(helper.abnormalChk(anyList())).thenAnswer(inv -> inv.getArgument(0));

		// ===== S3: 直下の既存キー一覧（最大=2にする）=====
		// getMaxCsvNoFromS3 は prefix="" の listKeys を使う
		List<String> rootKeys = List.of("1.csv", "2.csv", "seqList.txt");
		CopyOnWriteArrayList<String> uploadedTmpKeys = new CopyOnWriteArrayList<>();

		// listKeys：prefix に応じて返すものを変える（root / tmp）
		when(s3Operator.listKeys(eq(bucket), anyString())).thenAnswer(inv -> {
			String prefix = inv.getArgument(1, String.class);
			if (prefix == null || prefix.isBlank()) {
				return rootKeys; // 本番prefix（直下）
			}
			if (prefix.startsWith("tmp/")) {
				return new ArrayList<>(uploadedTmpKeys); // commit時：tmpにPUTしたキー群を返す
			}
			return List.of();
		});

		// copy/delete：副作用なし
		doNothing().when(s3Operator).copy(eq(bucket), anyString(), eq(bucket), anyString());
		doNothing().when(s3Operator).delete(eq(bucket), anyString());

		doAnswer(inv -> {
		    String b = inv.getArgument(0, String.class);
		    String key = inv.getArgument(1, String.class);
		    Path local = inv.getArgument(2, Path.class);

		    uploadedTmpKeys.add(key); // ★ commit 用に収集

		    System.out.println("UPLOAD bucket=" + b + " key=" + key + " local=" + local);
		    return null;
		}).when(s3Operator).uploadFile(eq(bucket), anyString(), any(Path.class));

		doAnswer(inv -> {
			System.out.println("COPY srcKey=" + inv.getArgument(1)
					+ " -> dstKey=" + inv.getArgument(3));
			return null;
		}).when(s3Operator).copy(anyString(), anyString(), anyString(), anyString());

		// ===== FileMngWrapper の new を潰す（csvWriteは何もしない）=====
		try (MockedConstruction<FileMngWrapper> mocked = Mockito.mockConstruction(
				FileMngWrapper.class,
				(mock, ctx) -> doNothing().when(mock).csvWrite(anyString(), anyList()))) {
			target.execute();
		}

		// ===== Assertions / Debug dump =====

		// 1) upload（PUT）された S3 key 一覧（＝tmp側）
		ArgumentCaptor<String> uploadKeyCap = ArgumentCaptor.forClass(String.class);
		verify(s3Operator, atLeastOnce()).uploadFile(eq(bucket), uploadKeyCap.capture(), any(Path.class));

		System.out.println("=== UPLOAD keys (PUT) ===");
		uploadKeyCap.getAllValues().forEach(System.out::println);

		// 期待：tmp/root/<runId>/3.csv, tmp/root/<runId>/4.csv, tmp/root/<runId>/seqList.txt, tmp/root/<runId>/data_team_list.txt 等

		// 2) copy の dstKey 一覧（＝最終的にバケット直下に配置されたキー）
		ArgumentCaptor<String> dstKeyCap = ArgumentCaptor.forClass(String.class);
		verify(s3Operator, atLeastOnce()).copy(eq(bucket), anyString(), eq(bucket), dstKeyCap.capture());

		System.out.println("=== COPY dst keys (FINAL) ===");
		dstKeyCap.getAllValues().forEach(System.out::println);

		// 期待：3.csv, 4.csv, seqList.txt, data_team_list.txt

		// ===== Assertions =====
		// 1) commit後に「直下へ copy されるキー」を捕まえる（ここが最終成果物）
		var finalKeyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(s3Operator, atLeastOnce()).copy(eq(bucket), anyString(), eq(bucket), finalKeyCaptor.capture());

		List<String> finalKeys = finalKeyCaptor.getAllValues();

		// (a) 管理ファイルが直下にcopyされている
		assertTrue(finalKeys.contains("seqList.txt"));
		assertTrue(finalKeys.contains("data_team_list.txt"));

		// (b) CSVは max(=2)+1 から連番になっている（グループ2つ → 3.csv, 4.csv）
		List<Integer> csvNos = finalKeys.stream()
				.filter(k -> ROOT_CSV.matcher(k).matches())
				.map(k -> Integer.parseInt(k.replace(".csv", "")))
				.sorted()
				.collect(Collectors.toList());

		assertEquals(List.of(3, 4), csvNos, "最大番号+1から連番で直下へcopyされること");

		// 2) 作成されたCSV数（直下にcopyされた csv の数）
		assertEquals(2, csvNos.size(), "作成されたCSV数が期待通りであること");

		assertTrue(
			    uploadKeyCap.getAllValues().stream().allMatch(k -> k.startsWith("tmp/")),
			    "uploadFile は tmp/ 配下に PUT される想定"
			);
	}

	private static SeqWithKey seqRow(String home, String away, int seq) {
		SeqWithKey r = new SeqWithKey();
		r.setHomeTeamName(home);
		r.setAwayTeamName(away);
		r.setSeq(seq);
		r.setTimes("第一ハーフ");
		r.setDataCategory("ラウンド1");
		return r;
	}
}
