package dev.common.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.BookDataEntity;
import dev.common.exception.BusinessException;



/**
 * データ整理共通クラス
 * @author shiraishitoshio
 *
 */
public class ExecuteMainUtil {

	/**
	 * 特徴量に関するリストサイズ
	 */
	private static final Integer FEATURE_SIZE_INTEGER = 4;

	/**
	 * 分割リスト
	 */
	private static final List<String> SPLIT_LIST;
	static {
		List<String> list = new ArrayList<>();
		list.add("ホームパス数");
		list.add("アウェーパス数");
		list.add("ホームファイナルサードパス数");
		list.add("アウェーファイナルサードパス数");
		list.add("ホームクロス数");
		list.add("アウェークロス数");
		list.add("ホームタックル数");
		list.add("アウェータックル数");
		SPLIT_LIST = Collections.unmodifiableList(list);
	}

	/**
	 * 除去リスト
	 */
	private static final List<String> SPLIT_RONRI_LIST;
	static {
		List<String> list = new ArrayList<>();
		list.add("home_pass_count");
		list.add("away_pass_count");
		list.add("home_final_third_pass_count");
		list.add("away_final_third_pass_count");
		list.add("home_cross_count");
		list.add("away_cross_count");
		list.add("home_tackle_count");
		list.add("away_tackle_count");
		SPLIT_RONRI_LIST = Collections.unmodifiableList(list);
	}

	/**
	 * 除去リスト
	 */
	private static final List<String> EXCLUSIVE_LIST;;
	static {
		List<String> list = new ArrayList<>();
		list.add("seq");
		list.add("condition_result_data_seq_id");
		list.add("times");
		list.add("home_score");
		list.add("away_score");
		list.add("home_rank");
		list.add("away_rank");
		list.add("home_team_name");
		list.add("away_team_name");
		list.add("data_category");
		list.add("weather");
		list.add("temparature");
		list.add("humid");
		list.add("record_time");
		list.add("judge_member");
		list.add("home_manager");
		list.add("away_manager");
		list.add("home_formation");
		list.add("away_formation");
		list.add("studium");
		list.add("audience");
		list.add("capacity");
		list.add("home_max_getting_scorer");
		list.add("away_max_getting_scorer");
		list.add("home_max_getting_scorer_game_situation");
		list.add("away_max_getting_scorer_game_situation");
		list.add("notice_flg");
		list.add("goal_time");
		list.add("goal_team_member");
		list.add("judge");
		list.add("home_team_style");
		list.add("away_team_style");
		list.add("probablity");
		list.add("prediction_score_time");
		EXCLUSIVE_LIST = Collections.unmodifiableList(list);
	}

	/**
	 * 除去リスト
	 */
	private static final List<String> EXCLUSIVE_LIST2;
	static {
		List<String> list = new ArrayList<>();
		list.add("seq");
		list.add("condition_result_data_seq_id");
		list.add("times");
		list.add("home_score");
		list.add("away_score");
		list.add("home_rank");
		list.add("away_rank");
		list.add("home_team_name");
		list.add("away_team_name");
		list.add("data_category");
		list.add("weather");
		list.add("temparature");
		list.add("humid");
		list.add("record_time");
		list.add("judge_member");
		list.add("home_manager");
		list.add("away_manager");
		list.add("home_formation");
		list.add("away_formation");
		list.add("studium");
		list.add("audience");
		list.add("capacity");
		list.add("home_max_getting_scorer");
		list.add("away_max_getting_scorer");
		list.add("home_max_getting_scorer_game_situation");
		list.add("away_max_getting_scorer_game_situation");
		list.add("home_team_home_score");
		list.add("home_team_home_lost");
		list.add("away_team_home_score");
		list.add("away_team_home_lost");
		list.add("home_team_away_score");
		list.add("home_team_away_lost");
		list.add("away_team_away_score");
		list.add("away_team_away_lost");
		list.add("notice_flg");
		list.add("goal_time");
		list.add("goal_team_member");
		list.add("judge");
		list.add("home_team_style");
		list.add("away_team_style");
		list.add("probablity");
		list.add("prediction_score_time");
		EXCLUSIVE_LIST2 = Collections.unmodifiableList(list);
	}

	/**
	 * コンストラクタ作成禁止
	 */
	private ExecuteMainUtil() {

	}

	/**
	 * 数字部分を抽出するメソッド
	 * @param label
	 * @return
	 */
	public static double extractNumericValue(String label) {
		try {
			String numStr = label.replaceAll("[^\\d.]", "");
			return Double.parseDouble(numStr);
		} catch (NumberFormatException e) {
			return Double.MAX_VALUE; // 不明な形式は最後に回す
		}
	}

	/**
	 * feature_name（特徴量変数名）に当たるフィールドをbutsuriListから探索し、それに該当するronriListを返却する
	 * @param feature_name
	 * @param ronriList
	 * @param butsuriList
	 * @return
	 */
	public static String getFeatureRonriField(String feature_name, List<String> ronriList, List<String> butsuriList) {
		for (int i = 0; i < ronriList.size(); i++) {
			if (feature_name.equals(butsuriList.get(i))) {
				return ronriList.get(i);
			}
		}
		throw new BusinessException(null, null, null, feature_name + ": フィールド名に該当する論理名が見つかりません。");
	}

	/**
	 * 除去リストに入っている項目かをチェック
	 * @param exclusive
	 * @return boolean
	 */
	public static boolean chkExclusive(String exclusive) {
		return (EXCLUSIVE_LIST.contains(exclusive)) ? true : false;
	}

	/**
	 * 除去リストに入っている項目かをチェック
	 * @param exclusive
	 * @return boolean
	 */
	public static boolean chkExclusive2(String exclusive) {
		return (EXCLUSIVE_LIST2.contains(exclusive)) ? true : false;
	}

	/**
	 * 分割リストに入っている項目かをチェック
	 * @param split
	 * @return boolean
	 */
	public static boolean chkSplit(String split) {
		return (SPLIT_LIST.contains(split)) ? true : false;
	}

	/**
	 * 分割リストに入っている項目かをチェック
	 * @param split
	 * @return boolean
	 */
	public static boolean chkRonriSplit(String split) {
		return (SPLIT_RONRI_LIST.contains(split)) ? true : false;
	}

	/**
	 * リストのサイズを減らすために入れ替えるメソッド
	 * @param home_swap_source_int 入れ替え元存在データインデックス
	 * @param away_swap_source_int 入れ替え元存在データインデックス
	 * @param dataInitList 入れ替え元リスト
	 * @return
	 */
	public static String[][] convertDataList(int home_swap_source_int, int away_swap_source_int,
			String[][][][] dataInitList) {
		int feature_size = dataInitList.length;
		String[][] convList = new String[feature_size][FEATURE_SIZE_INTEGER];
		for (int feature_int = 0; feature_int < feature_size; feature_int++) {
			for (int feature_size_int = 0; feature_size_int < FEATURE_SIZE_INTEGER; feature_size_int++) {
				convList[feature_int][feature_size_int] = dataInitList[feature_int][home_swap_source_int][away_swap_source_int][feature_size_int];
			}
		}
		return convList;
	}

	/**
	 * データ国及びカテゴリから国とリーグ名を取得する
	 * @param data_category
	 */
	public static List<String> getCountryLeagueByRegex(String data_category) {
		// 正規表現で国名とチーム名を抽出
		String regex = "(.*?)\\s*:\\s*(.*?)\\s*-";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(data_category);
		String country = "";
		String league = "";
		if (matcher.find()) {
			// 国名とチーム名を取得
			country = matcher.group(1);
			league = matcher.group(2);

			//System.out.println("国名: " + country);
			//System.out.println("チーム名: " + league);
			List<String> list = new ArrayList<>();
			list.add(country);
			list.add(league);
			return list;
		} else {
			throw new BusinessException(null, null, null, data_category + ": マッチする部分が見つかりませんでした。");
		}
	}

	/**
	 * 以下の特徴量の値を分割する
	 * @param feature_value
	 */
	public static List<String> splitGroup(String feature_value) {
		List<String> list = new ArrayList<>();
		if (feature_value == null || "".equals(feature_value)) {
			list.add("");
			list.add("");
			list.add("");
			return list;
		}

		String percentage = feature_value.split("%")[0] + "%"; // "1.22892389%"
		String values = "";
		String value1 = "";
		String value2 = "";
		if (feature_value.contains("(")) {
			// 数値部分を抽出 (括弧内の部分)
			values = feature_value.replace(percentage, "").trim(); // "231.12121212/256.1212121212"
			values = values.replace("(", "");
			values = values.replace(")", "");

			// スラッシュで分けて値を抽出
			String[] valueParts = values.split("/");
			value1 = valueParts[0]; // "231.12121212"
			value2 = valueParts[1]; // "256.1212121212"
		}
		list.add(percentage);
		list.add(value1);
		list.add(value2);
		//System.out.println(percentage + "," + value1 + "," + value2);
		return list;
	}

	/**
	 * 以下の特徴量の値を分割する
	 * @param feature_value
	 */
	public static List<String> splitFlgGroup(String feature_value) {
		List<String> list = new ArrayList<>();
		if (feature_value == null || "".equals(feature_value)) {
			list.add("");
			list.add("");
			list.add("");
			return list;
		}

		if (feature_value.contains("(")) {
			// 数値部分を抽出 (括弧内の部分)
			String[] parts = feature_value.replace("(", "").replace(")", "").replace("/", " ").split(" ");
			list.add(parts[0]);
			list.add(parts[1]);
			list.add(parts[2]);
		}
		//System.out.println(percentage + "," + value1 + "," + value2);
		return list;
	}

	/**
	 * 時間帯の範囲を取得する
	 * @param matchTime
	 * @return
	 */
	public static String classifyMatchTime(String matchTime) {
		// 時間帯を格納する配列
		String[] timeSlots = { "0〜10", "10〜20", "20〜30", "30〜40", "40〜45",
				"45〜50", "50〜60", "60〜70", "70〜80", "80〜90", "90〜" };

		if (BookMakersCommonConst.HALF_TIME.equals(matchTime) ||
				BookMakersCommonConst.FIRST_HALF_TIME.equals(matchTime)) {
			return "45〜50";
		}

		// 時間を分単位に変換
		double timeInMinutes = convertToMinutes(matchTime);

		// 時間帯に分類
		for (int i = 0; i < timeSlots.length; i++) {
			String[] range = timeSlots[i].split("〜");
			int start = Integer.parseInt(range[0]);
			int end = (i == timeSlots.length - 1) ? Integer.MAX_VALUE : Integer.parseInt(range[1]);

			if (timeInMinutes >= start && timeInMinutes < end) {
				return timeSlots[i];
			}
		}

		return "不明な時間帯";
	}

	/**
	 * 時間をdouble型に変換する
	 * @param matchTime
	 * @return
	 */
	public static double convertToMinutes(String matchTime) {
		//System.out.println("convertToMinutes, matchTime: " + matchTime);
		if (matchTime.equals("45+'") || BookMakersCommonConst.HALF_TIME.equals(matchTime) ||
				BookMakersCommonConst.FIRST_HALF_TIME.equals(matchTime)) {
			return 45.0;
		}
		if (matchTime.equals("90+'") || BookMakersCommonConst.FIN.equals(matchTime)) {
			return 90.0;
		}
		if (matchTime.contains(":")) {
			// "34:56"の形式 (分:秒)
			String[] parts = matchTime.split(":");
			int minutes = Integer.parseInt(parts[0]);
			int seconds = Integer.parseInt(parts[1]);
			return minutes + seconds / 60.0;
		} else if (matchTime.contains("+")) {
			// "45+8'"の形式 (分+秒)
			String[] parts = matchTime.split("\\+");
			if (parts.length == 2) {
				int minutes = Integer.parseInt(parts[0].replace("'", ""));
				int seconds = Integer.parseInt(parts[1].replace("'", ""));
				return minutes + seconds / 60.0;
			} else {
				int minutes = Integer.parseInt(parts[0].replace("'", ""));
				return minutes;
			}
		} else if (matchTime.contains(".") && matchTime.endsWith("'")) {
			return Double.parseDouble(matchTime.replace("'", ""));
		} else if (matchTime.endsWith("'")) {
			// "45'"の形式 (分')
			int minutes = Integer.parseInt(matchTime.replace("'", ""));
			return minutes;
		}
		return 0.0;
	}

	/**
	 * 文字列の形式をチェックし、相応の切り捨て処理を行う
	 * @param input
	 * @return
	 */
	public static String checkNumberTypeAndFloor(String input) {
		input = input.trim();
		// パーセンテージ（整数または小数のあとに % がついている）
		if (input.matches("^\\d+(\\.\\d+)?%$")) {
			String numStr = input.replace("%", "");
			double value = Double.parseDouble(numStr);
			int percentBase = (int) value / 10 * 10;
			return percentBase + "%";
		}
		// 小数（. が含まれていて数字）
		if (input.matches("^\\d+\\.\\d+$")) {
			double value = Double.parseDouble(input);
			int intPart = (int) value;
			int decimalFirstDigit = (int) ((value - intPart) * 10);
			return intPart + "." + decimalFirstDigit;
		}
		// 整数
		if (input.matches("^\\d+$")) {
			int value = Integer.parseInt(input);
			int base = (value < 10) ? 0 : (value / 10 * 10);
			return String.valueOf(base);
		}
		return null;
	}

	/**
	 * キャメル方式の文字列をスネーク方式に直す
	 * @param camelCase
	 * @return snakeCase
	 */
	public static String convertToSnakeCase(String camelCase) {
		StringBuilder snakeCase = new StringBuilder();

		// 各文字をループで処理
		for (int i = 0; i < camelCase.length(); i++) {
			char c = camelCase.charAt(i);
			// 大文字の場合、アンダースコアを追加して小文字に変換
			if (Character.isUpperCase(c)) {
				if (i != 0) {
					snakeCase.append("_");
				}
				snakeCase.append(Character.toLowerCase(c));
			} else {
				// 小文字の場合はそのまま追加
				snakeCase.append(c);
			}
		}

		return snakeCase.toString();
	}

	/**
	 * 国とリーグに分ける
	 * @param text
	 * @return
	 */
	public static String[] splitLeagueInfo(String text) {
		if (text.contains(": ")) {
			String[] mainParts = text.split(": ", 2);
			if (mainParts.length == 2) {
				String country = mainParts[0];
				String league = mainParts[1].split(" - ")[0];
				return new String[] { country, league };
			}
		}
		return null;
	}

	/**
	 * 最大の通番を持つEntityを返す
	 * @param entityList
	 * @return
	 */
	public static BookDataEntity getMaxSeqEntities(List<BookDataEntity> entityList) {
		int seq = -1;
		BookDataEntity returnEntity = null;
		for (BookDataEntity entity : entityList) {
			// 取得エラーはスキップ
			if (BookMakersCommonConst.GET_UNEXPECTED_ERROR.equals(entity.getGoalTime()) ||
					BookMakersCommonConst.GET_UNEXPECTED_ERROR.equals(entity.getGoalTeamMember())) {
				continue;
			}
			int seqs = Integer.parseInt(entity.getSeq());
			if (seq < seqs) {
				seq = seqs;
				returnEntity = entity;
			}
			if (BookMakersCommonConst.FIN.equals(entity.getTime())) {
				return entity;
			}
		}
		return returnEntity;
	}

	/**
	 * ハーフタイムを持つEntityを返す
	 * @param entityList
	 * @return
	 */
	public static BookDataEntity getHalfEntities(List<BookDataEntity> entityList) {
		BookDataEntity returnEntity = null;
		for (BookDataEntity entity : entityList) {
			if (BookMakersCommonConst.HALF_TIME.equals(entity.getTime()) ||
					BookMakersCommonConst.FIRST_HALF_TIME.equals(entity.getTime())) {
				return entity;
			}
		}
		return returnEntity;
	}

	/**
	 * 最小の通番を持つEntityを返す
	 * @param entityList
	 * @return
	 */
	public static BookDataEntity getMinSeqEntities(List<BookDataEntity> entityList) {
		int seq = Integer.MAX_VALUE;
		BookDataEntity returnEntity = null;
		for (BookDataEntity entity : entityList) {
			// 取得エラーはスキップ
			if (BookMakersCommonConst.GET_UNEXPECTED_ERROR.equals(entity.getGoalTime()) ||
					BookMakersCommonConst.GET_UNEXPECTED_ERROR.equals(entity.getGoalTeamMember())) {
				continue;
			}
			int seqs = Integer.parseInt(entity.getSeq());
			if (seq > seqs) {
				seq = seqs;
				returnEntity = entity;
			}
		}
		return returnEntity;
	}

}
