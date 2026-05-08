package dev.application.analyze.bm_m025;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.bm_m023.AverageStatisticsSituationConst;
import dev.application.analyze.bm_m024.CalcCorrelationConst;
import dev.application.analyze.bm_m024.CalcCorrelationEntity;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.bm.CalcCorrelationRankingRepository;
import dev.application.domain.repository.bm.CalcCorrelationRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M025統計分析ロジック（安全な逐次版 + 詳細ログ）
 * @author shiraishitoshio
 */
@Component
public class CalcCorrelationRankingStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CalcCorrelationRankingStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CalcCorrelationRankingStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M025_CALC_CORRELATION_RANKING";

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M025";

	/** SLF4J Logger */
	private static final Logger log = LoggerFactory.getLogger(CalcCorrelationRankingStat.class);

	/** CalcCorrelationRepositoryレポジトリクラス */
	@Autowired
	private CalcCorrelationRepository calcCorrelationRepository;

	/** CalcCorrelationRankingRepositoryレポジトリクラス */
	@Autowired
	private CalcCorrelationRankingRepository calcCorrelationRankingRepository;

	/** ログ管理ラッパー */
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";

		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		long started = System.currentTimeMillis();

		try {
			if (entities == null || entities.isEmpty()) {
				log.info("[BM_M025] entities is empty. nothing to process.");
				this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
				return;
			}

			log.info("[BM_M025] calcStat start. leagueCount={}", entities.size());

			Map<String, CalcCorrelationRankingEntity> resultMap = new LinkedHashMap<>();

			int leagueIndex = 0;
			int matchCount = 0;

			for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
				leagueIndex++;

				String leagueKey = safe(entry.getKey());
				Map<String, List<BookDataEntity>> entrySub = entry.getValue();

				String[] dataCategory = ExecuteMainUtil.splitLeagueInfo(leagueKey);
				String country = getArrayValue(dataCategory, 0);
				String league = getArrayValue(dataCategory, 1);

				int subSize = entrySub == null ? 0 : entrySub.size();
				log.info("[BM_M025] league start. leagueIndex={}/{}, country={}, league={}, subSize={}",
						leagueIndex, entities.size(), country, league, subSize);

				if (entrySub == null || entrySub.isEmpty()) {
					log.warn("[BM_M025] entrySub is empty. country={}, league={}", country, league);
					continue;
				}

				int subIndex = 0;

				for (String subEntry : entrySub.keySet()) {
					subIndex++;
					matchCount++;

					String[] pair = splitHomeAway(subEntry);
					String home = pair[0];
					String away = pair[1];

					log.info("[BM_M025] match start. leagueIndex={}/{}, subIndex={}/{}, country={}, league={}, home={}, away={}, rawKey={}",
							leagueIndex, entities.size(), subIndex, subSize, country, league, home, away, subEntry);

					try {
						decideBasedMain(resultMap, country, league, home, away);
						log.info("[BM_M025] match done. country={}, league={}, home={}, away={}, resultMapSize={}",
								country, league, home, away, resultMap.size());
					} catch (Exception e) {
						log.error("[BM_M025] decideBasedMain failed. country={}, league={}, home={}, away={}, rawKey={}",
								country, league, home, away, subEntry, e);
						throw e;
					}
				}

				log.info("[BM_M025] league done. leagueIndex={}/{}, country={}, league={}",
						leagueIndex, entities.size(), country, league);
			}

			log.info("[BM_M025] insert phase start. resultMapSize={}, processedMatchCount={}",
					resultMap.size(), matchCount);

			int insertIndex = 0;
			for (CalcCorrelationRankingEntity entity : resultMap.values()) {
				insertIndex++;
				try {
					log.info("[BM_M025] insert start. insertIndex={}/{}, fillChar={}",
							insertIndex, resultMap.size(), buildFillChar(entity));
					insert(entity);
					log.info("[BM_M025] insert done. insertIndex={}/{}, fillChar={}",
							insertIndex, resultMap.size(), buildFillChar(entity));
				} catch (Exception e) {
					log.error("[BM_M025] insert failed. insertIndex={}/{}, fillChar={}",
							insertIndex, resultMap.size(), buildFillChar(entity), e);
					throw e;
				}
			}

			long elapsed = System.currentTimeMillis() - started;
			log.info("[BM_M025] calcStat finished. resultMapSize={}, processedMatchCount={}, elapsedMs={}",
					resultMap.size(), matchCount, elapsed);

			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		} finally {
			this.manageLoggerComponent.clear();
		}
	}

	/**
	 * 処理メインロジック（逐次版）
	 * @param resultMap エンティティ
	 * @param country 国
	 * @param league リーグ
	 * @param home ホーム
	 * @param away アウェー
	 */
	private void decideBasedMain(
			Map<String, CalcCorrelationRankingEntity> resultMap,
			String country, String league, String home, String away) {

		List<String> flgs = List.of(
				AverageStatisticsSituationConst.ALL_DATA,
				AverageStatisticsSituationConst.FIRST_DATA,
				AverageStatisticsSituationConst.SECOND_DATA);

		log.info("[BM_M025] decideBasedMain start. country={}, league={}, home={}, away={}, flgCount={}",
				country, league, home, away, flgs.size());

		for (String flg : flgs) {
			log.info("[BM_M025] basedEntities start. country={}, league={}, home={}, away={}, flg={}",
					country, league, home, away, flg);

			basedEntities(resultMap, country, league, home, away, flg);

			log.info("[BM_M025] basedEntities done. country={}, league={}, home={}, away={}, flg={}, resultMapSize={}",
					country, league, home, away, flg, resultMap.size());
		}

		log.info("[BM_M025] decideBasedMain done. country={}, league={}, home={}, away={}",
				country, league, home, away);
	}

	/**
	 * 基準エンティティ指定
	 * @param resultMap エンティティ
	 * @param country 国
	 * @param league リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @param flg ALL/1st/2nd
	 */
	private void basedEntities(Map<String, CalcCorrelationRankingEntity> resultMap,
			String country, String league, String home, String away, String flg) {
		final String METHOD_NAME = "basedEntities";

		String chkBody = (AverageStatisticsSituationConst.ALL_DATA.equals(flg)
				|| AverageStatisticsSituationConst.FIRST_DATA.equals(flg)
				|| AverageStatisticsSituationConst.SECOND_DATA.equals(flg))
						? CalcCorrelationConst.PEARSON
						: "";

		String fillChar = setLoggerFillChar(chkBody, flg, country, league, home, away);

		log.info("[BM_M025] before findStatData. {}", fillChar);

		List<CalcCorrelationEntity> result = this.calcCorrelationRepository.findStatData(
				country, league, home, away, flg, chkBody);

		log.info("[BM_M025] after findStatData. {} resultSize={}",
				fillChar, result == null ? 0 : result.size());

		if (result == null || result.isEmpty()) {
			String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar);
			return;
		}

		List<SortRanking> sortList = new ArrayList<>();

		CalcCorrelationEntity entity = result.get(0);
		Field[] inFields = CalcCorrelationEntity.class.getDeclaredFields();

		try {
			int ind = 0;
			for (Field field : inFields) {
				if (ind < 9) {
					ind++;
					continue;
				}
				field.setAccessible(true);

				String name = field.getName();
				Object raw = field.get(entity);
				String value = raw == null ? "" : String.valueOf(raw);

				sortList.add(new SortRanking(name, value));
				ind++;
			}
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					messageCd, e, fillChar);
			throw new RuntimeException("BM_M025 reflection failed: " + fillChar, e);
		}

		log.info("[BM_M025] sort target created. {} sortListSize={}", fillChar, sortList.size());

		Comparator<SortRanking> byCorrDescNaNLast = Comparator
				.comparingDouble((SortRanking s) -> safeCorrKey(s.getCorr()))
				.reversed();

		sortList.sort(byCorrDescNaNLast);

		CalcCorrelationRankingEntity enti = new CalcCorrelationRankingEntity();
		Field[] outputFields = CalcCorrelationRankingEntity.class.getDeclaredFields();

		enti.setCountry(country);
		enti.setLeague(league);
		enti.setHome(home);
		enti.setAway(away);
		enti.setScore(flg);
		enti.setChkBody(chkBody);

		// ここは元コードのバグを修正
		// outputFields の 8番目以降へ、sortList を先頭から順に詰める
		for (int rankIdx = 0; rankIdx < sortList.size(); rankIdx++) {
			int outIdx = rankIdx + 8;
			if (outIdx >= outputFields.length) {
				log.warn("[BM_M025] output field overflow. {} rankIdx={}, outIdx={}, outputFieldLength={}",
						fillChar, rankIdx, outIdx, outputFields.length);
				break;
			}

			SortRanking sr = sortList.get(rankIdx);
			String field = safe(sr.getField());
			String value = safe(sr.getCorr());

			setOut(outputFields, enti, outIdx, field + "," + value);
		}

		String key = country + "-" + league + "-" + home + "-" + away + "-" + flg + "-" + chkBody;
		resultMap.put(key, enti);

		log.info("[BM_M025] entity put done. key={}, {}", key, fillChar);
	}

	/**
	 * 堅牢な相関係数のソート準備
	 * @param in
	 * @return
	 */
	private static double safeCorrKey(String in) {
		if (in == null) {
			return Double.NEGATIVE_INFINITY;
		}
		in = in.trim();
		if (in.isEmpty()) {
			return Double.NEGATIVE_INFINITY;
		}
		try {
			double v = Double.parseDouble(in);
			return Double.isNaN(v) ? Double.NEGATIVE_INFINITY : v;
		} catch (NumberFormatException e) {
			return Double.NEGATIVE_INFINITY;
		}
	}

	/**
	 * 相関係数出力
	 * @param outFields output field array
	 * @param entity entity
	 * @param idx field index
	 * @param value value
	 */
	private void setOut(Field[] outFields, CalcCorrelationRankingEntity entity, int idx, String value) {
		final String METHOD_NAME = "setOut";

		if (idx < 0 || idx >= outFields.length) {
			log.warn("[BM_M025] setOut skip. idx out of range. idx={}, outputFieldLength={}", idx, outFields.length);
			return;
		}

		Field out = outFields[idx];
		out.setAccessible(true);

		try {
			out.set(entity, value);
		} catch (IllegalAccessException e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, "相関係数設定失敗: " + out.getName());
			throw new RuntimeException("BM_M025 setOut failed: " + out.getName(), e);
		}
	}

	/**
	 * 登録メソッド
	 * @param entity エンティティ
	 */
	private void insert(CalcCorrelationRankingEntity entity) {
		final String METHOD_NAME = "insert";

		String fillChar = buildFillChar(entity);

		log.info("[BM_M025] before insert. {}", fillChar);

		int result = this.calcCorrelationRankingRepository.insert(entity);

		log.info("[BM_M025] after insert. {} result={}", fillChar, result);

		if (result != 1) {
			String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
			this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					messageCd, 1, result, fillChar);
		}

		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 登録件数: " + result + "件 (" + fillChar + ")");
	}

	/**
	 * 埋め字設定
	 * @param chkBody 調査内容
	 * @param score スコア
	 * @param country 国
	 * @param league リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @return 埋め字
	 */
	private String setLoggerFillChar(String chkBody, String score,
			String country, String league, String home, String away) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("調査内容: ").append(safe(chkBody)).append(", ");
		stringBuilder.append("スコア: ").append(safe(score)).append(", ");
		stringBuilder.append("国: ").append(safe(country)).append(", ");
		stringBuilder.append("リーグ: ").append(safe(league)).append(", ");
		stringBuilder.append("ホーム: ").append(safe(home)).append(", ");
		stringBuilder.append("アウェー: ").append(safe(away));
		return stringBuilder.toString();
	}

	private String buildFillChar(CalcCorrelationRankingEntity entity) {
		if (entity == null) {
			return "entity=null";
		}
		return setLoggerFillChar(
				entity.getChkBody(),
				entity.getScore(),
				entity.getCountry(),
				entity.getLeague(),
				entity.getHome(),
				entity.getAway());
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}

	private String getArrayValue(String[] arr, int idx) {
		if (arr == null || idx < 0 || idx >= arr.length || arr[idx] == null) {
			return "";
		}
		return arr[idx];
	}

	private String[] splitHomeAway(String subEntry) {
		if (subEntry == null) {
			return new String[] { "", "" };
		}
		String[] pair = subEntry.split("-", 2);
		if (pair.length < 2) {
			log.warn("[BM_M025] subEntry split failed. rawKey={}", subEntry);
			return new String[] { subEntry, "" };
		}
		return new String[] { safe(pair[0]), safe(pair[1]) };
	}
}
