package dev.application.analyze.bm_m019_bm_m020;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.MatchClassificationResultCountRepository;
import dev.application.domain.repository.MatchClassificationResultRepository;
import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.BookDataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M019_BM_M020統計分析ロジック（恒久対応版）
 * - 並列×DBを廃止
 * - BM_M020 は UPSERT(+1) で原子的にカウント反映
 * - BM_M019 はバルク挿入
 */
@Component
public class MatchClassificationResultStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MatchClassificationResultStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = MatchClassificationResultStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M019_BM_M020_MATCH_CLASSIFICATION_RESULT";

	/** BookDataToMatchClassificationResultMapperマッパークラス */
	@Autowired
	private BookDataToMatchClassificationResultMapper bookDataToMatchClassificationResultMapper;

	/** MatchClassificationResultRepositoryレポジトリクラス（※insertBatch対応必須） */
	@Autowired
	private MatchClassificationResultRepository matchClassificationResultRepository;

	/** MatchClassificationResultCountRepositoryレポジトリクラス（※upsertIncrementCount対応必須） */
	@Autowired
	private MatchClassificationResultCountRepository matchClassificationResultCountRepository;

	/** ログ管理ラッパー*/
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * DB項目,テーブル名Mapping
	 */
	public static final Map<Integer, String> SCORE_CLASSIFICATION_ALL_MAP;
	static {
		HashMap<Integer, String> SCORE_CLASSIFICATION_MAP = new LinkedHashMap<>();
		SCORE_CLASSIFICATION_MAP.put(1,  ClassifyScoreAIConst.HOME_SCORED_WITHIN_20_NEXT_SCORE_BEFORE_HALF);
		SCORE_CLASSIFICATION_MAP.put(2,  ClassifyScoreAIConst.HOME_SCORED_WITHIN_20_NEXT_SCORE_AFTER_HALF);
		SCORE_CLASSIFICATION_MAP.put(3,  ClassifyScoreAIConst.HOME_SCORED_WITHIN_20_NO_FURTHER_GOAL);
		SCORE_CLASSIFICATION_MAP.put(4,  ClassifyScoreAIConst.AWAY_SCORED_WITHIN_20_NEXT_SCORE_BEFORE_HALF);
		SCORE_CLASSIFICATION_MAP.put(5,  ClassifyScoreAIConst.AWAY_SCORED_WITHIN_20_NEXT_SCORE_AFTER_HALF);
		SCORE_CLASSIFICATION_MAP.put(6,  ClassifyScoreAIConst.AWAY_SCORED_WITHIN_20_NO_FURTHER_GOAL);
		SCORE_CLASSIFICATION_MAP.put(7,  ClassifyScoreAIConst.HOME_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_BEFORE_HALF);
		SCORE_CLASSIFICATION_MAP.put(8,  ClassifyScoreAIConst.HOME_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_AFTER_HALF);
		SCORE_CLASSIFICATION_MAP.put(9,  ClassifyScoreAIConst.HOME_SCORED_BETWEEN_20_AND_45_NO_FURTHER_GOAL);
		SCORE_CLASSIFICATION_MAP.put(10, ClassifyScoreAIConst.AWAY_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_BEFORE_HALF);
		SCORE_CLASSIFICATION_MAP.put(11, ClassifyScoreAIConst.AWAY_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_AFTER_HALF);
		SCORE_CLASSIFICATION_MAP.put(12, ClassifyScoreAIConst.AWAY_SCORED_BETWEEN_20_AND_45_NO_FURTHER_GOAL);
		SCORE_CLASSIFICATION_MAP.put(13, ClassifyScoreAIConst.NO_GOAL_FIRST_HALF_NEXT_HOME_SCORE);
		SCORE_CLASSIFICATION_MAP.put(14, ClassifyScoreAIConst.NO_GOAL_FIRST_HALF_NEXT_AWAY_SCORE);
		SCORE_CLASSIFICATION_MAP.put(15, ClassifyScoreAIConst.NO_GOAL);
		SCORE_CLASSIFICATION_MAP.put(-1, ClassifyScoreAIConst.EXCEPT_FOR_CONDITION);
		SCORE_CLASSIFICATION_ALL_MAP = Collections.unmodifiableMap(SCORE_CLASSIFICATION_MAP);
	}

	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		try {
			this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

			// 件数テーブルの初期行を作成（不足分のみ）※UPSERTで不要だが、全行を0で用意したい要件に合わせて維持
			for (Map.Entry<String, Map<String, List<BookDataEntity>>> e : entities.entrySet()) {
				String[] sp = ExecuteMainUtil.splitLeagueInfo(e.getKey());
				init(sp[0], sp[1]);
			}

			// 後段の件数更新用（country+league → classificationMode のリスト）
			Map<String, List<String>> mainMap = new HashMap<>();

			// BM_M019：明細作成→バルク挿入
			entities.entrySet().stream().forEach(entry -> {
				String[] sp = ExecuteMainUtil.splitLeagueInfo(entry.getKey());
				String country = sp[0];
				String league  = sp[1];

				List<MatchClassificationResultEntity> bulk = new ArrayList<>();
				Map<String, List<BookDataEntity>> matchMap = entry.getValue();

				for (List<BookDataEntity> dataList : matchMap.values()) {
					MatchClassificationResultOutputDTO dto = classification(dataList);
					if (dto == null) continue;

					// バルク用に溜める
					bulk.addAll(dto.getEntityList());

					// 後段の件数更新用に classificationMode を保持
					mainMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
						   .add(dto.getClassificationMode());
				}

				// バルク挿入（0件ならスキップ）
				if (!bulk.isEmpty()) {
					int result = this.matchClassificationResultRepository.insertBatch(bulk);
					if (result != bulk.size()) {
						this.rootCauseWrapper.throwUnexpectedRowCount(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, "バルク新規登録エラー",
							bulk.size(), result, "country=" + country + ", league=" + league);
					}
					this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, "登録件数",
						setLoggerFillChar(country, league),
						"BM_M019 登録件数: " + bulk.size() + "件");
				}
			});

			// BM_M020：件数反映（UPSERTで+1）
			mainMap.entrySet().stream().forEach(entry -> {
				String[] sp = ExecuteMainUtil.splitLeagueInfo(entry.getKey());
				String country = sp[0];
				String league  = sp[1];

				entry.getValue().stream()
					.filter(Objects::nonNull)
					.map(String::trim)
					.filter(s -> s.matches("-?\\d+")) // 数値のみ
					.forEach(classifyMode -> {
						MatchClassificationResultCountEntity e = new MatchClassificationResultCountEntity();
						e.setCountry(country);
						e.setLeague(league);
						e.setClassifyMode(classifyMode);
						e.setRemarks(getRemarks(safeParseInt(classifyMode, -1)));
						int up = this.matchClassificationResultCountRepository.upsertIncrementCount(e);
						if (up != 1) {
							this.rootCauseWrapper.throwUnexpectedRowCount(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, "件数反映エラー(UPSERT)",
								1, up, String.format("country=%s, league=%s, classify=%s", country, league, classifyMode));
						}
						this.manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, "BM_M020 件数反映",
							setLoggerFillChar(country, league), "反映:1件 (classify=" + classifyMode + ")");
					});
			});

			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		} finally {
			this.manageLoggerComponent.clear();
		}
	}

	/** 件数初期登録（不足行のみ作成） */
	private void init(String country, String league) {
		final String METHOD_NAME = "init";
		String fillChar = setLoggerFillChar(country, league);

		for (int classify = 1; classify <= SCORE_CLASSIFICATION_ALL_MAP.size() - 1; classify++) {
			if (!this.matchClassificationResultCountRepository
					.findData(country, league, String.valueOf(classify)).isEmpty()) {
				continue;
			}
			MatchClassificationResultCountEntity e = new MatchClassificationResultCountEntity();
			e.setCountry(country);
			e.setLeague(league);
			e.setClassifyMode(String.valueOf(classify));
			e.setCount("0");
			e.setRemarks(getRemarks(classify));
			int result = this.matchClassificationResultCountRepository.insert(e);
			if (result != 1) {
				this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, "新規登録エラー",
					1, result, "classifymode=" + classify);
			}
		}

		// -1（除外）行
		if (this.matchClassificationResultCountRepository.findData(country, league, "-1").isEmpty()) {
			MatchClassificationResultCountEntity e = new MatchClassificationResultCountEntity();
			e.setCountry(country);
			e.setLeague(league);
			e.setClassifyMode("-1");
			e.setCount("0");
			e.setRemarks(getRemarks(-1));
			int result = this.matchClassificationResultCountRepository.insert(e);
			if (result != 1) {
				this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, "新規登録エラー",
					1, result, "classifymode=-1");
			}
			this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, "登録件数",
				fillChar, "BM_M020 登録件数: " + SCORE_CLASSIFICATION_ALL_MAP.size() + "件");
		}
	}

	/** 分類本体（ロジックは従来踏襲） */
	private MatchClassificationResultOutputDTO classification(List<BookDataEntity> entityList) {
		MatchClassificationResultOutputDTO dto = new MatchClassificationResultOutputDTO();

		Set<Integer> cond = new HashSet<>();
		List<MatchClassificationResultEntity> inserts = new ArrayList<>();
		BookDataEntity max = ExecuteMainUtil.getMaxSeqEntities(entityList);
		if (!BookMakersCommonConst.FIN.equals(max.getTime())) return null;

		int maxHome = safeParseInt(max.getHomeScore(), 0);
		int maxAway = safeParseInt(max.getAwayScore(), 0);

		if (maxHome == 0 && maxAway == 0) cond.add(-1);
		if (maxHome == 1 && maxAway == 0) cond.add(-2);
		if (maxHome == 0 && maxAway == 1) cond.add(-3);

		int classify_mode = -1;
		List<String> scoreList = new ArrayList<>();

		for (BookDataEntity e : entityList) {
			if (BookMakersCommonConst.GOAL_DELETE.equals(e.getJudge())) continue;

			int h = safeParseInt(e.getHomeScore(), 0);
			int a = safeParseInt(e.getAwayScore(), 0);
			double min = ExecuteMainUtil.convertToMinutes(e.getTime());

			if ((int)min <= 20 && (h==1 && a==0)) cond.add(1);
			if ((int)min <= 45 && (h==2 && a==0)) cond.add(2);
			if ((int)min >  45 && (h==2 && a==0)) cond.add(3);
			if ((int)min <= 45 && (h==1 && a==1)) cond.add(4);
			if ((int)min <= 20 && (h==0 && a==1)) cond.add(5);
			if ((int)min <= 45 && (h==0 && a==2)) cond.add(6);
			if ((int)min >  45 && (h==0 && a==2)) cond.add(7);
			if ((int)min >  45 && (h==1 && a==1)) cond.add(8);
			if ((int)min >  20 && (int)min <= 45 && (h==1 && a==0)) cond.add(9);
			if ((int)min >  20 && (int)min <= 45 && (h==0 && a==1)) cond.add(10);
			if ((BookMakersCommonConst.FIRST_HALF_TIME.equals(e.getTime()) ||
				 BookMakersCommonConst.HALF_TIME.equals(e.getTime())) && (h==0 && a==0)) cond.add(11);
			if ((int)min >  45 && (h==1 && a==0)) cond.add(12);
			if ((int)min >  45 && (h==0 && a==1)) cond.add(13);

			// 判定（元ロジック踏襲）
			if (cond.contains(1) || cond.contains(5)) {
				if (cond.contains(1) && (cond.contains(2) || cond.contains(4))) classify_mode = 1;
				if (cond.contains(1) && (cond.contains(3) || cond.contains(8))) classify_mode = 2;
				if (cond.contains(1) && cond.contains(-2))                      classify_mode = 3;
				if (cond.contains(5) && (cond.contains(6) || cond.contains(4))) classify_mode = 4;
				if (cond.contains(5) && (cond.contains(7) || cond.contains(8))) classify_mode = 5;
				if (cond.contains(5) && cond.contains(-3))                      classify_mode = 6;
			} else if (cond.contains(9) || cond.contains(10)) {
				if (cond.contains(9)  && (cond.contains(2)  || cond.contains(4))) classify_mode = 7;
				if (cond.contains(9)  && (cond.contains(3)  || cond.contains(8))) classify_mode = 8;
				if (cond.contains(9)  && cond.contains(-2))                      classify_mode = 9;
				if (cond.contains(10) && (cond.contains(6)  || cond.contains(4))) classify_mode = 10;
				if (cond.contains(10) && (cond.contains(7)  || cond.contains(8))) classify_mode = 11;
				if (cond.contains(10) && cond.contains(-3))                      classify_mode = 12;
			} else if (cond.contains(11)) {
				if (cond.contains(11) && (cond.contains(12) || cond.contains(8))) classify_mode = 13;
				if (cond.contains(11) && (cond.contains(13) || cond.contains(8))) classify_mode = 14;
			} else if (cond.contains(-1)) {
				classify_mode = 15;
			}

			// 登録タイミング
			if (BookMakersCommonConst.HALF_TIME.equals(e.getTime()) ||
				BookMakersCommonConst.FIRST_HALF_TIME.equals(e.getTime())) {
				inserts.add(this.bookDataToMatchClassificationResultMapper.mapStruct(e, String.valueOf(classify_mode)));
			} else {
				String sig = e.getHomeScore() + ":" + e.getAwayScore();
				if (!scoreList.contains(sig)) {
					inserts.add(this.bookDataToMatchClassificationResultMapper.mapStruct(e, String.valueOf(classify_mode)));
					scoreList.add(sig);
				}
			}
		}

		for (MatchClassificationResultEntity e : inserts) {
			e.setClassifyMode(String.valueOf(classify_mode));
		}
		dto.setClassificationMode(String.valueOf(classify_mode));
		dto.setEntityList(inserts);
		return dto;
	}

	/** 埋め字設定 */
	private String setLoggerFillChar(String country, String league) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("国: " + country + ", ");
		stringBuilder.append("リーグ: " + league);
		return stringBuilder.toString();
	}

	private static int safeParseInt(String s, int def) {
		try { return Integer.parseInt(s); } catch (Exception e) { return def; }
	}

	/** キー→備考 */
	private String getRemarks(int key) {
		return SCORE_CLASSIFICATION_ALL_MAP.get(key);
	}
}
