package dev.application.analyze.common.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.application.constant.TableConst;

/**
 * テーブルユーティル
 * @author shiraishitoshio
 *
 */
public class TableUtil {

	/** フィールドマップ */
	private static final List<String> COUNTRYMAP;
	static {
		List<String> fieldMap = new ArrayList<>();
		fieldMap.add(TableConst.CALC_CORRELATION);
		fieldMap.add(TableConst.CALC_CORRELATION_RANKING);
		fieldMap.add(TableConst.COUNTRY_LEAGUE_SUMMARY);
		fieldMap.add(TableConst.EACH_TEAM_SCORE_BASED_FEATURE_STATS);
		fieldMap.add(TableConst.LEAGUE_SCORE_TIME_BAND_STATS);
		fieldMap.add(TableConst.LEAGUE_SCORE_TIME_BAND_STATS_SPLIT_SCORE);
		fieldMap.add(TableConst.MATCH_CLASSIFICATION_RESULT_COUNT);
		fieldMap.add(TableConst.SCORE_BASED_FEATURE_STATS);
		fieldMap.add(TableConst.SURFACE_OVERVIEW);
		fieldMap.add(TableConst.TEAM_MONTHLY_SCORE_SUMMARY);
		COUNTRYMAP = Collections.unmodifiableList(fieldMap);
	}

	/** フィールドマップ */
	private static final List<String> CATEGORYMAP;
	static {
		List<String> fieldMap = new ArrayList<>();
		fieldMap.add(TableConst.MATCH_CLASSIFICATION_RESULT);
		fieldMap.add(TableConst.NO_GOAL_MATCH_STATS);
		fieldMap.add(TableConst.TEAM_TIME_SEGMENT_SHOOTING_STAT);
		CATEGORYMAP = Collections.unmodifiableList(fieldMap);
	}

	/** フィールドマップ */
	private static final List<String> ALLMAP;
	static {
		List<String> fieldMap = new ArrayList<>();
		fieldMap.add(TableConst.CALC_CORRELATION);
		fieldMap.add(TableConst.CALC_CORRELATION_RANKING);
		fieldMap.add(TableConst.COUNTRY_LEAGUE_SUMMARY);
		fieldMap.add(TableConst.EACH_TEAM_SCORE_BASED_FEATURE_STATS);
		fieldMap.add(TableConst.LEAGUE_SCORE_TIME_BAND_STATS);
		fieldMap.add(TableConst.LEAGUE_SCORE_TIME_BAND_STATS_SPLIT_SCORE);
		fieldMap.add(TableConst.MATCH_CLASSIFICATION_RESULT_COUNT);
		fieldMap.add(TableConst.SCORE_BASED_FEATURE_STATS);
		fieldMap.add(TableConst.SURFACE_OVERVIEW);
		fieldMap.add(TableConst.TEAM_MONTHLY_SCORE_SUMMARY);
		fieldMap.add(TableConst.MATCH_CLASSIFICATION_RESULT);
		fieldMap.add(TableConst.NO_GOAL_MATCH_STATS);
		fieldMap.add(TableConst.TEAM_TIME_SEGMENT_SHOOTING_STAT);
		ALLMAP = Collections.unmodifiableList(fieldMap);
	}

	/** コンストラクタ禁止 */
	private TableUtil() {}


	/**
	 * フィールドマップを返却する
	 * @return
	 */
	public static List<String> getCountryList() {
		return COUNTRYMAP;
	}

	/**
	 * フィールドマップを返却する
	 * @return
	 */
	public static List<String> getCategoryList() {
		return CATEGORYMAP;
	}

	/**
	 * フィールドマップを返却する
	 * @return
	 */
	public static List<String> getAllList() {
		return ALLMAP;
	}

}
