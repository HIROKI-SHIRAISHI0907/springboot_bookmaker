package dev.web.api.bm_w020;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.web.repository.bm.BookDataRepository;
import dev.web.util.CsvArtifactHelper;

@SpringBootTest(properties = {
		"exportcsv.local-only=false"
})
@ActiveProfiles("test")
class ExportCsvTest {

	@Autowired
	ExportCsv target;
	@Autowired
	BookDataRepository bookDataRepository;

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

	@Autowired
	@Qualifier("bmDataSource")
	DataSource bmDataSource;

	@TempDir
	Path csvOutDir;

	static final String BUCKET = "aws-s3-outputs-csv";

	List<String> uploadedTmpKeys;

	@BeforeEach
	void setup() throws Exception {
		ResourceDatabasePopulator pop = new ResourceDatabasePopulator();
		pop.addScript(new ClassPathResource("schema.sql"));
		pop.execute(bmDataSource);

		uploadedTmpKeys = new CopyOnWriteArrayList<>();

		when(config.getS3BucketsStats()).thenReturn(BUCKET);
		when(config.getCsvFolder()).thenReturn(csvOutDir.toString());

		doNothing().when(bean).init();
		when(bean.getCsvInfo()).thenReturn(Collections.emptyMap());

		var dummy = mock(CsvArtifactResource.class);
		when(helper.getData()).thenReturn(dummy);
		when(helper.csvCondition(anyList(), any())).thenReturn(true);
		when(helper.abnormalChk(anyList())).thenAnswer(inv -> inv.getArgument(0));

		when(s3Operator.buildKey(anyString(), anyString())).thenAnswer(inv -> {
			String prefix = inv.getArgument(0);
			String name = inv.getArgument(1);
			if (prefix == null || prefix.isBlank())
				return name;
			prefix = prefix.replaceAll("/+$", "");
			return prefix + "/" + name;
		});

		// finalPrefix の listKeys（最大CSV番号の計算用）
		when(s3Operator.listKeys(eq(BUCKET), argThat(p -> p == null || !p.startsWith("tmp/"))))
				.thenReturn(List.of("1.csv", "2.csv", "seqList.txt", "data_team_list.txt"));

		// tmpPrefix の listKeys（commit対象の列挙）
		when(s3Operator.listKeys(eq(BUCKET), argThat(p -> p != null && p.startsWith("tmp/"))))
				.thenAnswer(inv -> new ArrayList<>(uploadedTmpKeys));

		doAnswer(inv -> {
			String key = inv.getArgument(1);
			uploadedTmpKeys.add(key);
			return null;
		}).when(s3Operator).uploadFile(eq(BUCKET), anyString(), any(Path.class));

		doNothing().when(s3Operator).copy(eq(BUCKET), anyString(), eq(BUCKET), anyString());
		doNothing().when(s3Operator).delete(eq(BUCKET), anyString());

		seedDataTableFromLocalCsv();
	}

	private void seedDataTableFromLocalCsv() throws Exception {
		Path root = ExportCsvTestSupport.resolveRootDir("src/test/java/dev/web/api/bm_w020/data");

		List<Path> csvFiles;
		try (var s = Files.walk(root)) {
			csvFiles = s.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".csv"))
					.sorted()
					.collect(Collectors.toList());
		}

		assertEquals(2977, csvFiles.size());

		for (Path csv : csvFiles) {
			List<DataEntity> list = CsvImport.importCsv(
					csv.toString(),
					DataEntity.class,
					CsvHeaderMaps.DATA_ORIGIN,
					(e, ctx) -> {
						try {
							e.setFile(ctx.getCsvPath().getFileName().toString());
						} catch (Exception ignore) {
						}
					});

			for (DataEntity e : list) {
				if (e == null || e.getSeq() == null || e.getSeq().isBlank())
					continue;
				if (bookDataRepository.findBySeq(e.getSeq()).isPresent()) {
					System.out.println("update: " + e.getSeq() + ", 時間:" + e.getTimes() + ", ");
					bookDataRepository.updateBySeq(e);
				} else {
					System.out.println("insert: " + e.getSeq() + ", 時間:" + e.getTimes() + ", ");
					bookDataRepository.insert(e);
				}
			}
		}
	}

	@Test
	void 全成功なら_tmpPut後に_commitされる() throws Exception {
		target.execute();

		List<DataEntity> row = bookDataRepository.findAll();
		assertEquals(2048, row.size());

		// tmp PUT（CSV複数 + seqList + data_team_list）
		verify(s3Operator, atLeast(2)).uploadFile(eq(BUCKET), startsWith("tmp/"), any(Path.class));
		// commit(copy + delete)
		verify(s3Operator, atLeast(1)).copy(eq(BUCKET), startsWith("tmp/"), eq(BUCKET), anyString());
		verify(s3Operator, atLeast(1)).delete(eq(BUCKET), startsWith("tmp/"));
	}

	@Test
	void CSVのtmpPUTが失敗したら_commitしない() throws Exception {
		// 「tmp配下の *.csv だけ」失敗させる（seqList.txt / data_team_list.txt は成功させる）
		doAnswer(inv -> {
			String key = inv.getArgument(1);
			if (key.startsWith("tmp/") && key.endsWith(".csv")) {
				throw new RuntimeException("upload failed (csv)");
			}
			uploadedTmpKeys.add(key);
			return null;
		}).when(s3Operator).uploadFile(eq(BUCKET), anyString(), any(Path.class));

		target.execute();

		verify(s3Operator, never()).copy(eq(BUCKET), startsWith("tmp/"), eq(BUCKET), anyString());
		verify(s3Operator, never()).delete(eq(BUCKET), startsWith("tmp/"));
	}
}
