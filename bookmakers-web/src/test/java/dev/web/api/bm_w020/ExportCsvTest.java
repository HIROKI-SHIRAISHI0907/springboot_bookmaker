package dev.web.api.bm_w020;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

import dev.common.config.PathConfig;
import dev.common.entity.DataEntity;
import dev.common.getinfo.GetOriginInfo;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.web.repository.bm.BookCsvDataRepository;
import dev.web.repository.bm.BookDataRepository;
import dev.web.util.CsvArtifactHelper;

@SpringBootTest
@ActiveProfiles("test")
class ExportCsvTest {

	@Autowired
	ExportCsv target;

	@Autowired
	BookCsvDataRepository bookCsvDataRepository;

	// ★ 追加：CSV起源データを data テーブルへ登録するため
	@Autowired
	GetOriginInfo getOriginInfo;
	@Autowired
	BookDataRepository bookDataRepository;

	// ★ Spring管理Beanを差し替えるので @MockBean にする
	@MockBean
	S3Operator s3Operator;
	@MockBean
	PathConfig config;
	@MockBean
	ReaderCurrentCsvInfoBean bean;
	@MockBean
	CsvArtifactHelper helper;
	@MockBean
	ManageLoggerComponent manageLoggerComponent;

	static final String BUCKET = "aws-s3-outputs-csv";
	static final String CSV_FOLDER = "/Users/shiraishitoshio/bookmaker/csv/";

	List<String> uploadedTmpKeys;

	// GetOriginInfo が downloadToFile で参照する「擬似S3 key → ローカル実体ファイル」
	Map<String, Path> originKeyToLocalFile;

	// GetOriginInfo が「S3から落としたファイルの保存先」に使うフォルダ
	@TempDir
	Path originOutDir;

	@Autowired
	@Qualifier("bmDataSource")
	DataSource bmDataSource;

	@BeforeEach
	void setup() throws Exception {
		// ★これを一番最初に入れる
		ResourceDatabasePopulator pop = new ResourceDatabasePopulator();
		pop.addScript(new ClassPathResource("schema.sql"));
		pop.execute(bmDataSource);

		uploadedTmpKeys = new CopyOnWriteArrayList<>();

		// ===== PathConfig mock（ExportCsv側）=====
		when(config.getS3BucketsStats()).thenReturn(BUCKET);
		when(config.getCsvFolder()).thenReturn(CSV_FOLDER);
		Files.createDirectories(Paths.get(CSV_FOLDER));

		// ===== ReaderCurrentCsvInfoBean mock =====
		doNothing().when(bean).init();
		when(bean.getCsvInfo()).thenReturn(Collections.emptyMap());

		// ===== helper mock（条件で落とさない）=====
		var dummyResource = mock(CsvArtifactResource.class);
		when(helper.getData()).thenReturn(dummyResource);
		when(helper.csvCondition(anyList(), any())).thenReturn(true);
		when(helper.abnormalChk(anyList())).thenAnswer(inv -> inv.getArgument(0));

		// ===== s3Operator mock（共通）=====
		when(s3Operator.buildKey(anyString(), anyString())).thenAnswer(inv -> {
			String prefix = inv.getArgument(0);
			String name = inv.getArgument(1);
			if (prefix == null || prefix.isBlank())
				return name;
			prefix = prefix.replaceAll("/+$", "");
			return prefix + "/" + name;
		});

		when(s3Operator.listKeys(eq(BUCKET), anyString())).thenAnswer(inv -> {
			String prefix = inv.getArgument(1);
			if (prefix != null && prefix.startsWith("tmp/")) {
				return new ArrayList<>(uploadedTmpKeys);
			}
			return List.of("1.csv", "2.csv", "seqList.txt", "data_team_list.txt");
		});

		doAnswer(inv -> {
			String key = inv.getArgument(1);
			uploadedTmpKeys.add(key);
			return null;
		}).when(s3Operator).uploadFile(eq(BUCKET), anyString(), any(Path.class));

		doNothing().when(s3Operator).copy(eq(BUCKET), anyString(), eq(BUCKET), anyString());
		doNothing().when(s3Operator).delete(eq(BUCKET), anyString());

		// ===== ★追加：GetOriginInfo を使って CSV→DataEntity→data登録 =====
		seedDataTableFromLocalCsv();

		System.out.println("findAllSeqsWithKey.size=" +
		bookCsvDataRepository.findAllSeqsWithKey().size());
	}

	private void seedDataTableFromLocalCsv() throws Exception {

		Path root = resolveRootDir("src/test/java/dev/web/api/bm_w020/data");

		List<Path> csvFiles;
		try (var s = Files.walk(root)) {
			csvFiles = s.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".csv"))
					.sorted()
					.collect(Collectors.toList());
		}

		// 2) 1ファイルずつ CsvImport で DataEntity に変換 → upsert
		for (Path csv : csvFiles) {
			List<DataEntity> list = CsvImport.importCsv(
					csv.toString(),
					DataEntity.class,
					CsvHeaderMaps.DATA_ORIGIN,
					(e, ctx) -> {
						// 追跡用：元CSV名を保持（必要なら）
						try {
							e.setFile(ctx.getCsvPath().getFileName().toString());
						} catch (Exception ignore) {
						}
					});

			for (DataEntity e : list) {
				upsertBySeq(e);
			}
		}
	}

	private void upsertBySeq(DataEntity e) {
		if (e == null || e.getSeq() == null || e.getSeq().isBlank())
			return;

		// 既にあれば update、なければ insert
		if (bookDataRepository.findBySeq(e.getSeq()).isPresent()) {
			bookDataRepository.updateBySeq(e);
		} else {
			bookDataRepository.insert(e);
		}
	}

	private static Path resolveRootDir(String relative) {
		// relative は "src/test/java/dev/web/api/bm_w020/data" を想定
		Path rel = Paths.get(relative).normalize();

		Path wd = Paths.get(System.getProperty("user.dir"))
				.toAbsolutePath()
				.normalize();

		// デバッグ（必要なら）
		System.out.println("[CSV] user.dir = " + wd);

		// 1) まず user.dir 直下で探す
		Path p = wd.resolve(rel).normalize();
		if (Files.isDirectory(p)) {
			System.out.println("[CSV] found = " + p);
			return p;
		}

		// 2) 親へ遡りながら探す（例：multi-module / IDE起点ずれ対策）
		Path cur = wd;
		for (int i = 0; i < 15 && cur != null; i++) {
			Path candidate = cur.resolve(rel).normalize();
			if (Files.isDirectory(candidate)) {
				System.out.println("[CSV] found = " + candidate);
				return candidate;
			}
			cur = cur.getParent();
		}

		throw new IllegalStateException(
				"CSVフォルダが存在しません: " + rel + "\n"
						+ "user.dir=" + wd + "\n"
						+ "ヒント: テストを実行しているプロジェクト配下に、"
						+ rel + " が実在する必要があります。");
	}

	@Test
	void 初回実行_全成功なら_tmpPut後に_commitされる() throws Exception {
		System.out.println("groups=" + bookCsvDataRepository.findAllSeqsWithKey().size());
		System.out.println("maxOnS3=" + /* ExportCsv#getMaxCsvNoFromS3 相当を呼べないので */ "see s3 listKeys mock");
		System.out.println("csvInfo.size=" + bean.getCsvInfo().size());

		target.execute();

		verify(s3Operator, atLeast(2)).uploadFile(eq(BUCKET), startsWith("tmp/"), any(Path.class));
		verify(s3Operator, atLeast(1)).copy(eq(BUCKET), startsWith("tmp/"), eq(BUCKET), anyString());
		verify(s3Operator, atLeast(1)).delete(eq(BUCKET), startsWith("tmp/"));
	}

	@Test
	void CSVのtmpPUTが1件でも失敗したら_commitしない() throws Exception {
		doAnswer(inv -> {
			String key = inv.getArgument(1);
			if (key.endsWith("4.csv"))
				throw new RuntimeException("upload failed");
			uploadedTmpKeys.add(key);
			return null;
		}).when(s3Operator).uploadFile(eq(BUCKET), anyString(), any(Path.class));

		target.execute();

		verify(s3Operator, never()).copy(eq(BUCKET), startsWith("tmp/"), eq(BUCKET), anyString());
		verify(s3Operator, never()).delete(eq(BUCKET), startsWith("tmp/"));
	}
}
