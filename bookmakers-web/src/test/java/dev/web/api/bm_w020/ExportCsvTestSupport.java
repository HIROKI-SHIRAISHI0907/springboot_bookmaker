package dev.web.api.bm_w020;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class ExportCsvTestSupport {

  private ExportCsvTestSupport() {}

  static Path resolveRootDir(String relative) {
    Path rel = Paths.get(relative).normalize();
    Path wd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();

    System.out.println("[CSV] user.dir = " + wd);

    Path p = wd.resolve(rel).normalize();
    if (Files.isDirectory(p)) {
      System.out.println("[CSV] found = " + p);
      return p;
    }

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
        "CSVフォルダが存在しません: " + rel + "\n" +
        "user.dir=" + wd + "\n" +
        "ヒント: テストを実行しているプロジェクト配下に " + rel + " が必要です。"
    );
  }
}
