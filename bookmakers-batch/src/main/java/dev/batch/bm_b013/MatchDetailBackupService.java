package dev.batch.bm_b013;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.batch.repository.bm.MatchDetailBatchRepository;
import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.getinfo.BackupZipLayout;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.zip.ZipArchiveHandler;

/**
 * B013 シーズン終了: 統計詳細データ（match_detail_* の 9 テーブル）を削除する前に、
 * 試合ごとの {@code <mid>.zip}（aws-s3-outputs-csv-bk。リアルタイムデータのバックアップと同じ zip）に入れる（2026-11-18 追加）
 *
 * <pre>
 * &lt;mid&gt;.zip
 *   realData/yyyy-MM-dd/mid=XXXX/seq=....csv   … リアルタイムデータ（PutOriginBackUpInfo。以前の直下の分はここへ移す）
 *   detailData/match_detail_match.csv など 9 つ … この試合の統計詳細データ（UTF-8 BOM 付き・見出しは列名・NULL は空欄）
 * </pre>
 *
 * <p>zip が既にあれば中身を残したまま detailData/ を入れ直し、無ければ detailData/ だけの zip を作る。</p>
 * <p>対象の試合は {@link MatchDetailBatchRepository#deleteByLeague} と同じ決め方。
 *    1 試合でも置けなかったら例外にする（EachTableTransaction がロールバックし、統計詳細データは消さない）。</p>
 *
 * @author shiraishitoshio
 */
@Component
public class MatchDetailBackupService {

	private static final String PROJECT_NAME = MatchDetailBackupService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();
	private static final String CLASS_NAME = MatchDetailBackupService.class.getName();

	/** 9 テーブル（${} で SQL に埋め込むので、ここにあるものだけ使う） */
	static final List<String> TABLES = List.of(
			"match_detail_match",
			"match_detail_summary",
			"match_detail_goal",
			"match_detail_momentum",
			"match_detail_momentum_incident",
			"match_detail_formation",
			"match_detail_lineup",
			"match_detail_missing",
			"match_detail_member_stat");

	private static final String ZIP_EXTENSION = ".zip";
	private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	@Autowired
	private MatchDetailBatchRepository matchDetailBatchRepository;

	@Autowired
	private S3Operator s3Operator;

	@Autowired
	private ZipArchiveHandler zipArchiveHandler;

	@Autowired
	private PathConfig config;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 国・リーグの統計詳細データを、試合ごとの {@code <mid>.zip} の detailData/ に入れる
	 *
	 * @return バックアップした試合数
	 * @throws IOException CSV・zip を作れない・S3 に置けないとき
	 */
	public int backup(String colon, String colonLike, String slash, String slashLike) throws IOException {
		final String METHOD_NAME = "backup";
		List<String> mids = matchDetailBatchRepository.findMatchIdsByLeague(colon, colonLike, slash, slashLike);
		if (mids.isEmpty()) {
			log(METHOD_NAME, "match_detail_backup: " + colon + " 対象の試合なし（バックアップしません）");
			return 0;
		}

		String bucket = config.getS3BucketsOutputsBackUp();
		Path work = Files.createTempDirectory("match-detail-backup-");
		try {
			// 1) テーブルごとに行を試合ID順に読み、試合ごとのフォルダ（work/csv/<mid>/）に CSV を書く
			Path csvRoot = work.resolve("csv");
			StringBuilder counts = new StringBuilder();
			for (String table : TABLES) {
				long n = writeTableCsvByMatch(table, csvRoot, mids, colon, colonLike, slash, slashLike);
				counts.append(counts.length() == 0 ? "" : ", ").append(table).append("=").append(n);
			}

			// 2) 試合ごとに <mid>.zip（あれば展開）の detailData/ を入れ直して置く
			for (String mid : mids) {
				putIntoMidZip(bucket, mid, csvRoot.resolve(mid), work.resolve("zip"));
			}
			log(METHOD_NAME, "match_detail_backup: " + colon + " 試合数=" + mids.size()
					+ " → s3://" + bucket + "/<mid>.zip の " + BackupZipLayout.DETAIL_DATA_DIR + "/（" + counts + "）");
			return mids.size();
		} finally {
			try {
				BackupZipLayout.deleteRecursively(work);
			} catch (IOException ignore) {
				// 一時フォルダは消せなくても続ける
			}
		}
	}

	/**
	 * 1 テーブル分を、試合ごとの CSV（csvRoot/&lt;mid&gt;/&lt;table&gt;.csv）に書く。行が無い試合も見出しだけの CSV を作る
	 * @return 書いた行数
	 */
	private long writeTableCsvByMatch(String table, Path csvRoot, List<String> mids,
			String colon, String colonLike, String slash, String slashLike) throws IOException {
		List<String> columns = matchDetailBatchRepository.findColumns(table);
		String[] header = columns.toArray(new String[0]);
		long[] n = { 0 };
		IOException[] err = { null };
		String[] curMid = { null };
		OutputStream[] cur = { null };

		matchDetailBatchRepository.selectByLeague(table, colon, colonLike, slash, slashLike, ctx -> {
			if (err[0] != null) {
				return;
			}
			try {
				LinkedHashMap<String, Object> row = ctx.getResultObject();
				String mid = String.valueOf(row.get("match_id"));
				if (!mid.equals(curMid[0])) {
					closeQuietly(cur[0]);
					cur[0] = openCsv(csvRoot.resolve(mid).resolve(table + ".csv"), header);
					curMid[0] = mid;
				}
				String[] values = new String[columns.size()];
				for (int i = 0; i < columns.size(); i++) {
					Object v = row.get(columns.get(i));
					values[i] = v == null ? "" : String.valueOf(v);
				}
				writeLine(cur[0], values);
				n[0]++;
			} catch (IOException e) {
				err[0] = e;
			}
		});
		closeQuietly(cur[0]);
		if (err[0] != null) {
			throw err[0];
		}
		// 行が無かった試合は見出しだけ
		for (String mid : mids) {
			Path f = csvRoot.resolve(mid).resolve(table + ".csv");
			if (!Files.exists(f)) {
				closeQuietly(openCsv(f, header));
			}
		}
		return n[0];
	}

	/** S3 の &lt;mid&gt;.zip に detailData/ を入れ直して置く（無ければ新しく作る） */
	private void putIntoMidZip(String bucket, String mid, Path midCsvDir, Path zipWork) throws IOException {
		String zipKey = mid + ZIP_EXTENSION;
		Path dir = zipWork.resolve(mid);
		Path extractDir = dir.resolve("extract");
		Files.createDirectories(extractDir);
		try {
			if (s3Operator.existsOnS3(bucket, zipKey)) {
				Path downloaded = dir.resolve("download.zip");
				s3Operator.downloadToFile(bucket, zipKey, downloaded);
				zipArchiveHandler.decompress(downloaded, extractDir);
				BackupZipLayout.moveLegacyRealData(extractDir);
			}
			Path detailDir = extractDir.resolve(BackupZipLayout.DETAIL_DATA_DIR);
			BackupZipLayout.deleteRecursively(detailDir);
			Files.createDirectories(detailDir);
			try (java.util.stream.Stream<Path> files = Files.list(midCsvDir)) {
				for (Path f : (Iterable<Path>) files::iterator) {
					Files.copy(f, detailDir.resolve(f.getFileName().toString()));
				}
			}
			Path newZip = dir.resolve("new.zip");
			zipArchiveHandler.compress(extractDir, newZip);
			s3Operator.uploadFile(bucket, zipKey, newZip);
		} finally {
			BackupZipLayout.deleteRecursively(dir);
		}
	}

	private static OutputStream openCsv(Path file, String[] header) throws IOException {
		Files.createDirectories(file.getParent());
		OutputStream os = new BufferedOutputStream(Files.newOutputStream(file));
		os.write(UTF8_BOM);
		writeLine(os, header);
		return os;
	}

	private static void closeQuietly(OutputStream os) {
		if (os == null) {
			return;
		}
		try {
			os.close();
		} catch (IOException ignore) {
			// 書き終わっているので続ける
		}
	}

	private void log(String method, String msg) {
		manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, method, MessageCdConst.MCD00099I_LOG, msg);
	}

	/** CSV 1 行（必要なら "" で囲む・CRLF） */
	private static void writeLine(OutputStream os, String[] values) throws IOException {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			String v = values[i] == null ? "" : values[i];
			if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
				v = "\"" + v.replace("\"", "\"\"") + "\"";
			}
			sb.append(v);
		}
		sb.append("\r\n");
		os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
	}
}
