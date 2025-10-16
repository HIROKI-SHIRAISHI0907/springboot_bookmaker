package dev.application.analyze.bm_m024;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
 * <p>BM_M024 相関分析ロジック。</p>
 *
 * <ul>
 *   <li>入力: 国×リーグ×カード単位の {@code BookDataEntity} 群</li>
 *   <li>処理: ALL/FIRST/SECOND でフィルタした時系列からフラグ（得点発生）と特徴量の
 *       ピアソン相関係数を算出</li>
 *   <li>出力: {@code CalcCorrelationEntity} をリポジトリ経由で登録</li>
 * </ul>
 *
 * <h3>スレッドモデル</h3>
 * <p>
 * 解析（ALL/FIRST/SECOND）自体は同期で実行し、DB登録のみを
 * Config で注入した共有 {@link ExecutorService} に非同期投入します。
 * 本クラス内で新規スレッドプールは生成しません。
 * </p>
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
    private static final String CLASS_NAME = CalcCorrelationStat.class.getSimpleName();

    /** 実行モード（ログ用） */
    private static final String EXEC_MODE = "BM_M024_CALC_CORRELATION";

    /** 相関結果登録レポジトリ */
    @Autowired
    private CalcCorrelationRepository calcCorrelationRepository;

    /** 例外ラッパー（件数不整合などの例外化） */
    @Autowired
    private RootCauseWrapper rootCauseWrapper;

    /** ログ管理コンポーネント */
    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

    /**
     * <p>Config で定義した共有 Executor（例：{@code @Bean @Qualifier("calcCorrelationExecutor")}）。</p>
     * <p>未注入の場合は {@link ForkJoinPool#commonPool()} にフォールバックします。</p>
     */
    @Autowired(required = false)
    @Qualifier("calcCorrelationExecutor")
    private ExecutorService injectedExecutor;

    /**
     * 使用する Executor を解決します。
     *
     * @return 注入済み Executor、なければ {@code ForkJoinPool.commonPool()}
     */
    private Executor getExecutor() {
        return (injectedExecutor != null) ? injectedExecutor : ForkJoinPool.commonPool();
    }

    /**
     * {@inheritDoc}
     *
     * <p>全ての国・リーグ・カードを走査し、相関計算→登録を行います。</p>
     * <p>登録のみ非同期実行・集約待機します。</p>
     *
     * @implSpec 本メソッドは内部で新規スレッドプールを生成しません。
     */
    @Override
    public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
        final String METHOD_NAME = "calcStat";
        manageLoggerComponent.init(EXEC_MODE, null);
        manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

        // 国×リーグを横断して解析結果を集約
        ConcurrentHashMap<String, CalcCorrelationEntity> resultMap = new ConcurrentHashMap<>();

        for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
            String[] data_category = ExecuteMainUtil.splitLeagueInfo(entry.getKey());
            String country = data_category[0];
            String league  = data_category[1];

            Map<String, List<BookDataEntity>> entrySub = entry.getValue();
            for (List<BookDataEntity> entityList : entrySub.values()) {
                if (entityList == null || entityList.isEmpty()) continue;

                String home = entityList.get(0).getHomeTeamName();
                String away = entityList.get(0).getAwayTeamName();

                // 計算は同期（軽量）で実施
                ConcurrentHashMap<String, CalcCorrelationEntity> partialMap =
                        decideBasedMain(entityList, country, league, home, away);

                resultMap.putAll(partialMap);
            }
        }

        // 登録のみを非同期実行
        List<CompletableFuture<Void>> futures = new ArrayList<>(resultMap.size());
        Executor exec = getExecutor();
        for (CalcCorrelationEntity entity : resultMap.values()) {
            futures.add(CompletableFuture.runAsync(() -> insert(entity), exec));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
        manageLoggerComponent.clear();
    }

    /**
     * 1カードに対して ALL/FIRST/SECOND の3種を順次計算します（同期）。
     *
     * @param entities 試合時系列エンティティ
     * @param country  国
     * @param league   リーグ
     * @param home     ホームチーム名
     * @param away     アウェーチーム名
     * @return 計算済みエンティティを格納したスレッドセーフなマップ
     */
    private ConcurrentHashMap<String, CalcCorrelationEntity> decideBasedMain(
            List<BookDataEntity> entities, String country, String league, String home, String away) {

        manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, null, null, entities.get(0).getFilePath());

        List<String> flgs = List.of(
                AverageStatisticsSituationConst.ALL_DATA,
                AverageStatisticsSituationConst.FIRST_DATA,
                AverageStatisticsSituationConst.SECOND_DATA
        );

        ConcurrentHashMap<String, CalcCorrelationEntity> allMap = new ConcurrentHashMap<>();
        for (String flg : flgs) {
            basedEntities(allMap, entities, flg, country, league, home, away);
        }
        return allMap;
    }

    /**
     * 指定フラグ（ALL/FIRST/SECOND）でエンティティをフィルタし、相関係数を算出します。
     * 結果は {@code insertMap} にスレッドセーフに格納します。
     *
     * @param insertMap 出力マップ
     * @param entities  時系列エンティティ
     * @param flg       解析フラグ
     * @param country   国
     * @param league    リーグ
     * @param home      ホームチーム
     * @param away      アウェーチーム
     */
    private void basedEntities(ConcurrentHashMap<String, CalcCorrelationEntity> insertMap,
                               List<BookDataEntity> entities, String flg,
                               String country, String league, String home, String away) {

        // フィルタリング
        List<BookDataEntity> filteredList = null;
        if (AverageStatisticsSituationConst.ALL_DATA.equals(flg)) {
            filteredList = entities;
        } else {
            String halfTimeSeq = ExecuteMainUtil.getHalfEntities(entities).getSeq();
            if (AverageStatisticsSituationConst.FIRST_DATA.equals(flg)) {
                filteredList = entities.stream()
                        .filter(e -> e.getSeq().compareTo(halfTimeSeq) <= 0)
                        .collect(Collectors.toList());
            } else if (AverageStatisticsSituationConst.SECOND_DATA.equals(flg)) {
                filteredList = entities.stream()
                        .filter(e -> e.getSeq().compareTo(halfTimeSeq) > 0)
                        .collect(Collectors.toList());
            }
        }
        if (filteredList == null || filteredList.isEmpty()) return;

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
        final Field[] inFields  = BookDataEntity.class.getDeclaredFields();

        // 出力/入力のフィールド開始位置（設計上の固定オフセット）
        final int OUT_OFFSET = 9;
        final int IN_START   = 11;
        final int IN_END     = Math.min(inFields.length, IN_START + AverageStatisticsSituationConst.COUNTER);

        int outIdx = 0;

        // 特徴量を走査し相関を計算
        for (int inIdx = IN_START; inIdx < IN_END; inIdx++) {
            Field f = inFields[inIdx];
            f.setAccessible(true);
            String name = f.getName();

            if (isTriSplitFieldName(name)) {
                // 「比率%」「成功数」「試行数」の3系列を抽出して個別に相関
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
                        var t = split3Safe(raw);
                        xRatio[pos] = t.getLeft();
                        xCount[pos] = t.getMiddle();
                        xTry[pos]   = t.getRight();
                        yList[pos]  = makeFlag(prev, curr, name);
                        if (BookMakersCommonConst.GOAL_DELETE.equals(prev.getJudge())
                                || BookMakersCommonConst.GOAL_DELETE.equals(curr.getJudge())) {
                            yList[pos] = "0";
                        }
                    }
                } catch (Exception e) {
                    manageLoggerComponent.debugErrorLog(
                            PROJECT_NAME, CLASS_NAME, "calc-corr-trisplit",
                            "tri-split 抽出失敗: " + name, e);
                    setOut(outFields, entity, OUT_OFFSET + outIdx++, 0.0);
                    setOut(outFields, entity, OUT_OFFSET + outIdx++, 0.0);
                    setOut(outFields, entity, OUT_OFFSET + outIdx++, 0.0);
                    continue;
                }

                double[] yD = convertToDoubleArray(yList, false);

                double[] xr = convertToDoubleArray(xRatio, firstNonNullContainsPercent(xRatio));
                double  pr  = calculatePearsonCorrelation(xr, yD);

                double[] xc = convertToDoubleArray(xCount, false);
                double  pc  = calculatePearsonCorrelation(xc, yD);

                double[] xt = convertToDoubleArray(xTry, false);
                double  pt  = calculatePearsonCorrelation(xt, yD);

                setOut(outFields, entity, OUT_OFFSET + outIdx++, pr);
                setOut(outFields, entity, OUT_OFFSET + outIdx++, pc);
                setOut(outFields, entity, OUT_OFFSET + outIdx++, pt);
            } else {
                // 単一系列
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
                    manageLoggerComponent.debugErrorLog(
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

        // スレッドセーフに格納（キーは次元の直積）
        String mapKey = String.join("|", country, league, home, away, flg, chkBody);
        insertMap.put(mapKey, entity);
    }

    /**
     * 相関係数を出力フィールドに設定します（小数点以下5桁で文字列化）。
     *
     * @param outFields 出力側フィールド配列
     * @param entity    出力エンティティ
     * @param idx       書き込みインデックス
     * @param value     値
     */
    private void setOut(Field[] outFields, CalcCorrelationEntity entity, int idx, double value) {
        if (idx < 0 || idx >= outFields.length) return;
        Field out = outFields[idx];
        out.setAccessible(true);
        try {
            out.set(entity, String.format("%.5f", value));
        } catch (IllegalAccessException e) {
            manageLoggerComponent.debugErrorLog(
                    PROJECT_NAME, CLASS_NAME, "setOut",
                    "相関係数設定失敗: " + out.getName(), e);
        }
    }

    /**
     * 得点フラグを生成します。フィールド名に "home"/"away" を含むかで
     * 該当側の得点増加（直前との差分）を 1、それ以外を 0 とします。
     *
     * @param prev      直前スナップショット
     * @param curr      現在スナップショット
     * @param fieldName 特徴量フィールド名
     * @return "1"（増加） or "0"（増加なし）
     */
    private String makeFlag(BookDataEntity prev, BookDataEntity curr, String fieldName) {
        int prevHome = Integer.parseInt(prev.getHomeScore());
        int currHome = Integer.parseInt(curr.getHomeScore());
        int prevAway = Integer.parseInt(prev.getAwayScore());
        int currAway = Integer.parseInt(curr.getAwayScore());
        String n = fieldName.toLowerCase();
        if (n.contains("home")) return (currHome > prevHome) ? "1" : "0";
        if (n.contains("away")) return (currAway > prevAway) ? "1" : "0";
        return "0";
    }

    /**
     * 最初に見つかった非 null 要素が % を含むかを判定します。
     *
     * @param arr 文字列配列
     * @return true: パーセント表現 / false: 数値表現
     */
    private boolean firstNonNullContainsPercent(String[] arr) {
        for (String s : arr) if (s != null) return s.contains("%");
        return false;
    }

    /**
     * 相関結果を 1 件登録します。結果件数が 1 以外の場合は例外化します。
     * <p><b>スレッドセーフ</b>のため {@code synchronized} としています。</p>
     *
     * @param entity 登録対象
     * @throws RuntimeException 期待件数不一致時
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
                    messageCd, 1, result, null);
        }
        String messageCd = "登録件数";
        this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "BM_M024 登録件数: 1件");
    }

    /**
     * ピアソン相関係数を安全に算出します。
     * <ul>
     *   <li>NaN/∞ を除外</li>
     *   <li>片方が定数系列（分散ゼロ）のときは 0.0 を返す</li>
     *   <li>長さが 2 未満のときは 0.0 を返す</li>
     * </ul>
     *
     * @param x 説明変数系列
     * @param y 目的変数系列
     * @return 相関係数（[-1,1]）、計算不可時は 0.0
     */
    public double calculatePearsonCorrelation(double[] x, double[] y) {
        if (x == null || y == null) return 0.0;
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

        if (isConstant(xx) || isConstant(yy)) return 0.0;

        try {
            double r = new PearsonsCorrelation().correlation(xx, yy);
            return Double.isFinite(r) ? r : 0.0;
        } catch (Exception ignore) {
            return 0.0;
        }
    }

    /**
     * 与えられた系列が定数系列かどうかを判定します。
     *
     * @param v 判定対象
     * @return true: 全要素が同一 / false: そうでない
     */
    private boolean isConstant(double[] v) {
        double first = v[0];
        for (int i = 1; i < v.length; i++) if (v[i] != first) return false;
        return true;
    }

    /**
     * 文字列配列を double 配列へ変換します。
     * <ul>
     *   <li>{@code isPercent} が true のとき、末尾「%」を除去して 100 で割る</li>
     *   <li>空/変換不可は 0.0 扱い</li>
     * </ul>
     *
     * @param strArray 入力配列
     * @param isPercent パーセント表現か
     * @return 変換結果
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
                result[ind] = 0.0;
            }
            ind++;
        }
        return result;
    }

    /**
     * ログの埋め字を生成します。
     *
     * @param chk_body 状況（例：PEARSON）
     * @param score    スコア種別（ALL/FIRST/SECOND）
     * @param country  国
     * @param league   リーグ
     * @param home     ホーム
     * @param away     アウェー
     * @return ログ用の連結文字列
     */
    private String setLoggerFillChar(String chk_body, String score,
                                     String country, String league, String home, String away) {
        StringBuilder sb = new StringBuilder();
        sb.append("調査内容: ").append(chk_body).append(", ");
        sb.append("スコア: ").append(score).append(", ");
        sb.append("国: ").append(country).append(", ");
        sb.append("リーグ: ").append(league).append(", ");
        sb.append("ホーム: ").append(home).append(", ");
        sb.append("アウェー: ").append(away);
        return sb.toString();
    }

}
