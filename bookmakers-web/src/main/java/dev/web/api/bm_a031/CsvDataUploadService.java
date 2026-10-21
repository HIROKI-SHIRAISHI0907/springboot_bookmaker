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

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.constant.CsvUploadConst.FileName;
import dev.common.constant.CsvUploadConst.Limit;
import dev.common.constant.CsvUploadConst.Master;
import dev.common.constant.CsvUploadConst.Message;
import dev.common.constant.CsvUploadConst.Phase;
import dev.common.constant.CsvUploadConst.Prefix;
import dev.common.constant.CsvUploadConst.Status;
import dev.web.jwt.JwtCurrentUserService;
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
 *   <li>ログインユーザーを確かめる（{@link JwtCurrentUserService} で Authorization ヘッダーの JWT を検証し、
 *       users に存在することまで確認。NG は {@link UnauthorizedUploadException}。Controller で 401。記録も保管もしない）。</li>
 *   <li>状況管理（file_upload_status_manage・bm DB）に「受付（RECEIVED）」で登録する。</li>
 *   <li>バリデーション（{@link CsvUtil#readUploadedZip} のファイル名・zip の中身・CSV の形、
 *       {@link CsvRowValidator} の行チェック。DB は見ない）。</li>
 *   <li>OK → zip をそのまま S3 の uploaded/ に置き、状態を「判定前（PENDING）」にする
 *       （バッチサービスが DB チェック・反映をして success/ か error/ に移す）。</li>
 *   <li>NG → 「エラー詳細」列を足した {名前}_error.csv を {名前}_error.zip にして S3 の error/ に置き、
 *       状態を「異常終了（ERROR・phase=VALIDATION）」にする。どの行も反映しない。</li>
 *   <li>S3 に置けなかったとき → 「異常終了（ERROR・phase=STORE）」。</li>
 * </ol>
 *
 * <p>ログインユーザーは JWT の sub（メールアドレス）。画面から送られたユーザー名などの値は使わない。</p>
 * <p>zip 名は「接頭辞_〇〇.zip」。Mac の「圧縮」で付く「接頭辞_〇〇.csv.zip」も同じ扱い（中の CSV は 接頭辞_〇〇.csv）。</p>
 *
 * @author shiraishitoshio
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsvDataUploadService {

	private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern(FileName.FORMAT_TIMESTAMP_PATTERN);
	private static final ZoneId JST = ZoneId.of(FileName.ZONE_ID);

	private final FileUploadStatusRepository statusRepository;

	private final CsvUploadArchiver archiver;

	private final ObjectMapper objectMapper;

	private final JwtCurrentUserService jwtCurrentUserService;

	// =========================================================
	// 公開メソッド
	// =========================================================

	/**
	 * シーズンデータ（country_league_season_master）
	 * @param file アップロードされた zip
	 * @param authorization Authorization ヘッダー（Bearer {JWT}）
	 */
	public CsvUploadResponse uploadSeason(MultipartFile file, String authorization) {
		return run(Master.SEASON, Prefix.SEASON, file, authorization, SeasonCsvRow.class, CsvRowValidator::season);
	}

	/** チームデータ（country_league_master） */
	public CsvUploadResponse uploadTeam(MultipartFile file, String authorization) {
		return run(Master.TEAM, Prefix.TEAM, file, authorization, TeamCsvRow.class, CsvRowValidator::team);
	}

	/** 試合予定データ（future_master） */
	public CsvUploadResponse uploadFuture(MultipartFile file, String authorization) {
		return run(Master.FUTURE, Prefix.FUTURE, file, authorization, FutureCsvRow.class, CsvRowValidator::future);
	}

	/** リアルタイムデータ（static_data。非表示・再表示のみ） */
	public CsvUploadResponse uploadRealtime(MultipartFile file, String authorization) {
		return run(Master.REALTIME, Prefix.REALTIME, file, authorization, RealtimeCsvRow.class, CsvRowValidator::realtime);
	}

	/**
	 * データカテゴリ統一（static_data.data_category・future_master.game_team_category。2026-10-21 追加）
	 * <p>処理区分 2 の行だけ反映する。2 の行が 1 つも無ければファイルのエラー。</p>
	 */
	public CsvUploadResponse uploadDataCategory(MultipartFile file, String authorization) {
		return run(Master.DATA_CATEGORY, Prefix.DATA_CATEGORY, file, authorization, DataCategoryCsvRow.class,
				CsvRowValidator::dataCategory, rows -> {
					for (DataCategoryCsvRow r : rows) {
						if (!CsvRowValidator.isSkippedDataCategoryRow(r)) {
							return null;
						}
					}
					return "反映する行がありません。（処理区分を 2 にした行だけ反映します）";
				});
	}

	/**
	 * アップロード用フォーマット（ヘッダー行だけの CSV を zip にしたもの）
	 * <p>名前は「接頭辞 + 日時（日本時間）」。例: season_20261018203000.zip の中に season_20261018203000.csv</p>
	 * @param masterName season / team / future / realtime
	 * @return zip。masterName が不正なら null
	 */
	public CsvUtil.CsvFile downloadFormat(String masterName) throws IOException {
		String ts = LocalDateTime.now(JST).format(FILE_TS);
		if (Master.SEASON.equals(masterName)) {
			return CsvUtil.createFormatZip(Prefix.SEASON + ts, SeasonCsvRow.class);
		}
		if (Master.TEAM.equals(masterName)) {
			return CsvUtil.createFormatZip(Prefix.TEAM + ts, TeamCsvRow.class);
		}
		if (Master.FUTURE.equals(masterName)) {
			return CsvUtil.createFormatZip(Prefix.FUTURE + ts, FutureCsvRow.class);
		}
		if (Master.REALTIME.equals(masterName)) {
			return CsvUtil.createFormatZip(Prefix.REALTIME + ts, RealtimeCsvRow.class);
		}
		if (Master.DATA_CATEGORY.equals(masterName)) {
			return createDataCategoryFormat(new DataCategoryFormatRequest());
		}
		return null;
	}

	/** データカテゴリ統一のフォーマットに付ける参考の列（アップロードでは読まない） */
	private static final List<String> DATA_CATEGORY_REFERENCE_COLUMNS = List.of(
			"件数（参考）", "国リーグ判定（参考）", "国リーグ候補（参考）", "形式（参考）");

	/**
	 * データカテゴリ統一のフォーマット（画面に表示中のデータを入れたもの。2026-10-21 追加）
	 * <p>名前は「dataCategory_ + 日時（日本時間）」。例: dataCategory_20261021203000.zip の中に dataCategory_20261021203000.csv</p>
	 * <p>列: 処理区分・ホームチーム・アウェーチーム・現在のデータカテゴリ・新しいデータカテゴリ ＋ 参考の列。
	 *    画面で新しい値を入れていた行は処理区分 2・新しい値入り、それ以外は空欄。</p>
	 *
	 * @param req 表示中の行（空ならヘッダーだけ）
	 */
	public CsvUtil.CsvFile createDataCategoryFormat(DataCategoryFormatRequest req) throws IOException {
		String baseName = Prefix.DATA_CATEGORY + LocalDateTime.now(JST).format(FILE_TS);
		List<String> header = new ArrayList<>(CsvUtil.headerOf(DataCategoryCsvRow.class));
		header.addAll(DATA_CATEGORY_REFERENCE_COLUMNS);
		List<String[]> lines = new ArrayList<>();
		if (req != null && req.getRows() != null) {
			for (DataCategoryFormatRequest.Row r : req.getRows()) {
				if (r == null) {
					continue;
				}
				String current = nvl(r.getCurrentDataCategory()).strip();
				String next = nvl(r.getNewDataCategory()).strip();
				// 新しい値が入っていて今と違う行だけ処理区分 2（画面の指定があればそれを使う）
				String op = r.getOperation() != null ? r.getOperation().strip()
						: (!next.isEmpty() && !next.equals(current) ? CsvOperation.UPDATE.getCode() : "");
				lines.add(new String[] {
						op,
						nvl(r.getHomeTeamName()).strip(),
						nvl(r.getAwayTeamName()).strip(),
						current,
						next,
						r.getCount() == null ? "" : String.valueOf(r.getCount()),
						nvl(r.getLeagueCheck()),
						r.getLeagueCandidates() == null ? "" : String.join(" / ", r.getLeagueCandidates()),
						nvl(r.getFormat()) });
			}
		}
		return new CsvUtil.CsvFile(baseName + FileName.ZIP_EXT,
				CsvUtil.zipSingle(baseName + FileName.CSV_EXT, CsvUtil.writeCsv(header, lines)));
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
		if (row instanceof DataCategoryCsvRow) {
			return ((DataCategoryCsvRow) row).getOperation();
		}
		return "";
	}

	private <R> CsvUploadResponse run(String masterName, String prefix, MultipartFile file, String authorization,
			Class<R> type, BiFunction<R, Set<String>, CsvRowValidator.Result> validator) {
		return run(masterName, prefix, file, authorization, type, validator, null);
	}

	/**
	 * @param fileCheck 行全体を見たファイルのチェック（エラーならメッセージ、OK なら null。不要なら null）
	 */
	private <R> CsvUploadResponse run(String masterName, String prefix, MultipartFile file, String authorization,
			Class<R> type, BiFunction<R, Set<String>, CsvRowValidator.Result> validator,
			java.util.function.Function<List<R>, String> fileCheck) {

		// 1. 誰がアップロードしたかを残すため、ログインしていなければ受け付けない（NG は例外 → Controller で 401）
		String loginUser = currentLoginUser(authorization);

		String fileName = file == null ? "" : CsvUtil.baseName(file.getOriginalFilename());
		byte[] bytes;
		try {
			bytes = file == null ? new byte[0] : file.getBytes();
		} catch (IOException e) {
			log.warn("アップロードファイルを読めませんでした fileName={}", fileName, e);
			bytes = new byte[0];
		}

		// 2. 状況管理に「受付」で登録（ID は S3 のキーにも使う。登録できなければ受け付けない）
		long uploadId = statusRepository.insertReceived(masterName, fileName, csvNameOf(fileName), bytes.length, loginUser);

		CsvUploadResponse res = new CsvUploadResponse(masterName);
		res.setUploadId(uploadId);
		res.setFileName(fileName);
		res.setStatus(Status.RECEIVED);

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
				fileErrors.add(Message.NO_DATA);
			} else if (fileCheck != null) {
				String m = fileCheck.apply(rows);
				if (m != null) {
					fileErrors.add(m);
				}
			}
			Set<String> seen = new HashSet<>();
			for (int i = 0; i < rows.size(); i++) {
				R row = rows.get(i);
				CsvRowValidator.Result r = validator.apply(row, seen);
				if (r.hasError()) {
					rowErrors.put(i, r.getErrors());
					res.getErrors().add(new CsvUploadRowError(i + 2, nvl(operationOf(row)), r.getKey(),
							String.join(Message.ERROR_DELIMITER, r.getErrors())));
				}
			}
		} catch (CsvFileException e) {
			fileErrors.add(e.getMessage());
			table = e.getTable();
			res.setTotalCount(table == null ? 0 : table.getRows().size());
		}

		if (!fileErrors.isEmpty()) {
			// ファイル全体のエラーは行番号 0 で先頭に
			res.getErrors().add(0, new CsvUploadRowError(0, "", Message.FILE_ERROR_KEY,
					String.join(Message.ERROR_DELIMITER, fileErrors)));
		}

		// 4. NG → error/ に {名前}_error.zip
		if (res.hasError()) {
			res.setStatus(Status.ERROR);
			res.setErrorPhase(Phase.VALIDATION);
			res.setMessage(String.format(Message.VALIDATION_ERROR, res.getErrors().size()));
			try {
				CsvUtil.CsvFile errorZip = CsvErrorFileBuilder.buildErrorZip(fileName, table, rowErrors, fileErrors);
				CsvUploadArchiver.Stored stored = archiver.putError(masterName, uploadId, errorZip, loginUser);
				res.setS3Bucket(stored.getBucket());
				res.setS3Key(stored.getKey());
			} catch (IOException | RuntimeException e) {
				log.error("エラーファイルを error/ に置けませんでした uploadId={}", uploadId, e);
				res.setMessage(res.getMessage() + Message.ERROR_FILE_STORE_FAILED);
			}
			statusRepository.markError(uploadId, res.getErrorPhase(), res.getMessage(), res.getTotalCount(),
					res.getErrors().size(), errorsJson(res), res.getS3Bucket(), res.getS3Key(), loginUser);
			return res;
		}

		// 5. OK → uploaded/ にそのまま置いて「判定前」にする（バッチサービスに引き継ぐ）
		try {
			CsvUploadArchiver.Stored stored = archiver.putUploaded(masterName, uploadId, zip.getZipName(),
					zip.getZipBytes(), loginUser);
			res.setS3Bucket(stored.getBucket());
			res.setS3Key(stored.getKey());
		} catch (RuntimeException e) {
			log.error("zip を uploaded/ に置けませんでした uploadId={}", uploadId, e);
			res.setStatus(Status.ERROR);
			res.setErrorPhase(Phase.STORE);
			res.setMessage(Message.STORE_ERROR);
			statusRepository.markError(uploadId, res.getErrorPhase(), res.getMessage(), res.getTotalCount(),
					0, "[]", null, null, loginUser);
			return res;
		}

		res.setStatus(Status.PENDING);
		res.setMessage(Message.PENDING);
		statusRepository.markUploaded(uploadId, res.getMessage(), res.getTotalCount(), res.getS3Bucket(),
				res.getS3Key(), loginUser);
		return res;
	}

	// =========================================================
	// 部品
	// =========================================================

	/**
	 * ログインユーザー（JWT の sub＝メールアドレス。users に存在することまで確かめる）
	 * @param authorization Authorization ヘッダー（Bearer {JWT}）
	 * @return メールアドレス
	 * @throws UnauthorizedUploadException ヘッダーなし・JWT 不正/期限切れ・ユーザーなし
	 */
	private String currentLoginUser(String authorization) {
		final JwtCurrentUserService.CurrentUser user;
		try {
			user = jwtCurrentUserService.resolve(authorization);
		} catch (ResponseStatusException e) {
			log.info("CSVアップロード 認証NG: {}", e.getReason());
			throw new UnauthorizedUploadException(Message.UNAUTHORIZED);
		}
		String email = user == null ? null : user.getEmail();
		if (email == null || email.isBlank()) {
			throw new UnauthorizedUploadException(Message.UNAUTHORIZED);
		}
		return email.strip();
	}

	/** 状況管理に残すエラー行（JSON 配列。先頭 Limit.MAX_SAVED_ERRORS 件まで） */
	private String errorsJson(CsvUploadResponse res) {
		List<CsvUploadRowError> errors = res.getErrors();
		List<CsvUploadRowError> saved = errors.size() > Limit.MAX_SAVED_ERRORS
				? errors.subList(0, Limit.MAX_SAVED_ERRORS)
				: errors;
		try {
			return objectMapper.writeValueAsString(saved);
		} catch (Exception e) {
			log.warn("エラー行の JSON 変換に失敗", e);
			return "[]";
		}
	}

	/** zip 名から中の CSV 名（〇〇.zip / 〇〇.csv.zip → 〇〇.csv。zip 以外なら空） */
	private static String csvNameOf(String zipName) {
		String stem = CsvUtil.stemOfZip(zipName);
		return stem == null ? "" : stem + FileName.CSV_EXT;
	}

	private static String nvl(String s) {
		return s == null ? "" : s;
	}
}