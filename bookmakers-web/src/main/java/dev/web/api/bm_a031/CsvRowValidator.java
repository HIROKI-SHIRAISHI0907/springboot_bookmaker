package dev.web.api.bm_a031;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * CSV の行チェック（web 側のバリデーション。DB は見ない）
 *
 * <p>ここで見るのは、値の形・必須項目・処理区分ごとの決まり・ファイル内の重複まで。
 *    キーが DB にあるか・重複していないかなどの DB チェックと反映はバッチサービスで行う。</p>
 *
 * <p>登録で必須の項目</p>
 * <ul>
 *   <li>シーズン: 処理区分・国・リーグ・シーズン年・シーズン開始日・シーズン終了日・ラウンド数・パス</li>
 *   <li>チーム: 処理区分・国・リーグ・チーム・リンク</li>
 *   <li>試合予定: 処理区分・試合国及びカテゴリ（「国: リーグ - ラウンド N」。区切りは半角のみ）・試合予定時間・
 *       ホームチーム・アウェーチーム・試合リンク文字列（データ取得時間は空欄ならバッチで現在日時）</li>
 *   <li>リアルタイム: 処理区分（4・5 のみ）・ホームチーム・アウェーチーム・データカテゴリ</li>
 * </ul>
 * <p>削除・非表示・再表示はキーだけで対象を決める（ID は空欄）。更新は ID かキーで決める。</p>
 *
 * @author shiraishitoshio
 */
public final class CsvRowValidator {

	private static final Pattern NUMBER_PAT = Pattern.compile("^-?\\d+(\\.\\d+)?$");
	private static final Pattern DIGITS_PAT = Pattern.compile("^\\d+$");

	/**
	 * 試合国及びカテゴリの形
	 * <p>「国: リーグ - ラウンド N」。「:」の後と「-」の前後は半角スペース 1 つ、「:」「-」も半角、N は半角数字。
	 *    全角の記号・全角スペース・余分なスペースは認めない。</p>
	 */
	public static final Pattern CATEGORY_PAT = Pattern.compile(
			"^[^:：\\s　](?:[^:：]*[^:：\\s　])?: [^\\s　](?:.*[^\\s　])? - ラウンド [0-9]+$");

	private CsvRowValidator() {}

	/** 1 行のチェック結果 */
	public static class Result {
		private final String key;
		private final List<String> errors = new ArrayList<>();

		Result(String key) {
			this.key = key;
		}

		public String getKey() {
			return key;
		}

		public List<String> getErrors() {
			return errors;
		}

		public boolean hasError() {
			return !errors.isEmpty();
		}

		void add(String message) {
			errors.add(message);
		}
	}

	// =========================================================
	// シーズン
	// =========================================================

	public static Result season(SeasonCsvRow row, Set<String> seen) {
		String country = strip(row.getCountry());
		String league = strip(row.getLeague());
		Result r = new Result(country + " / " + league);
		CsvOperation op = operation(row.getOperation(), r);
		if (op == null) {
			return r;
		}

		String start = strip(row.getStartSeasonDate());
		String end = strip(row.getEndSeasonDate());
		LocalDateTime startAt = dateTime(start, "シーズン開始日", r);
		LocalDateTime endAt = dateTime(end, "シーズン終了日", r);
		String round = strip(row.getRound());
		if (!round.isEmpty() && !DIGITS_PAT.matcher(round).matches()) {
			r.add("ラウンド数は数字で入力してください。");
		}
		String validFlg = strip(row.getValidFlg());
		if (!validFlg.isEmpty() && !validFlg.equals("0") && !validFlg.equals("1")) {
			r.add("有効フラグは 0 または 1 で入力してください。");
		}
		if (startAt != null && endAt != null && startAt.isAfter(endAt)) {
			r.add("シーズン終了日がシーズン開始日より前になっています。");
		}

		switch (op) {
		case INSERT -> {
			noId(row.getId(), op, r);
			required(country, "国", r);
			required(league, "リーグ", r);
			required(strip(row.getSeasonYear()), "シーズン年", r);
			required(start, "シーズン開始日", r);
			required(end, "シーズン終了日", r);
			required(round, "ラウンド数", r);
			required(strip(row.getPath()), "パス", r);
		}
		case UPDATE -> {
			if (!idOrKey(row.getId(), r, country, league)) {
				r.add("更新では ID か、国・リーグを入力してください。");
			}
			boolean byKey = strip(row.getId()).isEmpty();
			boolean any = anyNotEmpty(row.getSeasonYear(), start, end, round, row.getPath(), row.getIcon(), validFlg)
					|| (!byKey && anyNotEmpty(country, league));
			if (!any) {
				r.add("更新する項目がありません。");
			}
		}
		case DELETE, HIDE, SHOW -> {
			noId(row.getId(), op, r);
			required(country, "国", r);
			required(league, "リーグ", r);
		}
		}
		duplicate(seen, op, row.getId(), r);
		return r;
	}

	// =========================================================
	// チーム
	// =========================================================

	public static Result team(TeamCsvRow row, Set<String> seen) {
		String country = strip(row.getCountry());
		String league = strip(row.getLeague());
		String team = strip(row.getTeam());
		Result r = new Result(country + " / " + league + " / " + team);
		CsvOperation op = operation(row.getOperation(), r);
		if (op == null) {
			return r;
		}

		switch (op) {
		case INSERT -> {
			noId(row.getId(), op, r);
			required(country, "国", r);
			required(league, "リーグ", r);
			required(team, "チーム", r);
			required(strip(row.getLink()), "リンク", r);
		}
		case UPDATE -> {
			if (!idOrKey(row.getId(), r, country, league, team)) {
				r.add("更新では ID か、国・リーグ・チームを入力してください。");
			}
			boolean byKey = strip(row.getId()).isEmpty();
			if (!anyNotEmpty(row.getLink()) && (byKey || !anyNotEmpty(country, league, team))) {
				r.add("更新する項目がありません。");
			}
		}
		case DELETE, HIDE, SHOW -> {
			noId(row.getId(), op, r);
			required(country, "国", r);
			required(league, "リーグ", r);
			required(team, "チーム", r);
		}
		}
		duplicate(seen, op, row.getId(), r);
		return r;
	}

	// =========================================================
	// 試合予定
	// =========================================================

	public static Result future(FutureCsvRow row, Set<String> seen) {
		String home = strip(row.getHomeTeamName());
		String away = strip(row.getAwayTeamName());
		String category = strip(row.getGameTeamCategory());
		Result r = new Result(home + " vs " + away + " / " + category);
		CsvOperation op = operation(row.getOperation(), r);
		if (op == null) {
			return r;
		}

		String futureTime = strip(row.getFutureTime());
		dateTime(futureTime, "試合予定時間", r);
		dateTime(strip(row.getDataTime()), "データ取得時間", r);
		number(row.getHomeRank(), "ホーム順位", r);
		number(row.getAwayRank(), "アウェー順位", r);
		number(row.getHomeTeamHomeScore(), "ホームチームホーム得点", r);
		number(row.getHomeTeamHomeLost(), "ホームチームホーム失点", r);
		number(row.getAwayTeamHomeScore(), "アウェーチームホーム得点", r);
		number(row.getAwayTeamHomeLost(), "アウェーチームホーム失点", r);
		number(row.getHomeTeamAwayScore(), "ホームチームアウェー得点", r);
		number(row.getHomeTeamAwayLost(), "ホームチームアウェー失点", r);
		number(row.getAwayTeamAwayScore(), "アウェーチームアウェー得点", r);
		number(row.getAwayTeamAwayLost(), "アウェーチームアウェー失点", r);

		switch (op) {
		case INSERT -> {
			noId(row.getId(), op, r);
			required(category, "試合国及びカテゴリ", r);
			categoryFormat(category, r);
			required(futureTime, "試合予定時間", r);
			required(home, "ホームチーム", r);
			required(away, "アウェーチーム", r);
			required(strip(row.getGameLink()), "試合リンク文字列", r);
		}
		case UPDATE -> {
			if (!idOrKey(row.getId(), r, home, away, category)) {
				r.add("更新では ID か、ホームチーム・アウェーチーム・試合国及びカテゴリを入力してください。");
			}
			boolean byKey = strip(row.getId()).isEmpty();
			// ID 指定でカテゴリを書き換えるときは形をチェックする（キー指定のときは既存の値と同じ）
			if (!byKey) {
				categoryFormat(category, r);
			}
			boolean any = anyNotEmpty(futureTime, row.getHomeRank(), row.getAwayRank(), row.getHomeMaxGettingScorer(),
					row.getAwayMaxGettingScorer(), row.getHomeTeamHomeScore(), row.getHomeTeamHomeLost(),
					row.getAwayTeamHomeScore(), row.getAwayTeamHomeLost(), row.getHomeTeamAwayScore(),
					row.getHomeTeamAwayLost(), row.getAwayTeamAwayScore(), row.getAwayTeamAwayLost(), row.getGameLink(),
					row.getDataTime())
					|| (!byKey && anyNotEmpty(home, away, category));
			if (!any) {
				r.add("更新する項目がありません。");
			}
		}
		case DELETE, HIDE, SHOW -> {
			noId(row.getId(), op, r);
			required(home, "ホームチーム", r);
			required(away, "アウェーチーム", r);
			required(category, "試合国及びカテゴリ", r);
		}
		}
		duplicate(seen, op, row.getId(), r);
		return r;
	}

	// =========================================================
	// リアルタイム
	// =========================================================

	public static Result realtime(RealtimeCsvRow row, Set<String> seen) {
		String home = strip(row.getHomeTeamName());
		String away = strip(row.getAwayTeamName());
		String category = strip(row.getDataCategory());
		Result r = new Result(home + " vs " + away + " / " + category);
		CsvOperation op = operation(row.getOperation(), r);
		if (op == null) {
			return r;
		}
		if (op != CsvOperation.HIDE && op != CsvOperation.SHOW) {
			r.add("リアルタイムデータは 4:非表示 / 5:再表示 だけ指定できます。");
		}
		required(home, "ホームチーム", r);
		required(away, "アウェーチーム", r);
		required(category, "データカテゴリ", r);
		duplicate(seen, op, null, r);
		return r;
	}

	// =========================================================
	// 部品
	// =========================================================

	private static CsvOperation operation(String value, Result r) {
		CsvOperation op = CsvOperation.of(value);
		if (op == null) {
			r.add("処理区分が不正です。（1:登録 2:更新 3:削除 4:非表示 5:再表示）");
		}
		return op;
	}

	private static void required(String value, String label, Result r) {
		if (value == null || value.isEmpty()) {
			r.add(label + "が入力されていません。");
		}
	}

	private static void noId(String id, CsvOperation op, Result r) {
		if (!strip(id).isEmpty()) {
			r.add(op.getLabel() + "では ID は使いません。ID を空欄にして、キーで指定してください。");
		}
	}

	/** 更新: ID（数字）か、キーがすべて入っていること */
	private static boolean idOrKey(String id, Result r, String... keys) {
		String s = strip(id);
		if (!s.isEmpty()) {
			if (!DIGITS_PAT.matcher(s).matches()) {
				r.add("ID は数字で入力してください。");
			}
			return true;
		}
		for (String k : keys) {
			if (k == null || k.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private static void categoryFormat(String category, Result r) {
		if (!category.isEmpty() && !CATEGORY_PAT.matcher(category).matches()) {
			r.add("試合国及びカテゴリは「国: リーグ - ラウンド N」の形で入力してください。"
					+ "（「:」「-」・スペース・数字はすべて半角。「: 」と「 - 」のスペースは 1 つ。例: 日本: J1リーグ - ラウンド 30）");
		}
	}

	private static void number(String value, String label, Result r) {
		String v = strip(value);
		if (!v.isEmpty() && !NUMBER_PAT.matcher(v).matches()) {
			r.add(label + "は数値で入力してください。");
		}
	}

	/** ファイル内の重複（更新の ID 指定は ID、それ以外はキーで見る） */
	private static void duplicate(Set<String> seen, CsvOperation op, String id, Result r) {
		String idStr = strip(id);
		String k = op == CsvOperation.UPDATE && !idStr.isEmpty() ? "id:" + idStr : "k:" + norm(r.getKey());
		if (!seen.add(k)) {
			r.add("同じ対象がファイル内で重複しています。");
		}
	}

	/**
	 * 日時のチェック（yyyy-MM-dd / yyyy/MM/dd、時刻 HH:mm / HH:mm:ss、間は空白か T。月日・時は 1 桁も可）
	 * @return 空・形が違うときは null
	 */
	public static LocalDateTime dateTime(String value, String label, Result r) {
		String s = strip(value);
		if (s.isEmpty()) {
			return null;
		}
		LocalDateTime t = parseDateTime(s);
		if (t == null && r != null) {
			r.add(label + "の形式が正しくありません。（例: 2026-10-18 または 2026-10-18 19:30）");
		}
		return t;
	}

	/** 日時の文字列を読む（形が違えば null）。バッチ側でも同じ読み方をする */
	public static LocalDateTime parseDateTime(String value) {
		String s = strip(value).replace('/', '-').replace('T', ' ').replaceAll("\\s+", " ");
		if (s.isEmpty()) {
			return null;
		}
		try {
			if (s.contains(" ")) {
				String pattern = s.split(" ")[1].split(":").length == 3 ? "uuuu-M-d H:mm:ss" : "uuuu-M-d H:mm";
				return LocalDateTime.parse(s, DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT));
			}
			return LocalDate.parse(s, DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT))
					.atStartOfDay();
		} catch (DateTimeParseException | ArrayIndexOutOfBoundsException e) {
			return null;
		}
	}

	private static boolean anyNotEmpty(String... values) {
		for (String v : values) {
			if (v != null && !v.strip().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static String strip(String s) {
		return s == null ? "" : s.strip();
	}

	/** ファイル内の重複判定用（全角・半角の揺れをそろえる） */
	private static String norm(String s) {
		return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFKC).strip();
	}
}