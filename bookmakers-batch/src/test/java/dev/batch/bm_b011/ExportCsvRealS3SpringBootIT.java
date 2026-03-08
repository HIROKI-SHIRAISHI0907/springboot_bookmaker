package dev.batch.bm_b011;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class ExportCsvRealS3SpringBootIT {

  @TempDir
  static Path tempDir;

  static final String RUN_ID = UUID.randomUUID().toString();
  static final String BUCKET = "aws-s3-stat-csv";          // できればテスト用バケット推奨
  static final String PREFIX = "it/" + RUN_ID;             // ★これで隔離

  @Autowired
  ExportCsvService target;

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    // PathConfig が読むプロパティ名はプロジェクトに合わせて調整してください
    r.add("app.csv-folder", () -> tempDir.toString());
    r.add("process.s3.buckets.stats", () -> BUCKET);

    // ExportCsv に追加したプロパティ
    r.add("exportcsv.local-only", () -> "false");
    r.add("exportcsv.final-prefix", () -> PREFIX);
  }

  @Test
  void run_real_s3_real_spring() throws Exception {
    // 誤爆防止（CIでは実S3テストをデフォルト無効にするのが定石）
    Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("RUN_REAL_S3_IT")));

    target.execute();
  }
}
