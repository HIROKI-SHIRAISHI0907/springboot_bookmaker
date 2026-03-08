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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import dev.batch.repository.bm.BookCsvDataRepository;
import dev.common.config.PathConfig;
import dev.common.entity.DataEntity;
import dev.common.filemng.FileMngWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

class ExportCsvItTest {

	private static final Pattern ROOT_CSV = Pattern.compile("^\\d+\\.csv$");

	@TempDir
	Path tempDir;

	@Test
	void execute_S3直下の最大番号から連番でCSVが作られ_直下へ直接uploadされる() throws Exception {
		// ===== mocks =====
		S3Operator s3Operator = mock(S3Operator.class);
		PathConfig config = mock(PathConfig.class);
		ReaderCurrentCsvInfoBean bean = mock(ReaderCurrentCsvInfoBean.class);
		CsvArtifactHelper helper = mock(CsvArtifactHelper.class);
		BookCsvDataRepository repo = mock(BookCsvDataRepository.class);
		ManageLoggerComponent logger = mock(ManageLoggerComponent.class);

		// ===== target =====
		ExportCsvService target = new ExportCsvService();
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

		// ===== S3 download: 管理ファイルを「存在する形」で作る =====
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
		List<SeqWithKey> rows = new ArrayList<>();
		rows.add(seqRow("A", "B", 1));
		rows.add(seqRow("A", "B", 2));
		rows.add(seqRow("C", "D", 10));
		rows.add(seqRow("C", "D", 11));
		when(repo.findAllSeqsWithKey()).thenReturn(rows);

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
		List<String> rootKeys = List.of("1.csv", "2.csv", "seqList.txt");
		when(s3Operator.listKeys(eq(bucket), anyString())).thenAnswer(inv -> {
			String prefix = inv.getArgument(1, String.class);
			if (prefix == null || prefix.isBlank()) {
				return rootKeys;
			}
			return List.of(); // 直下upload方式では tmp の list は不要
		});

		// uploadFile：副作用なし（必要ならログも出せる）
		doAnswer(inv -> {
			System.out.println("UPLOAD bucket=" + inv.getArgument(0)
					+ " key=" + inv.getArgument(1)
					+ " local=" + inv.getArgument(2));
			return null;
		}).when(s3Operator).uploadFile(eq(bucket), anyString(), any(Path.class));

		// ===== FileMngWrapper の new を潰す（csvWriteは何もしない）=====
		try (MockedConstruction<FileMngWrapper> mocked = Mockito.mockConstruction(
				FileMngWrapper.class,
				(mock, ctx) -> doNothing().when(mock).csvWrite(anyString(), anyList()))) {
			target.execute();
		}

		// ===== Assertions =====

		// 1) uploadされたキーを全部捕まえる（直下）
		ArgumentCaptor<String> uploadKeyCap = ArgumentCaptor.forClass(String.class);
		verify(s3Operator, atLeastOnce()).uploadFile(eq(bucket), uploadKeyCap.capture(), any(Path.class));

		List<String> uploadedKeys = uploadKeyCap.getAllValues();

		// (a) 管理ファイルが直下にuploadされる
		assertTrue(uploadedKeys.contains("seqList.txt"));
		assertTrue(uploadedKeys.contains("data_team_list.txt"));

		// (b) CSVは max(=2)+1 から連番（グループ2つ → 3.csv, 4.csv）
		List<Integer> csvNos = uploadedKeys.stream()
				.filter(k -> ROOT_CSV.matcher(k).matches())
				.map(k -> Integer.parseInt(k.replace(".csv", "")))
				.sorted()
				.collect(Collectors.toList());
		assertEquals(List.of(3, 4), csvNos);

		// (c) tmp/ は使わない（= 余計な/tmpを作らない）
		assertTrue(uploadedKeys.stream().noneMatch(k -> k.startsWith("tmp/")));
		assertTrue(uploadedKeys.stream().noneMatch(k -> k.contains("/"))); // 直下キーのみ

		// 2) copy/delete は呼ばれない（直下PUT方式のキモ）
		verify(s3Operator, never()).copy(anyString(), anyString(), anyString(), anyString());
		verify(s3Operator, never()).delete(anyString(), anyString());
	}

	@Test
	void execute_S3直下の最大番号から連番でCSVが作られ_本番直下へ直接uploadされる() throws Exception {
	    // ===== mocks =====
	    S3Operator s3Operator = mock(S3Operator.class);
	    PathConfig config = mock(PathConfig.class);
	    ReaderCurrentCsvInfoBean bean = mock(ReaderCurrentCsvInfoBean.class);
	    CsvArtifactHelper helper = mock(CsvArtifactHelper.class);
	    BookCsvDataRepository repo = mock(BookCsvDataRepository.class);
	    ManageLoggerComponent logger = mock(ManageLoggerComponent.class);

	    // ===== target =====
	    ExportCsvService target = new ExportCsvService();
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

	    // ===== 既存CSV情報（空にして差分判定で全部作らせる）=====
	    doNothing().when(bean).init();
	    when(bean.getCsvInfo()).thenReturn(Collections.emptyMap());

	    // ===== DB（Repository） =====
	    List<SeqWithKey> rows = new ArrayList<>();
	    rows.add(seqRow("A", "B", 1));
	    rows.add(seqRow("A", "B", 2));
	    rows.add(seqRow("C", "D", 10));
	    rows.add(seqRow("C", "D", 11));
	    when(repo.findAllSeqsWithKey()).thenReturn(rows);

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
	    // ※ 本当にS3の実物で最大番号を見たいなら、ここも real 化が必要（後述）
	    List<String> rootKeys = List.of("1.csv", "2.csv", "seqList.txt");
	    when(s3Operator.listKeys(eq(bucket), anyString())).thenAnswer(inv -> {
	        String prefix = inv.getArgument(1, String.class);
	        if (prefix == null || prefix.isBlank()) return rootKeys;
	        return List.of();
	    });

	    // ===== upload はモックのまま =====
	    doAnswer(inv -> {
	        System.out.println("UPLOAD bucket=" + inv.getArgument(0)
	                + " key=" + inv.getArgument(1)
	                + " local=" + inv.getArgument(2));
	        return null;
	    }).when(s3Operator).uploadFile(eq(bucket), anyString(), any(Path.class));

	    // ===== downloadToFile だけ real =====
	    try (S3Client realS3 = S3Client.builder()
	            .region(Region.AP_NORTHEAST_1)
	            .credentialsProvider(DefaultCredentialsProvider.create())
	            .build()) {

	        doAnswer(inv -> {
	            String b = inv.getArgument(0, String.class);
	            String key = inv.getArgument(1, String.class);
	            Path out = inv.getArgument(2, Path.class);
	            Files.createDirectories(out.getParent());

	            if ("seqList.txt".equals(key) || "data_team_list.txt".equals(key)) {
	                try {
	                    realS3.getObject(
	                            GetObjectRequest.builder().bucket(b).key(key).build(),
	                            ResponseTransformer.toFile(out)
	                    );
	                    System.out.println("REAL-DOWNLOAD s3://" + b + "/" + key + " -> " + out);
	                    return null;

	                } catch (S3Exception e) {
	                    // 404だけ fallback、それ以外は落とす
	                    if (e.statusCode() != 404) throw e;
	                    System.out.println("NoSuchKey -> create dummy: s3://" + b + "/" + key);
	                }
	            }

	            // fallback
	            if ("seqList.txt".equals(key)) {
	                Files.writeString(out, "[]", StandardCharsets.UTF_8);
	            } else if ("data_team_list.txt".equals(key)) {
	                Files.writeString(out, "", StandardCharsets.UTF_8);
	            } else {
	                Files.writeString(out, "", StandardCharsets.UTF_8);
	            }
	            return null;

	        }).when(s3Operator).downloadToFile(eq(bucket), anyString(), any(Path.class));

	        // ===== FileMngWrapper new を潰す =====
	        try (MockedConstruction<FileMngWrapper> mocked = Mockito.mockConstruction(
	                FileMngWrapper.class,
	                (mock, ctx) -> doNothing().when(mock).csvWrite(anyString(), anyList()))) {
	            target.execute();
	        }
	    }

	    // ===== 実際に落ちたファイルを確認 =====
	    Path seq = tempDir.resolve("seqList.txt");
	    Path team = tempDir.resolve("data_team_list.txt");
	    System.out.println("=== seqList.txt ===");
	    System.out.println(Files.readString(seq, StandardCharsets.UTF_8));
	    System.out.println("=== data_team_list.txt (first 50 lines) ===");
	    Files.readAllLines(team, StandardCharsets.UTF_8).stream().limit(50).forEach(System.out::println);

	    // ===== Assertions（uploadは直下）=====
	    ArgumentCaptor<String> uploadKeyCap = ArgumentCaptor.forClass(String.class);
	    verify(s3Operator, atLeastOnce()).uploadFile(eq(bucket), uploadKeyCap.capture(), any(Path.class));

	    List<String> uploadedKeys = uploadKeyCap.getAllValues();
	    assertTrue(uploadedKeys.contains("seqList.txt"));
	    assertTrue(uploadedKeys.contains("data_team_list.txt"));

	    List<Integer> csvNos = uploadedKeys.stream()
	            .filter(k -> ROOT_CSV.matcher(k).matches())
	            .map(k -> Integer.parseInt(k.replace(".csv", "")))
	            .sorted()
	            .collect(Collectors.toList());
	    assertEquals(List.of(3, 4), csvNos);

	    assertTrue(uploadedKeys.stream().noneMatch(k -> k.startsWith("tmp/")));
	    assertTrue(uploadedKeys.stream().noneMatch(k -> k.contains("/")));

	    verify(s3Operator, never()).copy(anyString(), anyString(), anyString(), anyString());
	    verify(s3Operator, never()).delete(anyString(), anyString());
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
