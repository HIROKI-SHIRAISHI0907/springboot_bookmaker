package dev.application.analyze.bm_m025;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.bm_m023.AverageStatisticsSituationConst;
import dev.application.analyze.bm_m024.CalcCorrelationConst;
import dev.application.analyze.bm_m024.CalcCorrelationEntity;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.CalcCorrelationRankingRepository;
import dev.application.domain.repository.CalcCorrelationRepository;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M025統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class CalcCorrelationRankingStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CalcCorrelationRankingStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CalcCorrelationRankingStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M025_CALC_CORRELATION_RANKING";

	/** CalcCorrelationRepositoryレポジトリクラス */
	@Autowired
	private CalcCorrelationRepository calcCorrelationRepository;

	/** CalcCorrelationRankingRepositoryレポジトリクラス */
	@Autowired
	private CalcCorrelationRankingRepository calcCorrelationRankingRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// CalcCorrelationがない場合はランキングが設定できないのでskip
		// 全リーグ・国を走査
		ConcurrentHashMap<String, CalcCorrelationRankingEntity> resultMap = new ConcurrentHashMap<>();
		for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
			String[] data_category = ExecuteMainUtil.splitLeagueInfo(entry.getKey());
			String country = data_category[0];
			String league = data_category[1];

			Map<String, List<BookDataEntity>> entrySub = entry.getValue();
			for (String subEntry : entrySub.keySet()) {
				String home = subEntry.split("-")[0];
				String away = subEntry.split("-")[1];
				// decideBasedMain を呼び出して集計マップを取得
				resultMap = decideBasedMain(resultMap, country, league, home, away);
			}
		}

		// 登録
		for (CalcCorrelationRankingEntity entry : resultMap.values()) {
			insert(entry);
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * 処理メインロジック
	 * @param resultMap エンティティ
	 * @param country 国
	 * @param league リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @return
	 */
	private ConcurrentHashMap<String, CalcCorrelationRankingEntity> decideBasedMain(
			ConcurrentHashMap<String, CalcCorrelationRankingEntity> resultMap,
			String country, String league, String home, String away) {
		// 各種flg
		List<String> flgs = List.of(
				AverageStatisticsSituationConst.ALL_DATA,
				AverageStatisticsSituationConst.FIRST_DATA,
				AverageStatisticsSituationConst.SECOND_DATA);

		ExecutorService executor = Executors.newFixedThreadPool(20); // スレッド数は状況に応じて調整
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (String flg : flgs) {
			CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
				// 国,リーグ単位,ホーム,アウェーでデータ取得
				basedEntities(resultMap, country, league, home, away, flg);
			}, executor);
			futures.add(future);
		}
		// すべての非同期処理が終わるのを待つ
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		executor.shutdown();
		return resultMap;
	}

	/**
	 * 基準エンティティ指定
	 * @param resultMap エンティティ
	 * @param country 国
	 * @param league リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @param flg ALL/1st/2nd
	 * @return
	 */
	private void basedEntities(ConcurrentHashMap<String, CalcCorrelationRankingEntity> resultMap,
			String country, String league, String home, String away, String flg) {
		final String METHOD_NAME = "basedEntities";

		// getDataを呼び出すのはPearsonのみ
		String chkBody = (AverageStatisticsSituationConst.ALL_DATA.equals(flg) ||
				AverageStatisticsSituationConst.FIRST_DATA.equals(flg) ||
				AverageStatisticsSituationConst.SECOND_DATA.equals(flg))
						? CalcCorrelationConst.PEARSON
						: "";

		// correlation_dataから取得
		List<CalcCorrelationEntity> result = this.calcCorrelationRepository.findStatData(country, league,
				home, away, flg, chkBody);
		// 空マップならskip
		if (result == null || result.isEmpty()) {
			String fillChar = setLoggerFillChar(
					chkBody,
					flg,
					country,
					league,
					home,
					away);
			String messageCd = "";
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					messageCd, fillChar);
			return;
		}

		// ソート用List
		List<SortRanking> sortList = new ArrayList<>();

		// 取得したリストを「フィールド名」「値」をDTOに格納する
		CalcCorrelationEntity entity = result.get(0);
		Field[] outFields = CalcCorrelationEntity.class.getDeclaredFields();
		String fillChar = "";
		try {
			int ind = 0;
			for (Field field : outFields) {
				if (ind < 9) {
					ind++;
					continue;
				}
				field.setAccessible(true);
				String name = field.getName();
				String value = (String) field.get(entity);
				fillChar = name + ": " + value;
				sortList.add(new SortRanking(name, value));
				ind++;
			}
		} catch (Exception e) {
			String messageCd = "";
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					messageCd, e, fillChar);
		}

		// ソート
		// 降順 & (null / 空 / 非数 / NaN) は最後尾
		Comparator<SortRanking> byCorrDescNaNLast =
		    Comparator.comparingDouble((SortRanking s) -> safeCorrKey(s.getCorr()))
		              .reversed();
		sortList.sort(byCorrDescNaNLast);

		CalcCorrelationRankingEntity enti = new CalcCorrelationRankingEntity();
		// 出力側のフィールド（CalcCorrelationRankingEntity）の配列
		final Field[] outputFields = CalcCorrelationRankingEntity.class.getDeclaredFields();
		enti.setCountry(country);
		enti.setLeague(league);
		enti.setHome(home);
		enti.setAway(away);
		enti.setScore(flg);
		enti.setChkBody(chkBody);
		for (int inIdx = 8; inIdx < sortList.size(); inIdx++) {
			SortRanking sr = sortList.get(inIdx - 8);
			String field = sr.getField();
			String value = sr.getCorr();
			setOut(outputFields, enti, inIdx, field + "," + value);
		}

		String key = country + "-" + league + "-" + home + "-" + away + "-" + flg + "-" + chkBody;
		resultMap.put(key, enti);
	}

	/**
	 * 堅牢な相関係数のソート準備
	 * @param in
	 * @return
	 */
	private static double safeCorrKey(String in) {
	    if (in == null) return Double.NEGATIVE_INFINITY; // reversedで最後尾へ
	    in = in.trim();
	    if (in.isEmpty()) return Double.NEGATIVE_INFINITY;
	    try {
	        double v = Double.parseDouble(in);
	        return Double.isNaN(v) ? Double.NEGATIVE_INFINITY : v;
	    } catch (NumberFormatException e) {
	        return Double.NEGATIVE_INFINITY; // 数値じゃなければ最後尾
	    }
	}

	/**
	 * 相関係数出力
	 * @param outFields
	 * @param entity
	 * @param idx
	 * @param value
	 */
	private void setOut(Field[] outFields, CalcCorrelationRankingEntity entity, int idx, String value) {
		if (idx < 0 || idx >= outFields.length)
			return;
		Field out = outFields[idx];
		out.setAccessible(true);
		try {
			out.set(entity, value);
		} catch (IllegalAccessException e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, "setOut",
					"相関係数設定失敗: " + out.getName(), e);
		}
	}

	/**
	 * 登録メソッド
	 * @param entity エンティティ
	 */
	private synchronized void insert(CalcCorrelationRankingEntity entity) {
		final String METHOD_NAME = "insert";
		String fillChar = setLoggerFillChar(
				entity.getChkBody(),
				entity.getScore(),
				entity.getCountry(),
				entity.getLeague(),
				entity.getHome(),
				entity.getAway());
		int result = this.calcCorrelationRankingRepository.insert(entity);
		if (result != 1) {
			String messageCd = "新規登録エラー";
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
			this.manageLoggerComponent.createSystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null);
		}
		String messageCd = "登録件数";
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "BM_M025 登録件数: 1件");
	}

	/**
	 * 埋め字設定
	 * @param chk_body 状況
	 * @param score スコア
	 * @param country 国
	 * @param league リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @return
	 */
	private String setLoggerFillChar(String chk_body, String score,
			String country, String league, String home, String away) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("調査内容: " + chk_body + ", ");
		stringBuilder.append("スコア: " + score + ", ");
		stringBuilder.append("国: " + country + ", ");
		stringBuilder.append("リーグ: " + league + ", ");
		stringBuilder.append("ホーム: " + home + ", ");
		stringBuilder.append("アウェー: " + away);
		return stringBuilder.toString();
	}

}
