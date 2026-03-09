package dev.batch.bm_b011;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import dev.common.s3.S3Operator;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class ExportCsvRealS3SpringBootIT {

	private static final Pattern ROOT_CSV = Pattern.compile("^\\d+\\.csv$");

	@TempDir
	static Path tempDir;

	static final String RUN_ID = UUID.randomUUID().toString();
	static final String BUCKET = "aws-s3-stat-csv";
	static final String PREFIX = "it/" + RUN_ID; // ★S3隔離（直下を汚さない）

	@Autowired
	ExportCsvService target;

	// “本物Bean”に対して呼び出しだけ観測できる
	@SpyBean
	S3Operator s3Operator;

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("app.csv-folder", () -> tempDir.toString());
		r.add("process.s3.buckets.stats", () -> BUCKET);

		r.add("exportcsv.local-only", () -> "false");
		r.add("exportcsv.final-prefix", () -> PREFIX);

		// ★ログが出ない問題の対処（必要な範囲だけDEBUGに）
		r.add("logging.level.dev.batch.bm_b011", () -> "DEBUG");
		r.add("logging.level.dev.common.logger", () -> "DEBUG");
	}

	@Test
	void localOnly_false_upload_keys_are_correct(CapturedOutput output) throws Exception {
		// 誤爆防止（明示したときだけ実S3に投げる）
		Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("RUN_REAL_S3_IT")));

		target.execute();

		// ---- upload の実績を捕まえる（ログより確実）----
		ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
		verify(s3Operator, atLeastOnce()).uploadFile(eq(BUCKET), keyCap.capture(), any(Path.class));

		List<String> keys = keyCap.getAllValues();

		// 管理ファイルが prefix 配下に上がっている
		assertTrue(keys.contains(PREFIX + "/seqList.txt"), "seqList.txt が prefix 配下にuploadされること");
		assertTrue(keys.contains(PREFIX + "/data_team_list.txt"), "data_team_list.txt が prefix 配下にuploadされること");

		// CSVが prefix 配下に上がっている（番号は環境で変わるので “存在” を見る）
		List<Integer> csvNos = keys.stream()
				.map(k -> k.substring(k.lastIndexOf('/') + 1)) // ファイル名
				.filter(n -> ROOT_CSV.matcher(n).matches()) // N.csv
				.map(n -> Integer.parseInt(n.replace(".csv", "")))
				.sorted()
				.collect(Collectors.toList());

		assertFalse(csvNos.isEmpty(), "CSVが1つ以上uploadされること");

		// すべて prefix 配下になっている（直下に出ていないこと）
		assertTrue(keys.stream().allMatch(k -> k.startsWith(PREFIX + "/")),
				"全uploadが prefix 配下であること（直下汚染防止）");

		// ---- ログキャプチャ（出ているなら確認）----
		// ManageLoggerComponentの実装によっては標準出力に出ない場合もあるので “任意チェック” 推奨
		// 例: assertTrue(output.getOut().contains("UPLOAD(final)"));
		System.out.println("Captured log sample:\n" + output.getOut());
	}

	@Test
	void localOnly_false_real_s3_and_capture_log(org.springframework.boot.test.system.CapturedOutput output)
			throws Exception {
		Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("RUN_REAL_S3_IT")));

		target.execute();

		// 1) ログに "UPLOAD(final)" が出ているか（出ない場合は ManageLoggerComponent が DEBUG ログの可能性大）
		//    ※デバッグしやすいように失敗時ログを吐く
		String out = output.getOut();
		assertTrue(out.contains("UPLOAD(final)"),
				() -> "UPLOAD(final) がログに見つかりません。logLevel/ManageLoggerComponentの実装を確認。\n---- captured ----\n" + out);

		// 2) 実S3に上がったか（prefix配下をlistして検証）※こちらが最重要
		try (var s3 = software.amazon.awssdk.services.s3.S3Client.builder()
				.region(software.amazon.awssdk.regions.Region.AP_NORTHEAST_1)
				.credentialsProvider(software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.create())
				.build()) {

			var res = s3.listObjectsV2(b -> b.bucket(BUCKET).prefix(PREFIX + "/"));
			var keys = res.contents().stream().map(o -> o.key()).collect(Collectors.toList());

			assertTrue(keys.contains(PREFIX + "/seqList.txt"));
			assertTrue(keys.contains(PREFIX + "/data_team_list.txt"));
			assertTrue(keys.stream().anyMatch(k -> k.matches(Pattern.quote(PREFIX + "/") + "\\d+\\.csv")));
		}
	}
}
