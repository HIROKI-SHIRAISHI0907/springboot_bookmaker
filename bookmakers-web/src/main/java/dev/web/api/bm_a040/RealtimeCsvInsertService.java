package dev.web.api.bm_a040;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.CsvUploadConst.FileName;
import dev.common.constant.CsvUploadConst.Limit;
import dev.common.constant.CsvUploadConst.Phase;
import dev.common.entity.DataEntity;
import dev.common.readfile.ReadOrigin;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.web.api.bm_a031.CsvErrorFileBuilder;
import dev.web.api.bm_a031.CsvUploadArchiver;
import dev.web.api.bm_a031.CsvUploadRowError;
import dev.web.api.bm_a031.UnauthorizedUploadException;
import dev.web.jwt.JwtCurrentUserService;
import dev.web.repository.bm.FileUploadStatusRepository;
import dev.web.repository.bm.RealtimeCsvInsertRepository;
import dev.web.util.CsvUtil;
import dev.web.util.CsvUtil.CsvFileException;
import dev.web.util.CsvUtil.CsvTable;
import dev.web.util.CsvUtil.UploadedZip;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * リアルタイムデータ CSV アップロード（bm_a040。static_data に直接登録。2026-11-28 追加）
 *
 * <p>スクレイパーが取れなかった試合（終了済のデータなど）を、Flashscore から取り直した CSV で入れるための画面。</p>
 * <ol>
 *   <li>ログインユーザーを確かめる（JWT。NG は 401）。状況管理（種類 realtimeData）に「受付」で登録。</li>
 *   <li>zip を読む（realtimedata_〇〇.zip の中に realtimedata_〇〇.csv）。列はスクレイパーと同じ名前（{@link RealtimeCsvColumns#ALL}）。
 *       順番は自由・無い列は空として扱う。知らない列があればエラー。</li>
 *   <li>行のチェック（1 行でもエラーがあれば、どの行も登録しない）:
 *     <ul>
 *       <li>ホームチーム・アウェーチーム・試合国及びカテゴリ・試合時間・スコア時間 が空 → エラー</li>
 *       <li>ホームとアウェーが同じ → エラー</li>
 *       <li>試合国及びカテゴリが「国: リーグ」の形でない → エラー（「国: リーグ - ラウンド N」を推奨）</li>
 *       <li>スコア時間（= record_time。試合が行われた日時を入れる）が yyyy-MM-dd HH:mm:ss（/ 区切り・秒なしも可）でない → エラー</li>
 *       <li>試合ID も 試合リンク文字列の mid= も無い → エラー（seq_key・同じ試合の判定に使う）</li>
 *       <li>試合ID と リンクの mid= が違う → エラー</li>
 *       <li>同じ 試合ID・試合時間 の行がファイル内に 2 つ → エラー</li>
 *     </ul></li>
 *   <li>登録（1 トランザクション）: 列を並べ直した CSV を ReadOrigin（B008 と同じ読み方）で読み、
 *       すでに同じ行（データカテゴリ・試合時間・チーム・試合ID）があれば飛ばす。
 *       seq_key は「試合ID-連番」（その試合の最後の連番の次）。add_manual_flg = '1'（手動取得）。
 *       データカテゴリは CSV のまま（B008 のような推定はしない）。</li>
 *   <li>反映後は、通常の流れ（B011 → B006、終了済しか無い試合は B018）で統計に入る。</li>
 * </ol>
 * @author shiraishitoshio
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeCsvInsertService {

	public static final String PREFIX = "realtimedata_";
	private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");
	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
	private static final String DELIMITER = "／";
	private static final Pattern MID = Pattern.compile("[?&]mid=([A-Za-z0-9]+)");
	/** 「国: リーグ」（後ろに「 - ラウンド N」などが付いてもよい） */
	private static final Pattern CATEGORY = Pattern.compile("^[^:：]+[:：]\\s*\\S.*$");
	private static final DateTimeFormatter[] TIME_FORMATS = {
			DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"),
			DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss"),
			DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm"),
			DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm") };
	private static final DateTimeFormatter OUT_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

	private final RealtimeCsvInsertRepository repository;

	private final ReadOrigin readOrigin;

	private final JwtCurrentUserService jwtCurrentUserService;

	private final FileUploadStatusRepository statusRepository;

	private final CsvUploadArchiver archiver;

	private final ObjectMapper objectMapper;

	/** フォーマット（見出し行と例の行（先頭が #）の CSV を zip にしたもの） */
	public CsvUtil.CsvFile downloadFormat() throws IOException {
		String base = PREFIX + LocalDateTime.now(JST).format(FILE_TS);
		String[] sample = new String[RealtimeCsvColumns.ALL.size()];
		for (int i = 0; i < sample.length; i++) {
			sample[i] = "";
		}
		set(sample, "ホーム順位", "#3");
		set(sample, RealtimeCsvColumns.CATEGORY, "日本: J1 リーグ - ラウンド 9");
		set(sample, RealtimeCsvColumns.TIMES, "終了済");
		set(sample, RealtimeCsvColumns.HOME_TEAM, "FC東京");
		set(sample, RealtimeCsvColumns.HOME_SCORE, "2");
		set(sample, "アウェー順位", "5");
		set(sample, RealtimeCsvColumns.AWAY_TEAM, "浦和レッズ");
		set(sample, RealtimeCsvColumns.AWAY_SCORE, "1");
		set(sample, RealtimeCsvColumns.RECORD_TIME, "2026-10-04 16:55:00");
		set(sample, RealtimeCsvColumns.GAME_LINK, "https://www.flashscore.co.jp/match/soccer/xxxx/?mid=AbCdEfGh");
		set(sample, RealtimeCsvColumns.MATCH_ID, "AbCdEfGh");
		set(sample, RealtimeCsvColumns.SORT_SECONDS, "5400");
		List<String[]> rows = new ArrayList<>();
		rows.add(sample);
		byte[] csv = CsvUtil.writeCsv(RealtimeCsvColumns.ALL, rows);
		return new CsvUtil.CsvFile(base + ".zip", CsvUtil.zipSingle(base + FileName.CSV_EXT, csv));
	}

	private static void set(String[] row, String col, String v) {
		row[RealtimeCsvColumns.ALL.indexOf(col)] = v;
	}

	/** アップロード */
	public RealtimeCsvInsertResponse upload(MultipartFile file, String authorization) {
		String loginUser = currentLoginUser(authorization);
		String masterName = RealtimeCsvInsertResponse.MASTER_NAME;

		RealtimeCsvInsertResponse res = new RealtimeCsvInsertResponse();
		String fileName = file == null ? "" : CsvUtil.baseName(file.getOriginalFilename());
		res.setFileName(fileName);
		byte[] bytes;
		try {
			bytes = file == null ? new byte[0] : file.getBytes();
		} catch (IOException e) {
			log.warn("[A040] アップロードファイルを読めませんでした fileName={}", fileName, e);
			bytes = new byte[0];
		}
		long uploadId = statusRepository.insertReceived(masterName, fileName, csvNameOf(fileName), bytes.length, loginUser);
		res.setUploadId(uploadId);

		// 読む
		CsvTable table = null;
		UploadedZip zip = null;
		List<String> fileErrors = new ArrayList<>();
		try {
			zip = CsvUtil.readUploadedZip(fileName, bytes, PREFIX);
			table = zip.getTable();
			if (table.getRows().isEmpty()) {
				fileErrors.add("データ行がありません。");
			}
		} catch (CsvFileException e) {
			fileErrors.add(e.getMessage());
			table = e.getTable();
		}

		// 見出し → 列番号
		Map<String, Integer> pos = new HashMap<>();
		if (table != null && fileErrors.isEmpty()) {
			List<String> header = table.getHeader();
			for (int i = 0; i < header.size(); i++) {
				String h = header.get(i);
				if (h.isEmpty()) {
					continue;
				}
				if (!RealtimeCsvColumns.ALL.contains(h)) {
					fileErrors.add("知らない列があります: " + h);
				} else if (pos.containsKey(h)) {
					fileErrors.add("同じ列が 2 つあります: " + h);
				} else {
					pos.put(h, i);
				}
			}
			for (String req : RealtimeCsvColumns.REQUIRED) {
				if (!pos.containsKey(req)) {
					fileErrors.add("必須の列がありません: " + req);
				}
			}
			if (!pos.containsKey(RealtimeCsvColumns.MATCH_ID) && !pos.containsKey(RealtimeCsvColumns.GAME_LINK)) {
				fileErrors.add("「試合ID」か「試合リンク文字列」の列が必要です。");
			}
		}
		List<String[]> raw = table == null ? new ArrayList<>() : table.getRows();
		res.setTotalCount(raw.size());

		// 行のチェックと並べ直し
		List<String[]> ordered = new ArrayList<>();
		Map<Integer, List<String>> rowErrors = new LinkedHashMap<>();
		Map<String, Integer> firstRow = new HashMap<>();
		if (fileErrors.isEmpty()) {
			for (int i = 0; i < raw.size(); i++) {
				int rowNo = i + 2;
				String[] src = raw.get(i);
				String[] row = new String[RealtimeCsvColumns.ALL.size()];
				for (int c = 0; c < row.length; c++) {
					Integer p = pos.get(RealtimeCsvColumns.ALL.get(c));
					row[c] = p == null || p >= src.length || src[p] == null ? "" : src[p].strip();
				}
				List<String> errs = new ArrayList<>();
				for (String req : RealtimeCsvColumns.REQUIRED) {
					if (get(row, req).isEmpty()) {
						errs.add(req + "が空です。");
					}
				}
				String home = get(row, RealtimeCsvColumns.HOME_TEAM);
				String away = get(row, RealtimeCsvColumns.AWAY_TEAM);
				if (!home.isEmpty() && home.equals(away)) {
					errs.add("ホームとアウェーが同じです。");
				}
				String cat = get(row, RealtimeCsvColumns.CATEGORY);
				if (!cat.isEmpty() && !CATEGORY.matcher(cat).matches()) {
					errs.add("試合国及びカテゴリは「国: リーグ - ラウンド N」の形にしてください。");
				}
				String rt = get(row, RealtimeCsvColumns.RECORD_TIME);
				if (!rt.isEmpty()) {
					LocalDateTime t = parseTime(rt);
					if (t == null) {
						errs.add("スコア時間は yyyy-MM-dd HH:mm:ss の形にしてください。");
					} else {
						set(row, RealtimeCsvColumns.RECORD_TIME, t.format(OUT_TIME));
					}
				}
				String matchId = get(row, RealtimeCsvColumns.MATCH_ID);
				String mid = midOf(get(row, RealtimeCsvColumns.GAME_LINK));
				if (matchId.isEmpty() && mid == null) {
					errs.add("試合ID か 試合リンク文字列（?mid=…）のどちらかが必要です。");
				} else if (!matchId.isEmpty() && mid != null && !matchId.equals(mid)) {
					errs.add("試合ID（" + matchId + "）と試合リンクの mid（" + mid + "）が違います。");
				} else if (matchId.isEmpty()) {
					matchId = mid;
					set(row, RealtimeCsvColumns.MATCH_ID, mid);
				}
				String sort = get(row, RealtimeCsvColumns.SORT_SECONDS);
				if (sort.isEmpty()) {
					set(row, RealtimeCsvColumns.SORT_SECONDS, "1");
				} else if (!sort.matches("-?[0-9]+")) {
					errs.add("ソート用秒は整数にしてください。");
				}
				String times = get(row, RealtimeCsvColumns.TIMES);
				String dupKey = matchId + "|" + times;
				if (errs.isEmpty()) {
					if (firstRow.containsKey(dupKey)) {
						errs.add("同じ試合ID・試合時間の行が " + firstRow.get(dupKey) + " 行目にもあります。");
					} else {
						firstRow.put(dupKey, rowNo);
					}
				}
				if (!errs.isEmpty()) {
					rowErrors.put(i, errs);
					res.getErrors().add(new CsvUploadRowError(rowNo, "", home + " vs " + away + "（" + times + "）",
							String.join(DELIMITER, errs)));
				}
				ordered.add(row);
			}
		}
		if (!fileErrors.isEmpty()) {
			res.getErrors().add(0, new CsvUploadRowError(0, "", "ファイル", String.join(DELIMITER, fileErrors)));
		}
		if (!res.getErrors().isEmpty()) {
			return fail(res, uploadId, masterName, fileName, table, rowErrors, fileErrors, loginUser, Phase.VALIDATION,
					"バリデーションエラーのため登録しませんでした。（エラー " + res.getErrors().size() + " 件）");
		}

		// B008 と同じ読み方で DataEntity にする
		List<DataEntity> entities;
		try {
			byte[] csv = CsvUtil.writeCsv(RealtimeCsvColumns.ALL, ordered);
			ReadFileOutputDTO dto = readOrigin.getFileBodyFromStream(new ByteArrayInputStream(csv), "upload/" + fileName);
			if (!BookMakersCommonConst.NORMAL_CD.equals(dto.getResultCd()) || dto.getDataList() == null
					|| dto.getDataList().size() != ordered.size()) {
				throw new IllegalStateException("CSV の読み込みに失敗しました。");
			}
			entities = dto.getDataList();
		} catch (RuntimeException e) {
			log.error("[A040] CSV の読み込みに失敗 uploadId={}", uploadId, e);
			res.getErrors().add(new CsvUploadRowError(0, "", "ファイル", "CSV を読み込めませんでした。"));
			return fail(res, uploadId, masterName, fileName, table, rowErrors, fileErrors, loginUser, Phase.VALIDATION,
					"CSV を読み込めなかったため登録しませんでした。");
		}

		// 登録（1 トランザクション。すでにある行は飛ばす）
		final int[] counts = new int[2]; // [0]=登録 [1]=飛ばした
		try {
			repository.inTransaction(() -> {
				Map<String, Integer> nextNo = new HashMap<>();
				for (DataEntity e : entities) {
					e.setAddManualFlg("1");
					if (repository.exists(e)) {
						counts[1]++;
						continue;
					}
					String matchId = e.getMatchId();
					Integer next = nextNo.get(matchId);
					if (next == null) {
						String last = repository.findLastSeqKey(matchId);
						next = last == null ? 1 : renbanOf(last) + 1;
					}
					e.setSeqKey(matchId + "-" + next);
					nextNo.put(matchId, next + 1);
					repository.insert(e, loginUser);
					counts[0]++;
				}
			});
		} catch (RuntimeException e) {
			log.error("[A040] リアルタイムデータの登録に失敗しました uploadId={}", uploadId, e);
			res.getErrors().add(new CsvUploadRowError(0, "", "ファイル", "DB への登録に失敗したため、どの行も登録しませんでした。"));
			return fail(res, uploadId, masterName, fileName, table, rowErrors, fileErrors, loginUser, Phase.APPLY,
					"DB への登録に失敗したため、どの行も登録しませんでした。（" + e.getClass().getSimpleName() + "）");
		}
		res.setInsertCount(counts[0]);
		res.setSkipCount(counts[1]);
		res.setStatus(RealtimeCsvInsertResponse.STATUS_SUCCESS);
		String msg = "登録しました。（登録 " + counts[0] + " 行" + (counts[1] > 0 ? "・登録済みのため飛ばした " + counts[1] + " 行" : "") + "）";
		res.setMessage(msg);

		String bucket = null;
		String key = null;
		try {
			CsvUploadArchiver.Stored stored = archiver.putSuccess(masterName, uploadId, zip.getZipName(), zip.getZipBytes(), loginUser);
			bucket = stored.getBucket();
			key = stored.getKey();
		} catch (RuntimeException e) {
			log.error("[A040] zip を success/ に置けませんでした uploadId={}", uploadId, e);
		}
		statusRepository.markSuccess(uploadId, msg, res.getTotalCount(), counts[0], 0, bucket, key, loginUser);
		log.info("[A040] リアルタイムデータ CSV uploadId={} fileName={} 登録={} 飛ばした={} user={}", uploadId, fileName,
				counts[0], counts[1], loginUser);
		return res;
	}

	private RealtimeCsvInsertResponse fail(RealtimeCsvInsertResponse res, long uploadId, String masterName, String fileName,
			CsvTable table, Map<Integer, List<String>> rowErrors, List<String> fileErrors, String loginUser, String phase,
			String message) {
		res.setStatus(RealtimeCsvInsertResponse.STATUS_ERROR);
		res.setMessage(message);
		String bucket = null;
		String key = null;
		try {
			CsvUtil.CsvFile errorZip = CsvErrorFileBuilder.buildErrorZip(fileName, table, rowErrors, fileErrors);
			CsvUploadArchiver.Stored stored = archiver.putError(masterName, uploadId, errorZip, loginUser);
			bucket = stored.getBucket();
			key = stored.getKey();
		} catch (IOException | RuntimeException e) {
			log.error("[A040] エラーファイルを error/ に置けませんでした uploadId={}", uploadId, e);
			res.setMessage(res.getMessage() + "（エラーファイルは保管できませんでした）");
		}
		statusRepository.markError(uploadId, phase, res.getMessage(), res.getTotalCount(), res.getErrors().size(),
				errorsJson(res.getErrors()), bucket, key, loginUser);
		return res;
	}

	private static String get(String[] row, String col) {
		String v = row[RealtimeCsvColumns.ALL.indexOf(col)];
		return v == null ? "" : v;
	}

	private static LocalDateTime parseTime(String s) {
		for (DateTimeFormatter f : TIME_FORMATS) {
			try {
				return LocalDateTime.parse(s.strip(), f);
			} catch (DateTimeParseException e) {
				// 次の形式
			}
		}
		return null;
	}

	private static String midOf(String link) {
		if (link == null) {
			return null;
		}
		Matcher m = MID.matcher(link);
		return m.find() ? m.group(1) : null;
	}

	private static int renbanOf(String seqKey) {
		int idx = seqKey.lastIndexOf('-');
		try {
			return idx < 0 ? 0 : Integer.parseInt(seqKey.substring(idx + 1));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private String errorsJson(List<CsvUploadRowError> errors) {
		List<CsvUploadRowError> saved = errors.size() > Limit.MAX_SAVED_ERRORS ? errors.subList(0, Limit.MAX_SAVED_ERRORS) : errors;
		try {
			return objectMapper.writeValueAsString(saved);
		} catch (Exception e) {
			return "[]";
		}
	}

	private static String csvNameOf(String zipName) {
		String stem = CsvUtil.stemOfZip(zipName);
		return stem == null ? "" : stem + FileName.CSV_EXT;
	}

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
}
