package dev.common.getinfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * バックアップ用バケット（aws-s3-outputs-csv-bk）の {@code <mid>.zip} の中の並び（2026-11-18 追加）
 *
 * <pre>
 * &lt;mid&gt;.zip
 *   realData/yyyy-MM-dd/mid=XXXX/seq=000001_yyyyMMddTHHmmss+0900.csv   … リアルタイムデータ（PutOriginBackUpInfo）
 *   detailData/match_detail_match.csv など 9 テーブル                      … 統計詳細データ（B013 シーズン終了時）
 * </pre>
 *
 * <p>以前はリアルタイムデータを zip の直下（yyyy-MM-dd/...）に置いていた。読むときはどちらも読み
 *    （{@link #toRealDataKey}）、zip を書き直すときに realData/ の下へ移す（{@link #moveLegacyRealData}）。</p>
 */
public final class BackupZipLayout {

	/** リアルタイムデータのフォルダ */
	public static final String REAL_DATA_DIR = "realData";

	/** 統計詳細データのフォルダ */
	public static final String DETAIL_DATA_DIR = "detailData";

	/** 以前の置き方（zip 直下の日付フォルダ） */
	private static final Pattern DATE_DIR = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

	private BackupZipLayout() {
	}

	/**
	 * zip 内の相対パスを、リアルタイムデータのキー（yyyy-MM-dd/mid=.../seq=....csv）にする。
	 * realData/ の下ならその先、直下（以前の置き方）ならそのまま。それ以外（detailData/ など）は null
	 */
	public static String toRealDataKey(String relative) {
		if (relative == null) {
			return null;
		}
		String r = relative.replace('\\', '/');
		if (r.startsWith(REAL_DATA_DIR + "/")) {
			return r.substring(REAL_DATA_DIR.length() + 1);
		}
		if (r.startsWith(DETAIL_DATA_DIR + "/")) {
			return null;
		}
		return r;
	}

	/**
	 * 展開したフォルダの直下にある日付フォルダ（以前の置き方）を realData/ の下へ移す
	 * @return 移したフォルダの数
	 */
	public static int moveLegacyRealData(Path extractDir) throws IOException {
		if (extractDir == null || !Files.isDirectory(extractDir)) {
			return 0;
		}
		List<Path> legacy = new ArrayList<>();
		try (Stream<Path> s = Files.list(extractDir)) {
			s.filter(Files::isDirectory)
					.filter(p -> DATE_DIR.matcher(p.getFileName().toString()).matches())
					.forEach(legacy::add);
		}
		if (legacy.isEmpty()) {
			return 0;
		}
		Path realDir = extractDir.resolve(REAL_DATA_DIR);
		Files.createDirectories(realDir);
		for (Path dateDir : legacy) {
			Path dest = realDir.resolve(dateDir.getFileName().toString());
			if (!Files.exists(dest)) {
				Files.move(dateDir, dest);
				continue;
			}
			// 同じ日付のフォルダが realData/ 側にもあれば、ファイルごとに移す（realData/ 側を優先）
			try (Stream<Path> walk = Files.walk(dateDir)) {
				for (Path f : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
					Path to = dest.resolve(dateDir.relativize(f).toString());
					if (!Files.exists(to)) {
						Files.createDirectories(to.getParent());
						Files.move(f, to, StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
			deleteRecursively(dateDir);
		}
		return legacy.size();
	}

	/** フォルダを中身ごと消す */
	public static void deleteRecursively(Path dir) throws IOException {
		if (dir == null || !Files.exists(dir)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(dir)) {
			List<Path> all = new ArrayList<>();
			walk.forEach(all::add);
			for (int i = all.size() - 1; i >= 0; i--) {
				Files.deleteIfExists(all.get(i));
			}
		}
	}
}
