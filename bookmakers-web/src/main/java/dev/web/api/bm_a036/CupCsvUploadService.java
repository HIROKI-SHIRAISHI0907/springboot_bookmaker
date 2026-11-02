package dev.web.api.bm_a036;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.constant.CsvUploadConst.FileName;
import dev.common.constant.CsvUploadConst.Limit;
import dev.common.constant.CsvUploadConst.Phase;
import dev.web.api.bm_a031.CsvErrorFileBuilder;
import dev.web.api.bm_a031.CsvUploadArchiver;
import dev.web.api.bm_a031.CsvUploadRowError;
import dev.web.api.bm_a031.UnauthorizedUploadException;
import dev.web.jwt.JwtCurrentUserService;
import dev.web.repository.bm.FileUploadStatusRepository;
import dev.web.repository.master.CupCompetitionRepository;
import dev.web.util.CsvUtil;
import dev.web.util.CsvUtil.CsvFileException;
import dev.web.util.CsvUtil.CsvTable;
import dev.web.util.CsvUtil.UploadedZip;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 大会一括アップロード（bm_a036。どの大会を取得対象にするか）
 *
 * <ol>
 *   <li>ログインユーザーを確かめる（JWT。NG は {@link UnauthorizedUploadException} → Controller で 401）。</li>
 *   <li>ファイルアップロード状況管理（file_upload_status_manage・種類 cup）に「受付」で登録する。</li>
 *   <li>zip を読む（名前は cup_〇〇.zip、中の CSV は cup_〇〇.csv。{@link CsvUtil#readUploadedZip}）。</li>
 *   <li>行のチェック:
 *     <ul>
 *       <li>大会名・大会URL のどちらかが空 → エラー（アップロードできない）</li>
 *       <li>大会URL が https://www.flashscore.co.jp/soccer/ で始まらない → エラー</li>
 *       <li>同じ大会URL がファイル内に 2 回 → エラー</li>
 *       <li>大会名 100 文字・大会URL 500 文字を超える → エラー</li>
 *     </ul>
 *     1 行でもエラーがあれば、どの行も反映しない。エラー内容を書き足した zip を error/ に置き、状況管理を「異常終了」にする。</li>
 *   <li>反映（soccer_bm_master の 1 トランザクション）: 大会URL が同じ有効な大会があれば大会名を更新して「日程を取る」にする。
 *       無ければ登録する（地域・シーズンは空、表示順 0、日程を取る）。CSV に無い大会は変えない。</li>
 *   <li>反映できたら zip を success/ に置き、状況管理を「正常終了」にする（登録・更新の件数つき）。
 *       他の種類のように uploaded/・判定前（PENDING）を通らないので、バッチサービス（B015）は拾わない。</li>
 *   <li>反映後、大会と出場チームの JSON（b026_cup_competition.json）を出力し直す。</li>
 * </ol>
 *
 * @author shiraishitoshio
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CupCsvUploadService {

	public static final String PREFIX = "cup_";
	private static final int MAX_NAME = 100;
	private static final int MAX_URL = 500;
	private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");
	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
	private static final String DELIMITER = "／";

	private final CupCompetitionRepository repository;

	private final CupCompetitionService competitionService;

	private final JwtCurrentUserService jwtCurrentUserService;

	private final FileUploadStatusRepository statusRepository;

	private final CsvUploadArchiver archiver;

	private final ObjectMapper objectMapper;

	/** フォーマット（見出し行だけの CSV を zip にしたもの。例: cup_20261101120000.zip） */
	public CsvUtil.CsvFile downloadFormat() throws IOException {
		return CsvUtil.createFormatZip(PREFIX + LocalDateTime.now(JST).format(FILE_TS), CupCsvRow.class);
	}

	/**
	 * アップロード
	 * <p>トランザクションは反映の部分だけ（{@link CupCompetitionRepository#inTransaction}）。
	 * 状況管理（bm DB）は 1 文ずつすぐ確定する（反映が戻っても「異常終了」は残す）。</p>
	 * @param file zip
	 * @param authorization Authorization ヘッダー（Bearer {JWT}）
	 */
	public CupCsvUploadResponse upload(MultipartFile file, String authorization) {
		String loginUser = currentLoginUser(authorization);
		String masterName = CupCsvUploadResponse.MASTER_NAME;

		CupCsvUploadResponse res = new CupCsvUploadResponse();
		String fileName = file == null ? "" : CsvUtil.baseName(file.getOriginalFilename());
		res.setFileName(fileName);

		byte[] bytes;
		try {
			bytes = file == null ? new byte[0] : file.getBytes();
		} catch (IOException e) {
			log.warn("[A036] アップロードファイルを読めませんでした fileName={}", fileName, e);
			bytes = new byte[0];
		}

		// 状況管理に「受付」で登録（ID は S3 のキーにも使う）
		long uploadId = statusRepository.insertReceived(masterName, fileName, csvNameOf(fileName), bytes.length, loginUser);
		res.setUploadId(uploadId);

		// 読む
		CsvTable table = null;
		UploadedZip zip = null;
		List<CupCsvRow> rows = new ArrayList<>();
		List<String> fileErrors = new ArrayList<>();
		try {
			zip = CsvUtil.readUploadedZip(fileName, bytes, PREFIX);
			table = zip.getTable();
			rows = CsvUtil.toBeans(table, CupCsvRow.class);
			if (rows.isEmpty()) {
				fileErrors.add("データ行がありません。");
			}
		} catch (CsvFileException e) {
			fileErrors.add(e.getMessage());
			table = e.getTable();
		}
		res.setTotalCount(!rows.isEmpty() ? rows.size() : table == null ? 0 : table.getRows().size());

		// 行のチェック（どちらかが空ならアップロードできない）
		List<String> names = new ArrayList<>();
		List<String> urls = new ArrayList<>();
		Map<String, Integer> firstRowOfUrl = new HashMap<>();
		Map<Integer, List<String>> rowErrors = new LinkedHashMap<>();
		for (int i = 0; i < rows.size(); i++) {
			int rowNo = i + 2; // 見出しが 1 行目
			String name = trim(rows.get(i).getCompetitionName());
			String rawUrl = trim(rows.get(i).getCompetitionUrl());
			String url = CupCompetitionService.normalizeUrl(rawUrl);
			List<String> errs = new ArrayList<>();
			if (name.isEmpty()) {
				errs.add("大会名が空です。");
			} else if (name.length() > MAX_NAME) {
				errs.add("大会名は " + MAX_NAME + " 文字以内にしてください。");
			}
			if (rawUrl.isEmpty()) {
				errs.add("大会URLが空です。");
			} else if (!url.startsWith(CupCompetitionService.URL_PREFIX)) {
				errs.add("大会URLは " + CupCompetitionService.URL_PREFIX + " から始まる URL にしてください。");
			} else if (url.length() > MAX_URL) {
				errs.add("大会URLは " + MAX_URL + " 文字以内にしてください。");
			} else if (firstRowOfUrl.containsKey(url)) {
				errs.add("同じ大会URLが " + firstRowOfUrl.get(url) + " 行目にもあります。");
			} else {
				firstRowOfUrl.put(url, rowNo);
			}
			if (!errs.isEmpty()) {
				rowErrors.put(i, errs);
				res.getErrors().add(new CsvUploadRowError(rowNo, "", keyOf(name, rawUrl), String.join(DELIMITER, errs)));
			}
			names.add(name);
			urls.add(url);
		}
		if (!fileErrors.isEmpty()) {
			res.getErrors().add(0, new CsvUploadRowError(0, "", "ファイル", String.join(DELIMITER, fileErrors)));
		}

		// NG → error/ に {名前}_error.zip、状況管理は「異常終了」
		if (!res.getErrors().isEmpty()) {
			res.setStatus(CupCsvUploadResponse.STATUS_ERROR);
			res.setMessage("バリデーションエラーのため反映しませんでした。（エラー " + res.getErrors().size() + " 件）");
			String bucket = null;
			String key = null;
			try {
				CsvUtil.CsvFile errorZip = CsvErrorFileBuilder.buildErrorZip(fileName, table, rowErrors, fileErrors);
				CsvUploadArchiver.Stored stored = archiver.putError(masterName, uploadId, errorZip, loginUser);
				bucket = stored.getBucket();
				key = stored.getKey();
			} catch (IOException | RuntimeException e) {
				log.error("[A036] エラーファイルを error/ に置けませんでした uploadId={}", uploadId, e);
				res.setMessage(res.getMessage() + "（エラーファイルは保管できませんでした）");
			}
			statusRepository.markError(uploadId, Phase.VALIDATION, res.getMessage(), res.getTotalCount(),
					res.getErrors().size(), errorsJson(res.getErrors()), bucket, key, loginUser);
			return res;
		}

		// 反映（同じ URL の大会があれば更新、無ければ登録。1 トランザクション）
		final int[] counts = new int[2]; // [0]=登録 [1]=更新
		try {
			repository.inTransaction(() -> {
				for (int i = 0; i < names.size(); i++) {
					if (repository.updateCompetitionByUrl(urls.get(i), names.get(i), loginUser) > 0) {
						counts[1]++;
					} else {
						repository.insertCompetition(names.get(i), "", urls.get(i), "", 0, "1", loginUser);
						counts[0]++;
					}
				}
			});
		} catch (RuntimeException e) {
			log.error("[A036] 大会一括アップロードの反映に失敗しました uploadId={}", uploadId, e);
			res.setStatus(CupCsvUploadResponse.STATUS_ERROR);
			res.setMessage("DB への反映に失敗したため、どの行も反映しませんでした。");
			res.getErrors().add(new CsvUploadRowError(0, "", "ファイル", res.getMessage()));
			statusRepository.markError(uploadId, Phase.APPLY, res.getMessage(), res.getTotalCount(),
					1, errorsJson(res.getErrors()), null, null, loginUser);
			return res;
		}
		res.setInsertCount(counts[0]);
		res.setUpdateCount(counts[1]);
		res.setStatus(CupCsvUploadResponse.STATUS_SUCCESS);
		String msg = "反映しました。（登録 " + res.getInsertCount() + " 件・更新 " + res.getUpdateCount() + " 件）";

		// success/ に置く（置けなくても反映は済んでいるので正常終了のまま）
		String bucket = null;
		String key = null;
		try {
			CsvUploadArchiver.Stored stored = archiver.putSuccess(masterName, uploadId, zip.getZipName(),
					zip.getZipBytes(), loginUser);
			bucket = stored.getBucket();
			key = stored.getKey();
		} catch (RuntimeException e) {
			log.error("[A036] zip を success/ に置けませんでした uploadId={}", uploadId, e);
		}
		statusRepository.markSuccess(uploadId, msg, res.getTotalCount(), res.getInsertCount(), res.getUpdateCount(),
				bucket, key, loginUser);

		String exportError = competitionService.exportCompetitionJson();
		if (exportError != null) {
			msg += "ただし、" + exportError;
		}
		res.setMessage(msg);
		log.info("[A036] 大会一括アップロード uploadId={} fileName={} 登録={} 更新={} user={}", uploadId, fileName,
				res.getInsertCount(), res.getUpdateCount(), loginUser);
		return res;
	}

	/** 状況管理に残すエラー行（JSON 配列。先頭 Limit.MAX_SAVED_ERRORS 件まで） */
	private String errorsJson(List<CsvUploadRowError> errors) {
		List<CsvUploadRowError> saved = errors.size() > Limit.MAX_SAVED_ERRORS
				? errors.subList(0, Limit.MAX_SAVED_ERRORS)
				: errors;
		try {
			return objectMapper.writeValueAsString(saved);
		} catch (Exception e) {
			log.warn("[A036] エラー行の JSON 変換に失敗", e);
			return "[]";
		}
	}

	/** zip 名から中の CSV 名（〇〇.zip → 〇〇.csv。zip 以外なら空） */
	private static String csvNameOf(String zipName) {
		String stem = CsvUtil.stemOfZip(zipName);
		return stem == null ? "" : stem + FileName.CSV_EXT;
	}

	/** ログインユーザー（JWT の sub＝メールアドレス） */
	private String currentLoginUser(String authorization) {
		final JwtCurrentUserService.CurrentUser user;
		try {
			user = jwtCurrentUserService.resolve(authorization);
		} catch (ResponseStatusException e) {
			throw new UnauthorizedUploadException("ログインしてからアップロードしてください。");
		}
		String email = user == null ? null : user.getEmail();
		if (email == null || email.isBlank()) {
			throw new UnauthorizedUploadException("ログインしてからアップロードしてください。");
		}
		return email.strip();
	}

	private static String keyOf(String name, String url) {
		return (name.isEmpty() ? "（大会名なし）" : name) + " / " + (url.isEmpty() ? "（URLなし）" : url);
	}

	private static String trim(String s) {
		return s == null ? "" : s.strip();
	}
}
