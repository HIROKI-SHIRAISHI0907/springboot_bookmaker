package dev.batch.bm_b015;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.batch.bm_b015.UploadCsvUtil.CsvTable;
import dev.batch.repository.bm.CsvUploadRealtimeBatchRepository;
import dev.batch.repository.master.CsvUploadMasterBatchRepository;

/**
 * CSV の DB チェックと反映（B015）
 *
 * <ol>
 *   <li>全行をチェックし、行ごとに「すること」を決める（この時点では DB を変えない）。
 *       値の形のチェックは web でも行っているが、念のためここでも行う。</li>
 *   <li>1 行でもエラーがあれば、どの行も反映しない（行ごとのエラーを返す）。</li>
 *   <li>エラーが無ければ、1 つのトランザクションで CSV の順に反映する。途中で失敗したらすべて元に戻す。</li>
 * </ol>
 *
 * <p>対象の決め方（キーは全角・半角の揺れと前後の空白を無視して比べる。del_flg は問わない）</p>
 * <ul>
 *   <li>シーズン: 国 + リーグ / チーム: 国 + リーグ + チーム / 試合予定: ホームチーム + アウェーチーム + 試合国及びカテゴリ</li>
 *   <li>登録: キーが既にあればエラー。</li>
 *   <li>更新: ID があれば ID、無ければキーで 1 件に決める（0 件・2 件以上はエラー）。
 *       ID 指定でキーを書き換えるとき、変更後のキーが他の行と重なればエラー。</li>
 *   <li>削除・非表示・再表示: キーだけで 1 件に決める（0 件・2 件以上はエラー）。</li>
 *   <li>リアルタイム（static_data）: 非表示・再表示だけ。キーに該当する行すべてが対象（0 件はエラー）。</li>
 *   <li>同じ対象（同じ ID・同じキー）がファイル内に 2 回出てきたらエラー（順番で結果が変わるのを防ぐ）。</li>
 * </ul>
 *
 * <p>トランザクション: シーズン・チーム・試合予定は master DB（masterTxManager）、リアルタイムは bm DB（bmTxManager）。
 *    状況管理（file_upload_status_manage）の更新はこのトランザクションに含めない。</p>
 * @author shiraishitoshio
 */
@Component
public class FileUploadReflectService {

	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
	private static final DateTimeFormatter OUT_TS = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'+09:00'");
	private static final Pattern NUMBER_PAT = Pattern.compile("^-?\\d+(\\.\\d+)?$");
	private static final Pattern DIGITS_PAT = Pattern.compile("^\\d+$");

	/** 試合国及びカテゴリの形（web の CsvRowValidator.CATEGORY_PAT と同じ） */
	private static final Pattern CATEGORY_PAT = Pattern.compile(
			"^[^:：\\s　](?:[^:：]*[^:：\\s　])?: [^\\s　](?:.*[^\\s　])? - ラウンド [0-9]+$");

	private static final String USER = FileUploadBatchConst.CSV_USER;

	@Autowired
	private CsvUploadMasterBatchRepository masterRepository;

	@Autowired
	private CsvUploadRealtimeBatchRepository realtimeRepository;

	@Autowired
	@Qualifier("masterTxManager")
	private PlatformTransactionManager masterTxManager;

	@Autowired
	@Qualifier("bmTxManager")
	private PlatformTransactionManager bmTxManager;

	// =========================================================
	// 結果
	// =========================================================

	/** 反映の結果 */
	public static class Result {
		private boolean success;
		/** 反映の途中で DB エラーになった（すべて元に戻した） */
		private boolean applyFailed;
		private int totalCount;
		private int insertCount;
		private int updateCount;
		private int deleteCount;
		private int hideCount;
		private int showCount;
		/** 状況管理に残すエラー行（ファイル全体のエラーは rowNo = 0） */
		private final List<UploadRowError> errors = new ArrayList<>();
		/** エラーファイル用: データ行の番号（0 始まり）→ エラー内容 */
		private final Map<Integer, List<String>> rowErrors = new LinkedHashMap<>();
		/** エラーファイル用: ファイル全体のエラー */
		private final List<String> fileErrors = new ArrayList<>();

		public boolean isSuccess() {
			return success;
		}

		public boolean isApplyFailed() {
			return applyFailed;
		}

		public int getTotalCount() {
			return totalCount;
		}

		public int getInsertCount() {
			return insertCount;
		}

		public int getUpdateCount() {
			return updateCount;
		}

		public int getDeleteCount() {
			return deleteCount;
		}

		public int getHideCount() {
			return hideCount;
		}

		public int getShowCount() {
			return showCount;
		}

		public List<UploadRowError> getErrors() {
			return errors;
		}

		public Map<Integer, List<String>> getRowErrors() {
			return rowErrors;
		}

		public List<String> getFileErrors() {
			return fileErrors;
		}

		/** ファイル全体のエラーを足す */
		public void addFileError(String message) {
			fileErrors.add(message);
			errors.add(0, new UploadRowError(0, "", FileUploadBatchConst.MSG_FILE_KEY, message));
		}

		void addRowError(int index, String operation, String key, String message) {
			rowErrors.computeIfAbsent(index, k -> new ArrayList<>()).add(message);
			errors.add(new UploadRowError(index + 2, operation, key, message));
		}

		void count(UploadOperation op) {
			switch (op) {
			case INSERT:
				insertCount++;
				break;
			case UPDATE:
				updateCount++;
				break;
			case DELETE:
				deleteCount++;
				break;
			case HIDE:
				hideCount++;
				break;
			case SHOW:
				showCount++;
				break;
			default:
				break;
			}
		}

		void resetCounts() {
			insertCount = 0;
			updateCount = 0;
			deleteCount = 0;
			hideCount = 0;
			showCount = 0;
		}
	}

	/** 1 行分の「すること」 */
	private static class Plan {
		final int index;
		final UploadOperation op;
		final Runnable action;

		Plan(int index, UploadOperation op, Runnable action) {
			this.index = index;
			this.op = op;
			this.action = action;
		}
	}

	/** 行のエラー（行番号はあとで付ける） */
	private static class RowException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		final String key;

		RowException(String key, String message) {
			super(message);
			this.key = key;
		}
	}

	/** 反映中の失敗（どの行で失敗したかを持つ） */
	private static class ApplyException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		final int index;

		ApplyException(int index, Throwable cause) {
			super(cause);
			this.index = index;
		}
	}

	/** 1 行の値の取り出し */
	private static class Row {
		final CsvTable table;
		final int index;

		Row(CsvTable table, int index) {
			this.table = table;
			this.index = index;
		}

		String get(String column) {
			return table.value(index, column).strip();
		}
	}

	// =========================================================
	// 入口
	// =========================================================

	/**
	 * CSV をチェックし、エラーが無ければ反映する
	 * @param masterName season / team / future / realtime
	 * @param table CSV の表
	 */
	public Result reflect(String masterName, CsvTable table) {
		Result res = new Result();
		res.totalCount = table.getRows().size();

		// 列のチェック（web でも見ているが、念のため）
		List<String> missing = new ArrayList<>();
		for (String col : FileUploadBatchConst.columnsOf(masterName)) {
			if (table.indexOf(col) < 0) {
				missing.add(col);
			}
		}
		if (!missing.isEmpty()) {
			res.addFileError("CSVの見出しに列が足りません。（" + String.join(", ", missing) + "）");
			return res;
		}
		if (table.getRows().isEmpty()) {
			res.addFileError(FileUploadBatchConst.MSG_NO_DATA);
			return res;
		}

		// 1. チェックと「すること」の決定（DB は変えない）
		List<Plan> plans = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < table.getRows().size(); i++) {
			Row row = new Row(table, i);
			try {
				plans.add(plan(masterName, row, seen));
			} catch (RowException e) {
				res.addRowError(i, row.get(FileUploadBatchConst.COL_OPERATION), e.key, e.getMessage());
			}
		}
		if (!res.getErrors().isEmpty()) {
			return res;
		}

		// 2. 反映（1 トランザクション）
		TransactionTemplate tx = new TransactionTemplate(
				FileUploadBatchConst.MASTER_REALTIME.equals(masterName) ? bmTxManager : masterTxManager);
		try {
			tx.executeWithoutResult(status -> {
				for (Plan p : plans) {
					try {
						p.action.run();
					} catch (RuntimeException e) {
						throw new ApplyException(p.index, e);
					}
					res.count(p.op);
				}
			});
		} catch (ApplyException e) {
			res.resetCounts();
			res.applyFailed = true;
			Row row = new Row(table, e.index);
			res.addRowError(e.index, row.get(FileUploadBatchConst.COL_OPERATION), keyOf(masterName, row),
					"DB への反映に失敗しました。（" + rootMessage(e.getCause()) + "）ほかの行も含めて、すべて元に戻しました。");
			return res;
		} catch (RuntimeException e) {
			// コミット時の失敗など（どの行かは分からない）
			res.resetCounts();
			res.applyFailed = true;
			res.addFileError("DB への反映に失敗しました。（" + rootMessage(e) + "）すべて元に戻しました。");
			return res;
		}
		res.success = true;
		return res;
	}

	private Plan plan(String masterName, Row row, Set<String> seen) {
		if (FileUploadBatchConst.MASTER_SEASON.equals(masterName)) {
			return planSeason(row, seen);
		}
		if (FileUploadBatchConst.MASTER_TEAM.equals(masterName)) {
			return planTeam(row, seen);
		}
		if (FileUploadBatchConst.MASTER_FUTURE.equals(masterName)) {
			return planFuture(row, seen);
		}
		if (FileUploadBatchConst.MASTER_REALTIME.equals(masterName)) {
			return planRealtime(row, seen);
		}
		throw new RowException("", String.format(FileUploadBatchConst.MSG_UNKNOWN_MASTER, masterName));
	}

	/** エラー表示用のキー */
	private static String keyOf(String masterName, Row r) {
		if (FileUploadBatchConst.MASTER_SEASON.equals(masterName)) {
			return r.get(FileUploadBatchConst.COL_COUNTRY) + " / " + r.get(FileUploadBatchConst.COL_LEAGUE);
		}
		if (FileUploadBatchConst.MASTER_TEAM.equals(masterName)) {
			return r.get(FileUploadBatchConst.COL_COUNTRY) + " / " + r.get(FileUploadBatchConst.COL_LEAGUE) + " / "
					+ r.get(FileUploadBatchConst.COL_TEAM);
		}
		if (FileUploadBatchConst.MASTER_FUTURE.equals(masterName)) {
			return r.get(FileUploadBatchConst.COL_HOME_TEAM) + " vs " + r.get(FileUploadBatchConst.COL_AWAY_TEAM) + " / "
					+ r.get(FileUploadBatchConst.COL_GAME_TEAM_CATEGORY);
		}
		return r.get(FileUploadBatchConst.COL_HOME_TEAM) + " vs " + r.get(FileUploadBatchConst.COL_AWAY_TEAM) + " / "
				+ r.get(FileUploadBatchConst.COL_DATA_CATEGORY);
	}

	// =========================================================
	// シーズン（country_league_season_master）
	// =========================================================

	private Plan planSeason(Row r, Set<String> seen) {
		String country = r.get(FileUploadBatchConst.COL_COUNTRY);
		String league = r.get(FileUploadBatchConst.COL_LEAGUE);
		String key = country + " / " + league;
		UploadOperation op = requireOperation(r.get(FileUploadBatchConst.COL_OPERATION), key);
		String idStr = r.get(FileUploadBatchConst.COL_ID);
		String seasonYear = r.get(FileUploadBatchConst.COL_SEASON_YEAR);
		String startRaw = r.get(FileUploadBatchConst.COL_START_SEASON_DATE);
		String endRaw = r.get(FileUploadBatchConst.COL_END_SEASON_DATE);
		String start = toTimestamp(startRaw, "シーズン開始日", key);
		String end = toTimestamp(endRaw, "シーズン終了日", key);
		String round = r.get(FileUploadBatchConst.COL_ROUND);
		String path = r.get(FileUploadBatchConst.COL_PATH);
		String icon = r.get(FileUploadBatchConst.COL_ICON);
		String validFlg = r.get(FileUploadBatchConst.COL_VALID_FLG);
		if (!round.isEmpty() && !DIGITS_PAT.matcher(round).matches()) {
			throw new RowException(key, "ラウンド数は数字で入力してください。");
		}
		if (!validFlg.isEmpty() && !validFlg.equals("0") && !validFlg.equals("1")) {
			throw new RowException(key, "有効フラグは 0 または 1 で入力してください。");
		}
		if (start != null && end != null && start.compareTo(end) > 0) {
			throw new RowException(key, "シーズン終了日がシーズン開始日より前になっています。");
		}
		int index = r.index;

		if (op == UploadOperation.INSERT) {
			noId(idStr, op, key);
			requireNotEmpty(country, "国", key);
			requireNotEmpty(league, "リーグ", key);
			requireNotEmpty(seasonYear, "シーズン年", key);
			requireNotEmpty(startRaw, "シーズン開始日", key);
			requireNotEmpty(endRaw, "シーズン終了日", key);
			requireNotEmpty(round, "ラウンド数", key);
			requireNotEmpty(path, "パス", key);
			List<Long> ids = masterRepository.findSeasonIds(country, league);
			if (!ids.isEmpty()) {
				throw new RowException(key, "既に登録されています。（ID=" + ids.get(0) + "）");
			}
			markSeen(seen, "k:" + norm(key), key);
			SeasonUploadParam p = new SeasonUploadParam();
			p.setCountry(country);
			p.setLeague(league);
			p.setSeasonYear(seasonYear);
			p.setStartSeasonDate(start);
			p.setEndSeasonDate(end);
			p.setRound(round);
			p.setPath(path);
			p.setIcon(emptyToNull(icon));
			p.setValidFlg(validFlg.isEmpty() ? "0" : validFlg);
			p.setUser(USER);
			return new Plan(index, op, () -> expectOne(masterRepository.insertSeason(p)));
		}

		long id = resolveTarget(op, idStr, key,
				() -> {
					requireNotEmpty(country, "国", key);
					requireNotEmpty(league, "リーグ", key);
					return masterRepository.findSeasonIds(country, league);
				},
				v -> masterRepository.countSeasonById(v) > 0);
		markSeen(seen, "id:" + id, key);
		if (!country.isEmpty() && !league.isEmpty()) {
			markSeen(seen, "k:" + norm(key), key);
		}

		switch (op) {
		case UPDATE: {
			boolean byId = !idStr.isEmpty();
			if (byId && !country.isEmpty() && !league.isEmpty()) {
				for (Long other : masterRepository.findSeasonIds(country, league)) {
					if (other.longValue() != id) {
						throw new RowException(key, "変更後の国・リーグが他の行（ID=" + other + "）と重複します。");
					}
				}
			}
			SeasonUploadParam p = new SeasonUploadParam();
			p.setId(id);
			if (byId) {
				p.setCountry(emptyToNull(country));
				p.setLeague(emptyToNull(league));
			}
			p.setSeasonYear(emptyToNull(seasonYear));
			p.setStartSeasonDate(start);
			p.setEndSeasonDate(end);
			p.setRound(emptyToNull(round));
			p.setPath(emptyToNull(path));
			p.setIcon(emptyToNull(icon));
			p.setValidFlg(emptyToNull(validFlg));
			p.setUser(USER);
			if (p.getCountry() == null && p.getLeague() == null && p.getSeasonYear() == null && p.getStartSeasonDate() == null
					&& p.getEndSeasonDate() == null && p.getRound() == null && p.getPath() == null && p.getIcon() == null
					&& p.getValidFlg() == null) {
				throw new RowException(key, "更新する項目がありません。");
			}
			return new Plan(index, op, () -> expectOne(masterRepository.updateSeason(p)));
		}
		case DELETE:
			return new Plan(index, op, () -> expectOne(masterRepository.deleteSeason(id)));
		case HIDE:
		case SHOW: {
			String flg = op == UploadOperation.HIDE ? FileUploadBatchConst.DEL_FLG_HIDE : FileUploadBatchConst.DEL_FLG_SHOW;
			return new Plan(index, op, () -> {
				expectOne(masterRepository.updateSeasonDelFlg(id, flg, USER));
				// 既存のシーズン更新（id 指定）と同じく、point_setting_master の del_flg も合わせる
				SeasonUploadParam cl = masterRepository.findSeasonById(id);
				if (cl != null) {
					masterRepository.updatePointSettingDelFlg(cl.getCountry(), cl.getLeague(), flg, USER);
				}
			});
		}
		default:
			throw new RowException(key, "処理区分が不正です。");
		}
	}

	// =========================================================
	// チーム（country_league_master）
	// =========================================================

	private Plan planTeam(Row r, Set<String> seen) {
		String country = r.get(FileUploadBatchConst.COL_COUNTRY);
		String league = r.get(FileUploadBatchConst.COL_LEAGUE);
		String team = r.get(FileUploadBatchConst.COL_TEAM);
		String link = r.get(FileUploadBatchConst.COL_LINK);
		String key = country + " / " + league + " / " + team;
		UploadOperation op = requireOperation(r.get(FileUploadBatchConst.COL_OPERATION), key);
		String idStr = r.get(FileUploadBatchConst.COL_ID);
		int index = r.index;

		if (op == UploadOperation.INSERT) {
			noId(idStr, op, key);
			requireNotEmpty(country, "国", key);
			requireNotEmpty(league, "リーグ", key);
			requireNotEmpty(team, "チーム", key);
			requireNotEmpty(link, "リンク", key);
			List<Long> ids = masterRepository.findTeamIds(country, league, team);
			if (!ids.isEmpty()) {
				throw new RowException(key, "既に登録されています。（ID=" + ids.get(0) + "）");
			}
			markSeen(seen, "k:" + norm(key), key);
			TeamUploadParam p = new TeamUploadParam();
			p.setCountry(country);
			p.setLeague(league);
			p.setTeam(team);
			p.setLink(link);
			p.setUser(USER);
			return new Plan(index, op, () -> expectOne(masterRepository.insertTeam(p)));
		}

		long id = resolveTarget(op, idStr, key,
				() -> {
					requireNotEmpty(country, "国", key);
					requireNotEmpty(league, "リーグ", key);
					requireNotEmpty(team, "チーム", key);
					return masterRepository.findTeamIds(country, league, team);
				},
				v -> masterRepository.countTeamById(v) > 0);
		markSeen(seen, "id:" + id, key);
		if (!country.isEmpty() && !league.isEmpty() && !team.isEmpty()) {
			markSeen(seen, "k:" + norm(key), key);
		}

		switch (op) {
		case UPDATE: {
			boolean byId = !idStr.isEmpty();
			if (byId && !country.isEmpty() && !league.isEmpty() && !team.isEmpty()) {
				for (Long other : masterRepository.findTeamIds(country, league, team)) {
					if (other.longValue() != id) {
						throw new RowException(key, "変更後の国・リーグ・チームが他の行（ID=" + other + "）と重複します。");
					}
				}
			}
			TeamUploadParam p = new TeamUploadParam();
			p.setId(id);
			if (byId) {
				p.setCountry(emptyToNull(country));
				p.setLeague(emptyToNull(league));
				p.setTeam(emptyToNull(team));
			}
			p.setLink(emptyToNull(link));
			p.setUser(USER);
			if (p.getCountry() == null && p.getLeague() == null && p.getTeam() == null && p.getLink() == null) {
				throw new RowException(key, "更新する項目がありません。");
			}
			return new Plan(index, op, () -> expectOne(masterRepository.updateTeam(p)));
		}
		case DELETE:
			return new Plan(index, op, () -> expectOne(masterRepository.deleteTeam(id)));
		case HIDE:
		case SHOW: {
			String flg = op == UploadOperation.HIDE ? FileUploadBatchConst.DEL_FLG_HIDE : FileUploadBatchConst.DEL_FLG_SHOW;
			return new Plan(index, op, () -> expectOne(masterRepository.updateTeamDelFlg(id, flg, USER)));
		}
		default:
			throw new RowException(key, "処理区分が不正です。");
		}
	}

	// =========================================================
	// 試合予定（future_master）
	// =========================================================

	/** 数値の列（CSV の列名） */
	private static final String[] FUTURE_NUMBER_COLUMNS = {
			FileUploadBatchConst.COL_HOME_RANK, FileUploadBatchConst.COL_AWAY_RANK,
			FileUploadBatchConst.COL_HOME_TEAM_HOME_SCORE, FileUploadBatchConst.COL_HOME_TEAM_HOME_LOST,
			FileUploadBatchConst.COL_AWAY_TEAM_HOME_SCORE, FileUploadBatchConst.COL_AWAY_TEAM_HOME_LOST,
			FileUploadBatchConst.COL_HOME_TEAM_AWAY_SCORE, FileUploadBatchConst.COL_HOME_TEAM_AWAY_LOST,
			FileUploadBatchConst.COL_AWAY_TEAM_AWAY_SCORE, FileUploadBatchConst.COL_AWAY_TEAM_AWAY_LOST,
	};

	private Plan planFuture(Row r, Set<String> seen) {
		String home = r.get(FileUploadBatchConst.COL_HOME_TEAM);
		String away = r.get(FileUploadBatchConst.COL_AWAY_TEAM);
		String category = r.get(FileUploadBatchConst.COL_GAME_TEAM_CATEGORY);
		String futureRaw = r.get(FileUploadBatchConst.COL_FUTURE_TIME);
		String gameLink = r.get(FileUploadBatchConst.COL_GAME_LINK);
		String key = home + " vs " + away + " / " + category;
		UploadOperation op = requireOperation(r.get(FileUploadBatchConst.COL_OPERATION), key);
		String idStr = r.get(FileUploadBatchConst.COL_ID);
		String futureTime = toTimestamp(futureRaw, "試合予定時間", key);
		String dataTime = toTimestamp(r.get(FileUploadBatchConst.COL_DATA_TIME), "データ取得時間", key);
		for (String col : FUTURE_NUMBER_COLUMNS) {
			String v = r.get(col);
			if (!v.isEmpty() && !NUMBER_PAT.matcher(v).matches()) {
				throw new RowException(key, col + "は数値で入力してください。");
			}
		}
		int index = r.index;

		if (op == UploadOperation.INSERT) {
			noId(idStr, op, key);
			requireNotEmpty(category, "試合国及びカテゴリ", key);
			requireCategoryFormat(category, key);
			requireNotEmpty(futureRaw, "試合予定時間", key);
			requireNotEmpty(home, "ホームチーム", key);
			requireNotEmpty(away, "アウェーチーム", key);
			requireNotEmpty(gameLink, "試合リンク文字列", key);
			List<Long> seqs = masterRepository.findFutureSeqs(home, away, category);
			if (!seqs.isEmpty()) {
				throw new RowException(key, "既に登録されています。（ID=" + seqs.get(0) + "）");
			}
			markSeen(seen, "k:" + norm(key), key);
			FutureUploadParam p = futureParam(r);
			p.setGameTeamCategory(category);
			p.setFutureTime(futureTime);
			p.setHomeTeamName(home);
			p.setAwayTeamName(away);
			p.setGameLink(gameLink);
			// データ取得時間は空欄なら現在日時（日本時間）
			p.setDataTime(dataTime != null ? dataTime : LocalDateTime.now(JST).format(OUT_TS));
			return new Plan(index, op, () -> expectOne(masterRepository.insertFuture(p)));
		}

		long seq = resolveTarget(op, idStr, key,
				() -> {
					requireNotEmpty(home, "ホームチーム", key);
					requireNotEmpty(away, "アウェーチーム", key);
					requireNotEmpty(category, "試合国及びカテゴリ", key);
					return masterRepository.findFutureSeqs(home, away, category);
				},
				v -> masterRepository.countFutureBySeq(v) > 0);
		markSeen(seen, "id:" + seq, key);
		if (!home.isEmpty() && !away.isEmpty() && !category.isEmpty()) {
			markSeen(seen, "k:" + norm(key), key);
		}

		switch (op) {
		case UPDATE: {
			boolean byId = !idStr.isEmpty();
			if (byId && !category.isEmpty()) {
				requireCategoryFormat(category, key);
			}
			if (byId && !home.isEmpty() && !away.isEmpty() && !category.isEmpty()) {
				for (Long other : masterRepository.findFutureSeqs(home, away, category)) {
					if (other.longValue() != seq) {
						throw new RowException(key, "変更後のホーム・アウェー・試合国及びカテゴリが他の行（ID=" + other + "）と重複します。");
					}
				}
			}
			FutureUploadParam p = futureParam(r);
			p.setSeq(seq);
			if (byId) {
				p.setGameTeamCategory(emptyToNull(category));
				p.setHomeTeamName(emptyToNull(home));
				p.setAwayTeamName(emptyToNull(away));
			}
			p.setFutureTime(futureTime);
			p.setGameLink(emptyToNull(gameLink));
			p.setDataTime(dataTime);
			if (isEmptyFutureUpdate(p)) {
				throw new RowException(key, "更新する項目がありません。");
			}
			return new Plan(index, op, () -> expectOne(masterRepository.updateFuture(p)));
		}
		case DELETE:
			return new Plan(index, op, () -> expectOne(masterRepository.deleteFuture(seq)));
		case HIDE:
		case SHOW: {
			String flg = op == UploadOperation.HIDE ? FileUploadBatchConst.DEL_FLG_HIDE : FileUploadBatchConst.DEL_FLG_SHOW;
			return new Plan(index, op, () -> expectOne(masterRepository.updateFutureDelFlg(seq, flg, USER)));
		}
		default:
			throw new RowException(key, "処理区分が不正です。");
		}
	}

	/** 試合予定の任意項目（空欄は null） */
	private static FutureUploadParam futureParam(Row r) {
		FutureUploadParam p = new FutureUploadParam();
		p.setHomeRank(emptyToNull(r.get(FileUploadBatchConst.COL_HOME_RANK)));
		p.setAwayRank(emptyToNull(r.get(FileUploadBatchConst.COL_AWAY_RANK)));
		p.setHomeMaxGettingScorer(emptyToNull(r.get(FileUploadBatchConst.COL_HOME_MAX_SCORER)));
		p.setAwayMaxGettingScorer(emptyToNull(r.get(FileUploadBatchConst.COL_AWAY_MAX_SCORER)));
		p.setHomeTeamHomeScore(emptyToNull(r.get(FileUploadBatchConst.COL_HOME_TEAM_HOME_SCORE)));
		p.setHomeTeamHomeLost(emptyToNull(r.get(FileUploadBatchConst.COL_HOME_TEAM_HOME_LOST)));
		p.setAwayTeamHomeScore(emptyToNull(r.get(FileUploadBatchConst.COL_AWAY_TEAM_HOME_SCORE)));
		p.setAwayTeamHomeLost(emptyToNull(r.get(FileUploadBatchConst.COL_AWAY_TEAM_HOME_LOST)));
		p.setHomeTeamAwayScore(emptyToNull(r.get(FileUploadBatchConst.COL_HOME_TEAM_AWAY_SCORE)));
		p.setHomeTeamAwayLost(emptyToNull(r.get(FileUploadBatchConst.COL_HOME_TEAM_AWAY_LOST)));
		p.setAwayTeamAwayScore(emptyToNull(r.get(FileUploadBatchConst.COL_AWAY_TEAM_AWAY_SCORE)));
		p.setAwayTeamAwayLost(emptyToNull(r.get(FileUploadBatchConst.COL_AWAY_TEAM_AWAY_LOST)));
		p.setUser(USER);
		return p;
	}

	private static boolean isEmptyFutureUpdate(FutureUploadParam p) {
		String[] values = { p.getGameTeamCategory(), p.getFutureTime(), p.getHomeRank(), p.getAwayRank(), p.getHomeTeamName(),
				p.getAwayTeamName(), p.getHomeMaxGettingScorer(), p.getAwayMaxGettingScorer(), p.getHomeTeamHomeScore(),
				p.getHomeTeamHomeLost(), p.getAwayTeamHomeScore(), p.getAwayTeamHomeLost(), p.getHomeTeamAwayScore(),
				p.getHomeTeamAwayLost(), p.getAwayTeamAwayScore(), p.getAwayTeamAwayLost(), p.getGameLink(), p.getDataTime() };
		for (String v : values) {
			if (v != null) {
				return false;
			}
		}
		return true;
	}

	// =========================================================
	// リアルタイム（static_data。非表示・再表示のみ）
	// =========================================================

	private Plan planRealtime(Row r, Set<String> seen) {
		String home = r.get(FileUploadBatchConst.COL_HOME_TEAM);
		String away = r.get(FileUploadBatchConst.COL_AWAY_TEAM);
		String category = r.get(FileUploadBatchConst.COL_DATA_CATEGORY);
		String key = home + " vs " + away + " / " + category;
		UploadOperation op = requireOperation(r.get(FileUploadBatchConst.COL_OPERATION), key);
		if (op != UploadOperation.HIDE && op != UploadOperation.SHOW) {
			throw new RowException(key, "リアルタイムデータは 4:非表示 / 5:再表示 だけ指定できます。");
		}
		requireNotEmpty(home, "ホームチーム", key);
		requireNotEmpty(away, "アウェーチーム", key);
		requireNotEmpty(category, "データカテゴリ", key);
		markSeen(seen, "k:" + norm(home + "|" + away + "|" + category), key);
		int cnt = realtimeRepository.countByKey(home, away, category);
		if (cnt == 0) {
			throw new RowException(key, "対象のデータがありません。");
		}
		String flg = op == UploadOperation.HIDE ? FileUploadBatchConst.DEL_FLG_HIDE : FileUploadBatchConst.DEL_FLG_SHOW;
		return new Plan(r.index, op, () -> {
			if (realtimeRepository.updateDelFlgByKey(home, away, category, flg, USER) == 0) {
				throw new IllegalStateException("対象のデータがありませんでした。");
			}
		});
	}

	// =========================================================
	// 部品
	// =========================================================

	/** ID の存在確認 */
	@FunctionalInterface
	private interface IdExists {
		boolean exists(long id);
	}

	/** キー検索 */
	@FunctionalInterface
	private interface IdFinder {
		List<Long> find();
	}

	/**
	 * 対象の ID を決める
	 * <ul>
	 *   <li>削除・非表示・再表示: キーだけで決める（ID は使わない）。0 件・2 件以上はエラー。</li>
	 *   <li>更新: ID があれば ID（存在すること）、無ければキーで決める。</li>
	 * </ul>
	 */
	private static long resolveTarget(UploadOperation op, String idStr, String key, IdFinder finder, IdExists exists) {
		if (op != UploadOperation.UPDATE) {
			noId(idStr, op, key);
			return single(finder.find(), key, "対象を決められません。");
		}
		if (!idStr.isEmpty()) {
			if (!DIGITS_PAT.matcher(idStr).matches()) {
				throw new RowException(key, "ID は数字で入力してください。");
			}
			long id;
			try {
				id = Long.parseLong(idStr);
			} catch (NumberFormatException e) {
				throw new RowException(key, "ID が大きすぎます。");
			}
			if (!exists.exists(id)) {
				throw new RowException(key, "ID=" + id + " のデータがありません。");
			}
			return id;
		}
		return single(finder.find(), key, "ID を指定してください。");
	}

	private static long single(List<Long> ids, String key, String whenMany) {
		if (ids == null || ids.isEmpty()) {
			throw new RowException(key, "対象のデータがありません。");
		}
		if (ids.size() > 1) {
			throw new RowException(key, "キーに該当するデータが " + ids.size() + " 件あるため、" + whenMany + "（ID=" + ids + "）");
		}
		return ids.get(0);
	}

	private static UploadOperation requireOperation(String value, String key) {
		UploadOperation op = UploadOperation.of(value);
		if (op == null) {
			throw new RowException(key, "処理区分が不正です。（1:登録 2:更新 3:削除 4:非表示 5:再表示）");
		}
		return op;
	}

	private static void noId(String idStr, UploadOperation op, String key) {
		if (idStr != null && !idStr.isEmpty()) {
			throw new RowException(key, op.getLabel() + "では ID は使いません。ID を空欄にして、キーで指定してください。");
		}
	}

	private static void requireNotEmpty(String value, String label, String key) {
		if (value == null || value.isEmpty()) {
			throw new RowException(key, label + "が入力されていません。");
		}
	}

	private static void requireCategoryFormat(String category, String key) {
		if (!CATEGORY_PAT.matcher(category).matches()) {
			throw new RowException(key, "試合国及びカテゴリは「国: リーグ - ラウンド N」の形で入力してください。"
					+ "（「:」「-」・スペース・数字はすべて半角。「: 」と「 - 」のスペースは 1 つ。例: 日本: J1リーグ - ラウンド 30）");
		}
	}

	private static void markSeen(Set<String> seen, String k, String key) {
		if (!seen.add(k)) {
			throw new RowException(key, "同じ対象がファイル内で重複しています。");
		}
	}

	/** 反映で 1 行だけ変わったこと（0 行なら対象が消えている → 全部戻す） */
	private static void expectOne(int count) {
		if (count == 0) {
			throw new IllegalStateException("対象の行が見つかりませんでした。");
		}
	}

	/**
	 * 日時の文字列を「yyyy-MM-ddTHH:mm:ss+09:00」にする（日本時間として扱う）
	 * <p>yyyy-MM-dd / yyyy/MM/dd、時刻 HH:mm / HH:mm:ss、間は空白か T。月日・時は 1 桁も可（web と同じ読み方）。</p>
	 * @return 空なら null
	 */
	static String toTimestamp(String value, String label, String key) {
		String s = value == null ? "" : value.strip();
		if (s.isEmpty()) {
			return null;
		}
		s = s.replace('/', '-').replace('T', ' ').replaceAll("\\s+", " ");
		try {
			if (s.contains(" ")) {
				String pattern = s.split(" ")[1].split(":").length == 3 ? "uuuu-M-d H:mm:ss" : "uuuu-M-d H:mm";
				LocalDateTime t = LocalDateTime.parse(s, DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT));
				return t.format(OUT_TS);
			}
			LocalDate d = LocalDate.parse(s, DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT));
			return d.atStartOfDay().format(OUT_TS);
		} catch (DateTimeParseException | ArrayIndexOutOfBoundsException e) {
			throw new RowException(key, label + "の形式が正しくありません。（例: 2026-10-18 または 2026-10-18 19:30）");
		}
	}

	private static String emptyToNull(String s) {
		return s == null || s.isEmpty() ? null : s;
	}

	/** ファイル内の重複判定用（全角・半角の揺れをそろえる） */
	private static String norm(String s) {
		return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFKC).strip();
	}

	/** 例外の一番元のメッセージ（長すぎるときは切る） */
	private static String rootMessage(Throwable e) {
		Throwable t = e;
		while (t != null && t.getCause() != null && t.getCause() != t) {
			t = t.getCause();
		}
		String m = t == null ? "" : (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
		m = m.replaceAll("\\s+", " ").strip();
		return m.length() > 200 ? m.substring(0, 200) + "…" : m;
	}
}
