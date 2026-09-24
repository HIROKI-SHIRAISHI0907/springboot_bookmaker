package dev.application.analyze.bm_m024;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.bm_m023.AverageStatisticsSituationConst;
import dev.application.analyze.bm_m023.StatFormatResolver;
import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * <p>BM_M024 相関分析ロジック。（手動データ投入の場合は適用対象外）</p>
 *
 * <ul>
 *   <li>入力: 国×リーグ×カード単位の {@code BookDataEntity} 群</li>
 *   <li>処理: ALL/FIRST/SECOND でフィルタした時系列からフラグ（得点発生）と特徴量の
 *       ピアソン相関係数を算出</li>
 *   <li>出力: {@code CalcCorrelationEntity} をリポジトリ経由で登録</li>
 * </ul>
 *
 * 【修正履歴】
 * ・通番(seq)を文字列比較していたため、桁数が変わると（例: "9" と "10"）前半/後半の判定や
 *   並び順が崩れる不具合を修正（数値比較・数値順ソートに変更）
 * ・時系列が通番順に並んでいる前提で「直前との差分」を取っていたため、通番順にソートしてから処理
 * ・空値・変換不可の値を 0.0 として相関に含めていたのを、欠損(NaN)として除外するよう変更
 * ・スコアが空の場合に makeFlag の Integer.parseInt で例外になり、その特徴量全体が 0.0 に
 *   なっていたのを安全化
 * ・% 表記の判定を「先頭の非null要素」ではなく値ごとに行うよう変更（表記揺れ対策）
 * ・ログ出力で double[] が参照値で出ていたのを内容が出るよう修正
 *
 * @author shiraishitoshio
 * @since 1.0
 */
@Component
public class CalcCorrelationStat extends StatFormatResolver implements AnalyzeEntityIF {

	/** プロジェクト名（ログ用） */
	private static final String PROJECT_NAME = CalcCorrelationStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名（ログ用） */
	private static final String CLASS_NAME = CalcCorrelationStat.class.getName();

	/** 実行モード（ログ用） */
	private static final String EXEC_MODE = "BM_M024_CALC_CORRELATION";

	/** 相関結果DB永続化サービス */
	@Autowired
	private CalcCorrelationWriter calcCorrelationWriter;

	/** ログ管理コンポーネント */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 *
	 * <p>全ての国・リーグ・カードを走査し、相関計算→登録を行います。</p>
	 *
	 * @implSpec 本メソッドは内部で新規スレッドプールを生成しません。
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		manageLoggerComponent.init(EXEC_MODE, null);
		manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		try {
			if (entities == null || entities.isEmpty()) {
				return;
			}

			ConcurrentHashMap<String, CalcCorrelationEntity> resultMap = new ConcurrentHashMap<>();

			for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
				String[] data_category = ExecuteMainUtil.splitLeagueInfo(entry.getKey());
				String country = data_category[0];
				String league = data_category[1];

				Map<String, List<BookDataEntity>> entrySub = entry.getValue();
				if (entrySub == null) {
					continue;
				}
				for (List<BookDataEntity> entityList : entrySub.values()) {
					if (entityList == null || entityList.isEmpty()) {
						continue;
					}

					// 【修正】通番の数値順に並べ替え（直前との差分・前後半判定の前提）
					List<BookDataEntity> sorted = sortBySeq(entityList);

					String home = sorted.get(0).getHomeTeamName();
					String away = sorted.get(0).getAwayTeamName();

					ConcurrentHashMap<String, CalcCorrelationEntity> partialMap =
							decideBasedMain(sorted, country, league, home, away);

					resultMap.putAll(partialMap);
				}
			}

			for (CalcCorrelationEntity entity : resultMap.values()) {
				calcCorrelationWriter.insert(entity);
			}
		} finally {
			manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			manageLoggerComponent.clear();
		}
	}

	/**
	 * 1カードに対して ALL/FIRST/SECOND の3種を順次計算します（同期）。
	 */
	private ConcurrentHashMap<String, CalcCorrelationEntity> decideBasedMain(
			List<BookDataEntity> entities, String country, String league, String home, String away) {

		manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, null, null, entities.get(0).getFilePath());

		List<String> flgs = List.of(
				AverageStatisticsSituationConst.ALL_DATA,
				AverageStatisticsSituationConst.FIRST_DATA,
				AverageStatisticsSituationConst.SECOND_DATA);

		ConcurrentHashMap<String, CalcCorrelationEntity> allMap = new ConcurrentHashMap<>();
		for (String flg : flgs) {
			basedEntities(allMap, entities, flg, country, league, home, away);
		}
		return allMap;
	}

	/**
	 * 指定フラグ（ALL/FIRST/SECOND）でエンティティをフィルタし、相関係数を算出します。
	 */
	private void basedEntities(ConcurrentHashMap<String, CalcCorrelationEntity> insertMap,
			List<BookDataEntity> entities, String flg,
			String country, String league, String home, String away) {
		final String METHOD_NAME = "basedEntities";
		// フィルタリング
		List<BookDataEntity> filteredList = null;
		if (AverageStatisticsSituationConst.ALL_DATA.equals(flg)) {
			filteredList = entities;
		} else {
			BookDataEntity half = ExecuteMainUtil.getHalfEntities(entities);
			if (half == null || half.getSeq() == null) {
				manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, null,
						"half not found -> skip FIRST/SECOND. file=" + entities.get(0).getFilePath()
								+ ", size=" + entities.size()
								+ ", country=" + country + ", league=" + league
								+ ", home=" + home + ", away=" + away);
				return; // FIRST/SECOND は計算不能なのでスキップ
			}
			// 【修正】通番は数値で比較（文字列比較だと "10" < "9" になる）
			final long halfTimeSeq = seqToLong(half.getSeq());
			if (AverageStatisticsSituationConst.FIRST_DATA.equals(flg)) {
				filteredList = entities.stream()
						.filter(e -> seqToLong(e.getSeq()) <= halfTimeSeq)
						.collect(Collectors.toList());
			} else if (AverageStatisticsSituationConst.SECOND_DATA.equals(flg)) {
				filteredList = entities.stream()
						.filter(e -> seqToLong(e.getSeq()) > halfTimeSeq)
						.collect(Collectors.toList());
			}
		}
		// 差分を取るため最低2件必要
		if (filteredList == null || filteredList.size() < 2) {
			return;
		}

		// 解析種別（今は Pearson 固定）
		String chkBody = CalcCorrelationConst.PEARSON;

		// 出力エンティティ初期化
		CalcCorrelationEntity entity = new CalcCorrelationEntity();
		entity.setCountry(country);
		entity.setLeague(league);
		entity.setHome(home);
		entity.setAway(away);
		entity.setScore(flg);
		entity.setChkBody(chkBody);

		final Field[] outFields = CalcCorrelationEntity.class.getDeclaredFields();
		final Field[] inFields = BookDataEntity.class.getDeclaredFields();

		// 出力/入力のフィールド開始位置（設計上の固定オフセット）
		// OUT_OFFSET=9 : CalcCorrelationEntity の homeExpInfo
		// IN_START=11  : BookDataEntity の homeExp
		final int OUT_OFFSET = 9;
		final int IN_START = 11;
		final int IN_END = Math.min(inFields.length, IN_START + AverageStatisticsSituationConst.COUNTER);

		int outIdx = 0;

		// 特徴量を走査し相関を計算
		for (int inIdx = IN_START; inIdx < IN_END; inIdx++) {
			Field f = inFields[inIdx];
			f.setAccessible(true);
			String name = f.getName();

			// 目的変数（得点フラグ）は特徴量ごとに home/away で決まる
			int n = filteredList.size() - 1;
			String[] yList = new String[n];

			if (isTriSplitFieldName(name)) {
				// 「比率%」「成功数」「試行数」の3系列を抽出して個別に相関
				String[] xRatio = new String[n];
				String[] xCount = new String[n];
				String[] xTry = new String[n];

				try {
					for (int ent = 1; ent < filteredList.size(); ent++) {
						int pos = ent - 1;
						BookDataEntity prev = filteredList.get(ent - 1);
						BookDataEntity curr = filteredList.get(ent);
						String raw = (String) f.get(curr);
						var t = split3Safe(raw);
						xRatio[pos] = t.getLeft();
						xCount[pos] = t.getMiddle();
						xTry[pos] = t.getRight();
						yList[pos] = makeFlag(prev, curr, name);
					}
				} catch (Exception e) {
					String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, "tri-split 抽出失敗: " + name);
					setOut(outFields, entity, OUT_OFFSET + outIdx++, 0.0);
					setOut(outFields, entity, OUT_OFFSET + outIdx++, 0.0);
					setOut(outFields, entity, OUT_OFFSET + outIdx++, 0.0);
					continue;
				}

				double[] yD = convertToDoubleArray(yList);

				double pr = calculatePearsonCorrelation(convertToDoubleArray(xRatio), yD);
				double pc = calculatePearsonCorrelation(convertToDoubleArray(xCount), yD);
				double pt = calculatePearsonCorrelation(convertToDoubleArray(xTry), yD);

				setOut(outFields, entity, OUT_OFFSET + outIdx++, pr);
				setOut(outFields, entity, OUT_OFFSET + outIdx++, pc);
				setOut(outFields, entity, OUT_OFFSET + outIdx++, pt);
			} else {
				// 単一系列
				String[] xList = new String[n];

				try {
					for (int ent_ind = 1; ent_ind < filteredList.size(); ent_ind++) {
						int pos = ent_ind - 1;
						BookDataEntity prev = filteredList.get(ent_ind - 1);
						BookDataEntity curr = filteredList.get(ent_ind);

						xList[pos] = (String) f.get(curr);
						yList[pos] = makeFlag(prev, curr, name);
					}
				} catch (Exception e) {
					String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
							"calc-corr-single 単一抽出失敗: " + name);
					setOut(outFields, entity, OUT_OFFSET + outIdx++, 0.0);
					continue;
				}

				double pearson = calculatePearsonCorrelation(convertToDoubleArray(xList), convertToDoubleArray(yList));
				setOut(outFields, entity, OUT_OFFSET + outIdx++, pearson);
			}
		}

		String mapKey = String.join("|", country, league, home, away, flg, chkBody);
		insertMap.put(mapKey, entity);
	}

	/**
	 * 相関係数を出力フィールドに設定します（小数点以下5桁で文字列化）。
	 */
	private void setOut(Field[] outFields, CalcCorrelationEntity entity, int idx, double value) {
		final String METHOD_NAME = "setOut";
		if (idx < 0 || idx >= outFields.length) {
			return;
		}
		Field out = outFields[idx];
		out.setAccessible(true);
		try {
			out.set(entity, String.format("%.5f", value));
		} catch (IllegalAccessException e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
					"相関係数設定失敗: " + out.getName());
		}
	}

	/**
	 * 得点フラグを生成します。フィールド名に "home"/"away" を含むかで
	 * 該当側の得点増加（直前との差分）を 1、それ以外を 0 とします。
	 * ゴール取り消し（GOAL_DELETE）が絡む区間は 0 とします。
	 * 【修正】スコアが空・数値でない場合は例外にせず、欠損（null）を返して相関計算から除外
	 */
	private String makeFlag(BookDataEntity prev, BookDataEntity curr, String fieldName) {
		if (BookMakersCommonConst.GOAL_DELETE.equals(prev.getJudge())
				|| BookMakersCommonConst.GOAL_DELETE.equals(curr.getJudge())) {
			return "0";
		}
		Integer prevHome = toIntOrNull(prev.getHomeScore());
		Integer currHome = toIntOrNull(curr.getHomeScore());
		Integer prevAway = toIntOrNull(prev.getAwayScore());
		Integer currAway = toIntOrNull(curr.getAwayScore());
		String n = fieldName.toLowerCase();
		if (n.startsWith("home")) {
			if (prevHome == null || currHome == null) {
				return null;
			}
			return (currHome > prevHome) ? "1" : "0";
		}
		if (n.startsWith("away")) {
			if (prevAway == null || currAway == null) {
				return null;
			}
			return (currAway > prevAway) ? "1" : "0";
		}
		return "0";
	}

	/**
	 * ピアソン相関係数を安全に算出します。
	 * <ul>
	 *   <li>NaN/∞（欠損）を含む組は除外</li>
	 *   <li>片方が定数系列（分散ゼロ）のときは 0.0 を返す</li>
	 *   <li>有効な組が 2 未満のときは 0.0 を返す</li>
	 * </ul>
	 */
	public double calculatePearsonCorrelation(double[] x, double[] y) {
		final String METHOD_NAME = "calculatePearsonCorrelation";
		if (x == null || y == null) {
			return 0.0;
		}
		int n = Math.min(x.length, y.length);
		List<Double> xs = new ArrayList<>(n);
		List<Double> ys = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			double a = x[i];
			double b = y[i];
			if (Double.isFinite(a) && Double.isFinite(b)) {
				xs.add(a);
				ys.add(b);
			}
		}
		if (xs.size() < 2) {
			return 0.0;
		}

		double[] xx = xs.stream().mapToDouble(Double::doubleValue).toArray();
		double[] yy = ys.stream().mapToDouble(Double::doubleValue).toArray();

		if (isConstant(xx) || isConstant(yy)) {
			return 0.0;
		}

		try {
			double r = new PearsonsCorrelation().correlation(xx, yy);
			return Double.isFinite(r) ? r : 0.0;
		} catch (Exception ex) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, ex,
					"説明変数系列: " + Arrays.toString(xx) + ", 目的変数系列: " + Arrays.toString(yy));
			return 0.0;
		}
	}

	/**
	 * 与えられた系列が定数系列かどうかを判定します。
	 */
	private boolean isConstant(double[] v) {
		double first = v[0];
		for (int i = 1; i < v.length; i++) {
			if (v[i] != first) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 文字列配列を double 配列へ変換します。
	 * 【修正】
	 * <ul>
	 *   <li>「%」は値ごとに除去（相関は尺度に依存しないため 100 で割る必要はない）</li>
	 *   <li>空・変換不可は 0.0 ではなく NaN（欠損）とし、相関計算から除外</li>
	 * </ul>
	 */
	private double[] convertToDoubleArray(String[] strArray) {
		double[] result = new double[strArray.length];
		for (int i = 0; i < strArray.length; i++) {
			String val = strArray[i];
			if (val == null || val.isBlank()) {
				result[i] = Double.NaN;
				continue;
			}
			try {
				result[i] = Double.parseDouble(val.replace("%", "").trim());
			} catch (NumberFormatException e) {
				result[i] = Double.NaN;
			}
		}
		return result;
	}

	/**
	 * 【追加】通番の数値順に並べ替えた新しいリストを返す（元のリストは変更しない）
	 */
	private List<BookDataEntity> sortBySeq(List<BookDataEntity> list) {
		List<BookDataEntity> copy = new ArrayList<>(list);
		copy.sort(Comparator.comparingLong(e -> seqToLong(e.getSeq())));
		return copy;
	}

	/**
	 * 【追加】通番を数値化（変換不可は末尾扱い）
	 */
	private static long seqToLong(String seq) {
		if (seq == null || seq.isBlank()) {
			return Long.MAX_VALUE;
		}
		try {
			return Long.parseLong(seq.trim());
		} catch (NumberFormatException e) {
			return Long.MAX_VALUE;
		}
	}

	/**
	 * 【追加】整数変換（変換不可は null）
	 */
	private static Integer toIntOrNull(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
