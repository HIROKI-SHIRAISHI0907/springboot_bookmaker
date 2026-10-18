package dev.web.api.bm_a031;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.web.repository.bm.FileUploadStatusRepository;
import dev.web.util.CsvUtil;
import dev.web.util.CsvUtil.CsvFileException;
import dev.web.util.CsvUtil.CsvTable;
import dev.web.util.CsvUtil.UploadedZip;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CSVアップロード画面（受付とバリデーション）
 *
 * <p>シーズン・チーム・試合予定・リアルタイムの CSV（zip）を受け付ける。DB への反映はバッチサービスで行う。</p>
 *
 * <ol>
 *   <li>ログインユーザーを確かめる（未ログインは {@link UnauthorizedUploadException}。Controller で 401。記録も保管もしない）。</li>
 *   <li>状況管理（file_upload_status_manage・bm DB）に「判定前（PENDING）」で登録する。</li>
 *   <li>バリデーション（{@link CsvUtil#readUploadedZip} のファイル名・zip の中身・CSV の形、
 *       {@link CsvRowValidator} の行チェック。DB は見ない）。</li>
 *   <li>OK → zip をそのまま S3 の uploaded/ に置く。状態は判定前のまま（バッチサービスが DB チェック・反映をして
 *       success/ か error/ に移す）。</li>
 *   <li>NG → 「エラー詳細」列を足した {名前}_error.csv を {名前}_error.zip にして S3 の error/ に置き、
 *       状態を「異常終了（ERROR・phase=VALIDATION）」にする。どの行も反映しない。</li>
 *   <li>S3 に置けなかったとき → 「異常終了（phase=STORE）」。</li>
 * </ol>
 *
 * <p>ログインユーザーは JwtAuthenticationFilter が SecurityContext に入れたもの（JWT の sub＝メールアドレス）。
 *    画面から送られた値は使わない。</p>
 *
 * @author shiraishitoshio
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsvDataUploadService {

	public static final String MASTER_SEASON = "season";
	public static final String MASTER_TEAM = "team";
	public static final String MASTER_FUTURE = "future";
	public static final String MASTER_REALTIME = "realtime";

	/** zip・CSV の名前の接頭辞 */
	public static final String PREFIX_SEASON = "season_";
	public static final String PREFIX_TEAM = "team_";
	public static final String PREFIX_FUTURE = "future_";
	public static final String PREFIX_REALTIME = "realtime_";

	/** 状況管理に残すエラー行の上限（多すぎる場合は先頭だけ。error フォルダの CSV には全行書く） */
	private static final int MAX_SAVED_ERRORS = 1000;

	private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");
	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

	private static final String MSG_PENDING = "判定前です。（バリデーションを通りました。バッチサービスで反映します）";

	private final FileUploadStatusRepository statusRepository;

	private final CsvUploadArchiver archiver;

	private final ObjectMapper objectMapper;

	// =========================================================
	// 公開メソッド
	// =========================================================

	/** シーズンデータ（country_league_season_master） */
	public CsvUploadResponse uploadSeason(MultipartFile file) {
		return run(MASTER_SEASON, PREFIX_SEASON, file, SeasonCsvRow.class, CsvRowValidator::season);
	}

	/** チームデータ（country_league_master） */
	public CsvUploadResponse uploadTeam(MultipartFile file) {
		return run(MASTER_TEAM, PREFIX_TEAM, file, TeamCsvRow.class, CsvRowValidator::team);
	}

	/** 試合予定データ（future_master） */
	public CsvUploadResponse uploadFuture(MultipartFile file) {
		return run(MASTER_FUTURE, PREFIX_FUTURE, file, FutureCsvRow.class, CsvRowValidator::future);
	}

	/** リアルタイムデータ（static_data。非表示・再表示のみ） */
	public CsvUploadResponse uploadRealtime(MultipartFile file) {
		return run(MASTER_REALTIME, PREFIX_REALTIME, file, RealtimeCsvRow.class, CsvRowValidator::realtime);
	}

	/**
	 * アップロード用フォーマット（ヘッダー行だけの CSV を zip にしたもの）
	 * <p>名前は「接頭辞 + 日時（日本時間）」。例: season_20261018203000.zip の中に season_20261018203000.csv</p>
	 * @param masterName season / team / future / realtime
	 * @return zip。masterName が不正なら null
	 */
	public CsvUtil.CsvFile downloadFormat(String masterName) throws IOException {
		String ts = LocalDateTime.now(JST).format(FILE_TS);
		if (MASTER_SEASON.equals(masterName)) {
			return CsvUtil.createFormatZip(PREFIX_SEASON + ts, SeasonCsvRow.class);
		}
		if (MASTER_TEAM.equals(masterName)) {
			return CsvUtil.createFormatZip(PREFIX_TEAM + ts, TeamCsvRow.class);
		}
		if (MASTER_FUTURE.equals(masterName)) {
			return CsvUtil.createFormatZip(PREFIX_FUTURE + ts, FutureCsvRow.class);
		}
		if (MASTER_REALTIME.equals(masterName)) {
			return CsvUtil.createFormatZip(PREFIX_REALTIME + ts, RealtimeCsvRow.class);
		}
		return null;
	}

	// =========================================================
	// 受付の流れ
	// =========================================================

	/** 行の処理区分（エラー表示用） */
	private static String operationOf(Object row) {
		if (row instanceof SeasonCsvRow) {
			return ((SeasonCsvRow) row).getOperation();
		}
		if (row instanceof TeamCsvRow) {
			return ((TeamCsvRow) row).getOperation();
		}
		if (row instanceof FutureCsvRow) {
			return ((FutureCsvRow) row).getOperation();
		}
		if (row instanceof RealtimeCsvRow) {
			return ((RealtimeCsvRow) row).getOperation();
		}
		return "";
	}

	private <R> CsvUploadResponse run(String masterName, String prefix, MultipartFile file, Class<R> type,
			BiFunction<R, Set<String>, CsvRowValidator.Result> validator) {

		// 1. 誰がアップロードしたかを残すため、ログインしていなければ受け付けない
		String loginUser = currentLoginUser();
		if (loginUser == null) {
			throw new UnauthorizedUploadException("ログインしてからアップロードしてください。");
		}

		String fileName = file == null ? "" : CsvUtil.baseName(file.getOriginalFilename());
		byte[] bytes;
		try {
			bytes = file == null ? new byte[0] : file.getBytes();
		} catch (IOException e) {
			bytes = new byte[0];
		}

		// 2. 状況管理に「判定前」で登録（ID は S3 のキーにも使う。登録できなければ受け付けない）
		long uploadId = statusRepository.insertPending(masterName, fileName, csvNameOf(fileName), bytes.length, loginUser);

		CsvUploadResponse res = new CsvUploadResponse(masterName);
		res.setUploadId(uploadId);
		res.setFileName(fileName);

		// 3. バリデーション
		CsvTable table = null;
		List<String> fileErrors = new ArrayList<>();
		Map<Integer, List<String>> rowErrors = new LinkedHashMap<>();
		UploadedZip zip = null;
		try {
			zip = CsvUtil.readUploadedZip(fileName, bytes, prefix);
			table = zip.getTable();
			List<R> rows = CsvUtil.toBeans(table, type);
			res.setTotalCount(rows.size());
			if (rows.isEmpty()) {
				fileErrors.add("データ行がありません。");
			}
			Set<String> seen = new HashSet<>();
			for (int i = 0; i < rows.size(); i++) {
				R row = rows.get(i);
				CsvRowValidator.Result r = validator.apply(row, seen);
				if (r.hasError()) {
					rowErrors.put(i, r.getErrors());
					res.getErrors().add(new CsvUploadRowError(i + 2, nvl(operationOf(row)), r.getKey(), String.join("／", r.getErrors())));
				}
			}
		} catch (CsvFileException e) {
			fileErrors.add(e.getMessage());
			table = e.getTable();
			res.setTotalCount(table == null ? 0 : table.getRows().size());
		}

		if (!fileErrors.isEmpty()) {
			// ファイル全体のエラーは行番号 0 で先頭に
			res.getErrors().add(0, new CsvUploadRowError(0, "", "ファイル", String.join("／", fileErrors)));
		}

		// 4. NG → error/ に {名前}_error.zip
		if (res.hasError()) {
			res.setStatus(CsvUploadStatus.ERROR);
			res.setErrorPhase(CsvUploadStatus.PHASE_VALIDATION);
			res.setMessage("バリデーションエラーのため受け付けませんでした。（エラー " + res.getErrors().size()
					+ " 件）エラー内容を書き足したファイルを状況管理画面からダウンロードできます。");
			try {
				CsvUtil.CsvFile errorZip = CsvErrorFileBuilder.buildErrorZip(fileName, table, rowErrors, fileErrors);
				CsvUploadArchiver.Stored stored = archiver.putError(masterName, uploadId, errorZip, loginUser);
				res.setS3Bucket(stored.getBucket());
				res.setS3Key(stored.getKey());
			} catch (IOException | RuntimeException e) {
				log.error("エラーファイルを error/ に置けませんでした uploadId={}", uploadId, e);
				res.setMessage(res.getMessage() + "（エラーファイルの保管に失敗しました）");
			}
			statusRepository.markError(uploadId, res.getErrorPhase(), res.getMessage(), res.getTotalCount(),
					res.getErrors().size(), errorsJson(res), res.getS3Bucket(), res.getS3Key(), loginUser);
			return res;
		}

		// 5. OK → uploaded/ にそのまま置く（判定前のまま。バッチサービスに引き継ぐ）
		try {
			CsvUploadArchiver.Stored stored = archiver.putUploaded(masterName, uploadId, zip.getZipName(), zip.getZipBytes(), loginUser);
			res.setS3Bucket(stored.getBucket());
			res.setS3Key(stored.getKey());
		} catch (RuntimeException e) {
			log.error("zip を uploaded/ に置けませんでした uploadId={}", uploadId, e);
			res.setStatus(CsvUploadStatus.ERROR);
			res.setErrorPhase(CsvUploadStatus.PHASE_STORE);
			res.setMessage("ファイルの保管に失敗したため受け付けませんでした。時間をおいてもう一度アップロードしてください。");
			statusRepository.markError(uploadId, res.getErrorPhase(), res.getMessage(), res.getTotalCount(),
					0, "[]", null, null, loginUser);
			return res;
		}

		res.setStatus(CsvUploadStatus.PENDING);
		res.setMessage(MSG_PENDING);
		statusRepository.markUploaded(uploadId, res.getMessage(), res.getTotalCount(), res.getS3Bucket(), res.getS3Key(), loginUser);
		return res;
	}

	// =========================================================
	// 部品
	// =========================================================

	/** 状況管理に残すエラー行（JSON 配列） */
	private String errorsJson(CsvUploadResponse res) {
		List<CsvUploadRowError> errors = res.getErrors();
		List<CsvUploadRowError> saved = errors.size() > MAX_SAVED_ERRORS ? errors.subList(0, MAX_SAVED_ERRORS) : errors;
		try {
			return objectMapper.writeValueAsString(saved);
		} catch (Exception e) {
			log.warn("エラー行の JSON 変換に失敗", e);
			return "[]";
		}
	}

	/** zip 名から中の CSV 名（zip 以外なら空） */
	private static String csvNameOf(String zipName) {
		return zipName.toLowerCase().endsWith(".zip") ? zipName.substring(0, zipName.length() - 4) + ".csv" : "";
	}

	/**
	 * ログインユーザー（JWT の sub＝メールアドレス）
	 * @return 未ログイン（JWT が無い・不正・匿名）なら null
	 */
	private static String currentLoginUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
			return null;
		}
		String name = auth.getName();
		if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
			return null;
		}
		return name.strip();
	}

	private static String nvl(String s) {
		return s == null ? "" : s;
	}
}
