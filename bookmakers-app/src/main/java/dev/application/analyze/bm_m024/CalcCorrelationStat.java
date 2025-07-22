package dev.application.analyze.bm_m024;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import dev.application.analyze.bm_m023.AverageStatisticsSituationConst;
import dev.application.analyze.bm_m023.BmM023M024M026InitBean;
import dev.application.analyze.bm_m023.ScoreBasedFeatureStat;
import dev.application.analyze.bm_m023.ScoreBasedFeatureStatsEntity;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.ScoreBasedFeatureStatsRepository;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M024統計分析ロジック
 * @author shiraishitoshio
 *
 */
public class CalcCorrelationStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ScoreBasedFeatureStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ScoreBasedFeatureStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M024_CALC_CORRELATION";

	/** Beanクラス */
	@Autowired
	private BmM023M024M026InitBean bean;

	/** ScoreBasedFeatureStatsRepositoryレポジトリクラス */
	@Autowired
	private ScoreBasedFeatureStatsRepository scoreBasedFeatureStatsRepository;

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

		// 全リーグ・国を走査
		ConcurrentHashMap<String, ScoreBasedFeatureStatsEntity> map = new ConcurrentHashMap<>();
		for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
			String[] data_category = entry.getKey().split("-");
			String country = data_category[0];
			String league = data_category[1];
			Map<String, List<BookDataEntity>> entrySub = entry.getValue();
			for (List<BookDataEntity> entityList : entrySub.values()) {
				// null や空リストはスキップ
				if (entityList == null || entityList.isEmpty())
					continue;
				// decideBasedMain を呼び出して集計マップを取得
				map = decideBasedMain(entityList, country, league);
			}
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * 処理メインロジック
	 * @param entities エンティティ
	 * @param country 国
	 * @param league リーグ
	 * @return
	 */
	private ConcurrentHashMap<String, ScoreBasedFeatureStatsEntity> decideBasedMain(List<BookDataEntity> entities,
			String country, String league) {
		// 各種flg
		List<String> flgs = List.of(
				AverageStatisticsSituationConst.ALL_DATA,
				AverageStatisticsSituationConst.FIRST_DATA,
				AverageStatisticsSituationConst.SECOND_DATA);

		ExecutorService executor = Executors.newFixedThreadPool(20); // スレッド数は状況に応じて調整
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		ConcurrentHashMap<String, ScoreBasedFeatureStatsEntity> allMap = new ConcurrentHashMap<>();
		for (String flg : flgs) {
			// ALL_DATA / FIRST_DATA / SECOND_DATA → スコア単位でなく全体処理なので null を渡す
			CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
				basedEntities(allMap, entities, flg, country, league);
			}, executor);
			futures.add(future);
		}
		// すべての非同期処理が終わるのを待つ
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		executor.shutdown();
		return allMap;
	}

	/**
	 * 基準エンティティ指定
	 * @param insertMap map
	 * @param entities 全体エンティティ
	 * @param flg 設定フラグ
	 * @param country 国
	 * @param league リーグ
	 * @return
	 */
	private void basedEntities(ConcurrentHashMap<String, ScoreBasedFeatureStatsEntity> insertMap,
			List<BookDataEntity> entities, String flg,
			String country, String league) {
		// 既存のリスト
		List<BookDataEntity> filteredList = null;
		if (AverageStatisticsSituationConst.ALL_DATA.equals(flg)) {
			filteredList = entities;
		} else {
			// ハーフタイムの通番を特定
			String halfTimeSeq = ExecuteMainUtil.getHalfEntities(entities).getSeq();
			// ハーフタイム前の試合時間のデータをフィルタリング（通番が半分より小さいもの）
			if (AverageStatisticsSituationConst.FIRST_DATA.equals(flg)) {
				filteredList = entities.stream()
						.filter(entity -> entity.getSeq().compareTo(halfTimeSeq) <= 0) // 通番がハーフタイム(含む)より前
						.collect(Collectors.toList());
			} else if (AverageStatisticsSituationConst.SECOND_DATA.equals(flg)) {
				filteredList = entities.stream()
						.filter(entity -> entity.getSeq().compareTo(halfTimeSeq) > 0) // 通番がハーフタイムより後
						.collect(Collectors.toList());
			}
		}

		// 空マップならskip
		if (filteredList == null || filteredList.isEmpty()) {
			return;
		}

		String[] minList = this.bean.getMinList().clone();
		String[] maxList = this.bean.getMaxList().clone();
		String[] aveList = this.bean.getAvgList().clone();
		String[] sigmaList = this.bean.getSigmaList().clone();
		Integer[] minCntList = this.bean.getCntList().clone();
		Integer[] maxCntList = this.bean.getCntList().clone();
		Integer[] aveCntList = this.bean.getCntList().clone();
		Integer[] sigmaCntList = this.bean.getCntList().clone();
		String[] tMinList = this.bean.getTimeMinList().clone();
		String[] tMaxList = this.bean.getTimeMaxList().clone();
		String[] tAveList = this.bean.getTimeAvgList().clone();
		String[] tSigmaList = this.bean.getTimeSigmaList().clone();
		Integer[] tMinCntList = this.bean.getTimeCntList().clone();
		Integer[] tMaxCntList = this.bean.getTimeCntList().clone();
		Integer[] tAveCntList = this.bean.getTimeCntList().clone();
		Integer[] tSigmaCntList = this.bean.getTimeCntList().clone();

		// TODO: 得点と特徴量との相関関係を導出
	}
}
