package dev.application.analyze.bm_m024;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.bm_m023.AverageStatisticsSituationConst;
import dev.application.analyze.bm_m023.StatFormatResolver;
import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.CalcCorrelationRepository;
import dev.common.entity.BookDataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M024統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
public class CalcCorrelationStat extends StatFormatResolver implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CalcCorrelationStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CalcCorrelationStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M024_CALC_CORRELATION";

	/** CalcCorrelationRepositoryレポジトリクラス */
	@Autowired
	private CalcCorrelationRepository calcCorrelationRepository;

	/** ログ管理ラッパー*/
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
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 全リーグ・国を走査
		ConcurrentHashMap<String, CalcCorrelationEntity> resultMap = new ConcurrentHashMap<>();
		for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
			String[] data_category = ExecuteMainUtil.splitLeagueInfo(entry.getKey());
			String country = data_category[0];
			String league = data_category[1];

			Map<String, List<BookDataEntity>> entrySub = entry.getValue();
			for (List<BookDataEntity> entityList : entrySub.values()) {
				// null や空リストはスキップ
				if (entityList == null || entityList.isEmpty())
					continue;
				String home = entityList.get(0).getHomeTeamName();
				String away = entityList.get(0).getAwayTeamName();
				// decideBasedMain を呼び出して集計マップを取得
				ConcurrentHashMap<String, CalcCorrelationEntity> partialMap = decideBasedMain(entityList, country,
						league, home, away);
				resultMap.putAll(partialMap);
			}
		}

		// 登録
		ExecutorService executor = Executors.newFixedThreadPool(resultMap.size());
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (Map.Entry<String, CalcCorrelationEntity> entry : resultMap.entrySet()) {
			CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
				CalcCorrelationEntity entity = entry.getValue();
				insert(entity);
			}, executor);
			futures.add(future);
		}
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		executor.shutdown();

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
	 * @param home ホーム
	 * @param away アウェー
	 * @return
	 */
	private ConcurrentHashMap<String, CalcCorrelationEntity> decideBasedMain(List<BookDataEntity> entities,
			String country, String league, String home, String away) {
		// 各種flg
		List<String> flgs = List.of(
				AverageStatisticsSituationConst.ALL_DATA,
				AverageStatisticsSituationConst.FIRST_DATA,
				AverageStatisticsSituationConst.SECOND_DATA);

		ExecutorService executor = Executors.newFixedThreadPool(20); // スレッド数は状況に応じて調整
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		ConcurrentHashMap<String, CalcCorrelationEntity> allMap = new ConcurrentHashMap<>();
		for (String flg : flgs) {
			// ALL_DATA / FIRST_DATA / SECOND_DATA → スコア単位でなく全体処理なので null を渡す
			CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
				basedEntities(allMap, entities, flg, country, league, home, away);
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
	 * @param home ホーム
	 * @param away アウェー
	 * @return
	 */
	private void basedEntities(ConcurrentHashMap<String, CalcCorrelationEntity> insertMap,
			List<BookDataEntity> entities, String flg,
			String country, String league, String home, String away) {
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

		// getDataを呼び出すのはPearsonのみ
		String chkBody = (AverageStatisticsSituationConst.ALL_DATA.equals(flg) ||
				AverageStatisticsSituationConst.FIRST_DATA.equals(flg) ||
				AverageStatisticsSituationConst.SECOND_DATA.equals(flg))
						? CalcCorrelationConst.PEARSON
						: "";

		// 相関係数データ初期化リスト(特徴量分だけloop)
		CalcCorrelationEntity entity = new CalcCorrelationEntity();
		entity.setCountry(country);
		entity.setLeague(league);
		entity.setHome(home);
		entity.setAway(away);
		entity.setScore(flg);
		entity.setChkBody(chkBody);

		// 出力側のフィールド（CalcCorrelationEntity）の配列
		final Field[] outFields = CalcCorrelationEntity.class.getDeclaredFields();
		// 入力側（BookDataEntity）のフィールド配列
		final Field[] inFields  = BookDataEntity.class.getDeclaredFields();

		final int OUT_OFFSET = 9;           // ★ 出力開始インデックス
		final int IN_START   = 11;          // 例: BookDataEntity で特徴量開始
		final int IN_END     = Math.min(inFields.length, IN_START + AverageStatisticsSituationConst.COUNTER);

		int outIdx = 0;                     // ★ 相関の「出力順序」カウンタ（0始まりだが実際はOUT_OFFSETを足して書く）

		for (int inIdx = IN_START; inIdx < IN_END; inIdx++) {
		    Field f = inFields[inIdx];
		    f.setAccessible(true);
		    String name = f.getName();

		    // tri-split 対象か？
		    if (isTriSplitFieldName(name)) {
		        // --- 3系列分の配列を用意 ---
		        int n = filteredList.size() - 1;
		        String[] xRatio = new String[n];
		        String[] xCount = new String[n];
		        String[] xTry   = new String[n];
		        String[] yList  = new String[n];

		        try {
		        	for (int ent = 1; ent < filteredList.size(); ent++) {
		                int pos = ent - 1;
		                BookDataEntity prev = filteredList.get(ent - 1);
		                BookDataEntity curr = filteredList.get(ent);
		                String raw = (String) f.get(curr);
		                // "65% (13/20)" / "93%" / "" を常に3要素に
		                var t = split3Safe(raw); // left=%, middle=成功数, right=試行数

		                xRatio[pos] = t.getLeft();   // 例: "65%" or "93%" or ""
		                xCount[pos] = t.getMiddle(); // 例: "13" or ""
		                xTry[pos]   = t.getRight();  // 例: "20" or ""
		                yList[pos]  = makeFlag(prev, curr, name);
		                if (BookMakersCommonConst.GOAL_DELETE.equals(prev.getJudge())
		                 || BookMakersCommonConst.GOAL_DELETE.equals(curr.getJudge())) {
		                    yList[pos] = "0";
		                }
		            }
		        } catch (Exception e) {
		            this.manageLoggerComponent.debugErrorLog(
		                PROJECT_NAME, CLASS_NAME, "calc-corr-trisplit",
		                "tri-split 抽出失敗: " + name, e);
		            setOut(outFields, entity, OUT_OFFSET + outIdx++, 0.0);
		            setOut(outFields, entity, OUT_OFFSET + outIdx++, 0.0);
		            setOut(outFields, entity, OUT_OFFSET + outIdx++, 0.0);
		            continue;
		        }

		        // --- 相関を3つ計算 ---
		        double[] yD = convertToDoubleArray(yList, false);

		        double[] xr = convertToDoubleArray(xRatio, firstNonNullContainsPercent(xRatio));
		        double  pr  = calculatePearsonCorrelation(xr, yD);

		        double[] xc = convertToDoubleArray(xCount, false);
		        double  pc  = calculatePearsonCorrelation(xc, yD);

		        double[] xt = convertToDoubleArray(xTry, false);
		        double  pt  = calculatePearsonCorrelation(xt, yD);

		        // --- 連続する3インデックスに割り当て ---
		        setOut(outFields, entity, OUT_OFFSET + outIdx++, pr);
		        setOut(outFields, entity, OUT_OFFSET + outIdx++, pc);
		        setOut(outFields, entity, OUT_OFFSET + outIdx++, pt);
		    } else {
		        // --- 単一系列 ---
		        int n = filteredList.size() - 1;
		        String[] xList = new String[n];
		        String[] yList = new String[n];

		        try {
		            for (int ent_ind = 1; ent_ind < filteredList.size(); ent_ind++) {
		                int pos = ent_ind - 1;
		                BookDataEntity prev = filteredList.get(ent_ind - 1);
		                BookDataEntity curr = filteredList.get(ent_ind);

		                String raw = (String) f.get(curr);
		                xList[pos] = raw;
		                yList[pos] = makeFlag(prev, curr, name);

		                if (BookMakersCommonConst.GOAL_DELETE.equals(prev.getJudge())
		                 || BookMakersCommonConst.GOAL_DELETE.equals(curr.getJudge())) {
		                    yList[pos] = "0";
		                }
		            }
		        } catch (Exception e) {
		            this.manageLoggerComponent.debugErrorLog(
		                PROJECT_NAME, CLASS_NAME, "calc-corr-single",
		                "単一抽出失敗: " + name, e);
		            setOut(outFields, entity, OUT_OFFSET + outIdx++, 0.0);
		            continue;
		        }

		        boolean isPercent = firstNonNullContainsPercent(xList);
		        double[] xD = convertToDoubleArray(xList, isPercent);
		        double[] yD = convertToDoubleArray(yList, false);
		        double pearson = calculatePearsonCorrelation(xD, yD);

		        setOut(outFields, entity, OUT_OFFSET + outIdx++, pearson);
		    }
		}

		// スレッドセーフな格納
		String mapKey = String.join("|",
		        country,
		        league,
		        home,
		        away,
		        flg,
		        chkBody
		);
		insertMap.put(mapKey, entity);
	}

	/**
	 * 相関係数出力
	 * @param outFields
	 * @param entity
	 * @param idx
	 * @param value
	 */
	private void setOut(Field[] outFields, CalcCorrelationEntity entity, int idx, double value) {
	    if (idx < 0 || idx >= outFields.length) return;
	    Field out = outFields[idx];
	    out.setAccessible(true);
	    try {
	        out.set(entity, String.format("%.5f", value));
	    } catch (IllegalAccessException e) {
	        this.manageLoggerComponent.debugErrorLog(
	            PROJECT_NAME, CLASS_NAME, "setOut",
	            "相関係数設定失敗: " + out.getName(), e);
	    }
	}

	/**
	 * フラグに対して値抽出
	 * @param prev
	 * @param curr
	 * @param fieldName
	 * @return
	 */
	private String makeFlag(BookDataEntity prev, BookDataEntity curr, String fieldName) {
		int prevHome = Integer.parseInt(prev.getHomeScore());
		int currHome = Integer.parseInt(curr.getHomeScore());
		int prevAway = Integer.parseInt(prev.getAwayScore());
		int currAway = Integer.parseInt(curr.getAwayScore());
		String n = fieldName.toLowerCase();
		if (n.contains("home"))
			return (currHome > prevHome) ? "1" : "0";
		if (n.contains("away"))
			return (currAway > prevAway) ? "1" : "0";
		return "0";
	}

	/**
	 * nullpercent
	 * @param arr
	 * @return
	 */
	private boolean firstNonNullContainsPercent(String[] arr) {
		for (String s : arr)
			if (s != null)
				return s.contains("%");
		return false;
	}

	/**
	 * 登録メソッド
	 * @param entity エンティティ
	 */
	private synchronized void insert(CalcCorrelationEntity entity) {
		final String METHOD_NAME = "insert";
		String fillChar = setLoggerFillChar(
				entity.getChkBody(),
				entity.getScore(),
				entity.getCountry(),
				entity.getLeague(),
				entity.getHome(),
				entity.getAway());
		int result = this.calcCorrelationRepository.insert(entity);
		if (result != 1) {
			String messageCd = "新規登録エラー";
			this.rootCauseWrapper.throwUnexpectedRowCount(
			        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
			        messageCd,
			        1, result,
			        null
			    );
		}
		String messageCd = "登録件数";
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "BM_M024 登録件数: 1件");
	}

	/**
	 * ピアソン相関係数を導出
	 * @param x 特徴量
	 * @param y 得点
	 * @return
	 */
	public double calculatePearsonCorrelation(double[] x, double[] y) {
	    // 不正/欠損/定数系列を除外した安全版
	    if (x == null || y == null) return 0.0;

	    // 同じ長さの「有限要素」だけに揃える
	    int n = Math.min(x.length, y.length);
	    List<Double> xs = new ArrayList<>(n);
	    List<Double> ys = new ArrayList<>(n);
	    for (int i = 0; i < n; i++) {
	        double a = x[i], b = y[i];
	        if (Double.isFinite(a) && Double.isFinite(b)) {
	            xs.add(a); ys.add(b);
	        }
	    }
	    if (xs.size() < 2) return 0.0;

	    double[] xx = xs.stream().mapToDouble(Double::doubleValue).toArray();
	    double[] yy = ys.stream().mapToDouble(Double::doubleValue).toArray();

	    // 片方が定数（分散ゼロ）なら 0 を返す
	    if (isConstant(xx) || isConstant(yy)) return 0.0;

	    try {
	        double r = new PearsonsCorrelation().correlation(xx, yy);
	        return Double.isFinite(r) ? r : 0.0;
	    } catch (Exception ignore) {
	        return 0.0;
	    }
	}

	/**
	 * 定数かどうか
	 * @param v
	 * @return
	 */
	private boolean isConstant(double[] v) {
	    double first = v[0];
	    for (int i = 1; i < v.length; i++) {
	        if (v[i] != first) return false;
	    }
	    return true;
	}

	/**
	 * doubleに変換する
	 * @param strArray
	 * @param isPercent
	 * @return
	 */
	private double[] convertToDoubleArray(String[] strArray, boolean isPercent) {
		double[] result = new double[strArray.length];
		int ind = 0;
		for (String val : strArray) {
			try {
				if (val == null || val.isBlank()) {
					result[ind] = 0.0;
				} else if (isPercent && val.contains("%")) {
					result[ind] = Double.parseDouble(val.replace("%", "").trim()) / 100.0;
				} else {
					result[ind] = Double.parseDouble(val.trim());
				}
			} catch (NumberFormatException e) {
				result[ind] = 0.0; // 異常値は0扱いに
			}
			ind++;
		}
		return result;
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
