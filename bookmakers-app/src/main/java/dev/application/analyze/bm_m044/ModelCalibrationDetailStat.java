package dev.application.analyze.bm_m044;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.entity.BookDataEntity;
import lombok.RequiredArgsConstructor;

/**
 * モデルのキャリブレーション詳細を算出するサービスです。
 *
 * <p>モデル名・モデルバージョン・目的変数名・ビン番号単位で、
 * 平均予測確率、実測率、サンプル件数を集計し、
 * ModelCalibrationDetailEntity として保存します。</p>
 *
 * <p>入力データは以下のどちらにも対応します。</p>
 * <ul>
 *   <li>binIndex / predictedProbAvg / actualRate / sampleCount を直接持つ集計済みデータ</li>
 *   <li>predictedProb / actualLabel を持つ明細予測データ</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ModelCalibrationDetailStat implements AnalyzeEntityIF {

    /** 小数スケールです。 */
    private static final int SCALE = 6;

    /** ビン数です。 */
    private static final int BIN_COUNT = 10;

    /** Writer です。 */
    private final ModelCalibrationDetailWriter modelCalibrationDetailWriter;

    /**
     * モデルのキャリブレーション詳細を算出します。
     *
     * @param entities 国・リーグ単位にグルーピングされた入力データ
     */
    @Override
    public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {

        if (entities == null || entities.isEmpty()) {
            return;
        }

        Map<String, CalibrationAccumulator> accumulatorMap = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, List<BookDataEntity>>> countryEntry : entities.entrySet()) {

            Map<String, List<BookDataEntity>> leagueMap = countryEntry.getValue();
            if (leagueMap == null || leagueMap.isEmpty()) {
                continue;
            }

            for (Map.Entry<String, List<BookDataEntity>> leagueEntry : leagueMap.entrySet()) {

                List<BookDataEntity> sourceEntities = leagueEntry.getValue();
                if (sourceEntities == null || sourceEntities.isEmpty()) {
                    continue;
                }

                decideBasedMain(sourceEntities, accumulatorMap);
            }
        }

        for (CalibrationAccumulator acc : accumulatorMap.values()) {
            ModelCalibrationDetailEntity entity = buildEntity(acc);
            modelCalibrationDetailWriter.insert(entity);
        }
    }

    /**
     * 入力一覧を集計します。
     *
     * @param sourceEntities 入力データ
     * @param accumulatorMap 集計マップ
     */
    private void decideBasedMain(
            List<BookDataEntity> sourceEntities,
            Map<String, CalibrationAccumulator> accumulatorMap) {

        for (BookDataEntity source : sourceEntities) {

            if (!isCalibrationRow(source)) {
                continue;
            }

            String modelName = trimToNull(readString(source, "modelName", "model_name"));
            String modelVersion = trimToNull(readString(source, "modelVersion", "model_version"));
            String targetName = trimToNull(readString(source, "targetName", "target_name"));

            Integer binIndex = resolveBinIndex(source);
            if (binIndex == null) {
                continue;
            }

            BigDecimal predictedProbAvg = resolvePredictedProb(source);
            BigDecimal actualRate = resolveActualRate(source);
            Integer sampleCount = resolveSampleCount(source);

            String key = buildKey(modelName, modelVersion, targetName, binIndex);

            CalibrationAccumulator acc = accumulatorMap.computeIfAbsent(
                    key,
                    k -> new CalibrationAccumulator(modelName, modelVersion, targetName, binIndex));

            acc.accept(predictedProbAvg, actualRate, sampleCount);
        }
    }

    /**
     * 集計結果から Entity を構築します。
     *
     * @param acc 集計データ
     * @return Entity
     */
    private ModelCalibrationDetailEntity buildEntity(CalibrationAccumulator acc) {

        ModelCalibrationDetailEntity entity = new ModelCalibrationDetailEntity();

        entity.setModelName(acc.modelName);
        entity.setModelVersion(acc.modelVersion);
        entity.setTargetName(acc.targetName);
        entity.setBinIndex(acc.binIndex);
        entity.setPredictedProbAvg(acc.predictedProbAverage());
        entity.setActualRate(acc.actualRateAverage());
        entity.setSampleCount(acc.totalSampleCount);

        return entity;
    }

    /**
     * キャリブレーション対象行かどうかを判定します。
     *
     * @param source 対象
     * @return 対象なら true
     */
    private boolean isCalibrationRow(BookDataEntity source) {

        return trimToNull(readString(source, "modelName", "model_name")) != null
                && (
                        readInteger(source, "binIndex", "bin_index") != null
                        || readBigDecimal(source, "predictedProb", "predictedProbability", "predicted_prob") != null
                        || readBigDecimal(source, "predictedProbAvg", "predicted_prob_avg") != null
                );
    }

    /**
     * ビン番号を解決します。
     *
     * <p>binIndex がある場合はそれを使用し、無ければ predictedProb から 0-9 ビンを作成します。</p>
     *
     * @param source 対象
     * @return ビン番号
     */
    private Integer resolveBinIndex(BookDataEntity source) {

        Integer binIndex = readInteger(source, "binIndex", "bin_index");
        if (binIndex != null) {
            return binIndex;
        }

        BigDecimal predictedProb = readBigDecimal(
                source,
                "predictedProb", "predictedProbability", "predicted_prob");

        if (predictedProb == null) {
            return null;
        }

        double p = clamp(predictedProb.doubleValue(), 0.0d, 0.999999d);
        return (int) Math.floor(p * BIN_COUNT);
    }

    /**
     * 平均予測確率を解決します。
     *
     * @param source 対象
     * @return 平均予測確率
     */
    private BigDecimal resolvePredictedProb(BookDataEntity source) {

        BigDecimal predictedProbAvg = readBigDecimal(
                source,
                "predictedProbAvg", "predicted_prob_avg");

        if (predictedProbAvg != null) {
            return predictedProbAvg;
        }

        return readBigDecimal(
                source,
                "predictedProb", "predictedProbability", "predicted_prob");
    }

    /**
     * 実測率を解決します。
     *
     * @param source 対象
     * @return 実測率
     */
    private BigDecimal resolveActualRate(BookDataEntity source) {

        BigDecimal actualRate = readBigDecimal(source, "actualRate", "actual_rate");
        if (actualRate != null) {
            return actualRate;
        }

        BigDecimal actualLabel = readBigDecimal(
                source,
                "actualLabel", "actual", "label", "isPositive", "actual_label");

        if (actualLabel != null) {
            return actualLabel;
        }

        return null;
    }

    /**
     * サンプル件数を解決します。
     *
     * @param source 対象
     * @return サンプル件数
     */
    private Integer resolveSampleCount(BookDataEntity source) {

        Integer sampleCount = readInteger(source, "sampleCount", "sample_count");
        if (sampleCount != null && sampleCount > 0) {
            return sampleCount;
        }

        return 1;
    }

    /**
     * 集計キーを生成します。
     *
     * @param modelName モデル名
     * @param modelVersion モデルバージョン
     * @param targetName 目的変数名
     * @param binIndex ビン番号
     * @return キー
     */
    private String buildKey(
            String modelName,
            String modelVersion,
            String targetName,
            Integer binIndex) {

        return String.valueOf(modelName)
                + "|" + String.valueOf(modelVersion)
                + "|" + String.valueOf(targetName)
                + "|" + String.valueOf(binIndex);
    }

    /**
     * BigDecimal を取得します。
     *
     * @param target 対象
     * @param fieldNames 候補フィールド名
     * @return BigDecimal
     */
    private BigDecimal readBigDecimal(Object target, String... fieldNames) {

        Object value = readField(target, fieldNames);
        if (value == null) {
            return null;
        }

        try {
            if (value instanceof BigDecimal) {
                return ((BigDecimal) value).setScale(SCALE, RoundingMode.HALF_UP);
            }
            if (value instanceof Integer) {
                return BigDecimal.valueOf((Integer) value).setScale(SCALE, RoundingMode.HALF_UP);
            }
            if (value instanceof Long) {
                return BigDecimal.valueOf((Long) value).setScale(SCALE, RoundingMode.HALF_UP);
            }
            if (value instanceof Double) {
                return BigDecimal.valueOf((Double) value).setScale(SCALE, RoundingMode.HALF_UP);
            }

            String str = String.valueOf(value).replace("%", "").replace(",", "").trim();
            if (str.isEmpty()) {
                return null;
            }

            return new BigDecimal(str).setScale(SCALE, RoundingMode.HALF_UP);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 整数を取得します。
     *
     * @param target 対象
     * @param fieldNames 候補フィールド名
     * @return 整数
     */
    private Integer readInteger(Object target, String... fieldNames) {

        Object value = readField(target, fieldNames);
        if (value == null) {
            return null;
        }

        try {
            if (value instanceof Integer) {
                return (Integer) value;
            }
            if (value instanceof Long) {
                return ((Long) value).intValue();
            }
            if (value instanceof BigDecimal) {
                return ((BigDecimal) value).intValue();
            }

            String str = String.valueOf(value).replace("%", "").replace(",", "").trim();
            if (str.isEmpty()) {
                return null;
            }

            return Integer.valueOf(str);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 文字列を取得します。
     *
     * @param target 対象
     * @param fieldNames 候補フィールド名
     * @return 文字列
     */
    private String readString(Object target, String... fieldNames) {

        Object value = readField(target, fieldNames);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * フィールド値をリフレクションで取得します。
     *
     * @param target 対象
     * @param fieldNames 候補フィールド名
     * @return 値
     */
    private Object readField(Object target, String... fieldNames) {

        if (target == null || fieldNames == null) {
            return null;
        }

        Class<?> clazz = target.getClass();

        for (String fieldName : fieldNames) {
            if (fieldName == null) {
                continue;
            }

            Field field = findField(clazz, fieldName);
            if (field == null) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object value = field.get(target);
                if (value != null) {
                    return value;
                }
            } catch (Exception e) {
                // no-op
            }
        }

        return null;
    }

    /**
     * フィールドを探索します。
     *
     * @param clazz クラス
     * @param fieldName フィールド名
     * @return フィールド
     */
    private Field findField(Class<?> clazz, String fieldName) {

        Class<?> current = clazz;

        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }

        return null;
    }

    /**
     * trim後に空文字なら null を返します。
     *
     * @param value 値
     * @return 変換後
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 値を範囲制限します。
     *
     * @param value 値
     * @param min 最小
     * @param max 最大
     * @return 制限後
     */
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * キャリブレーション集計用の内部クラスです。
     */
    private static class CalibrationAccumulator {

        /** モデル名です。 */
        private final String modelName;

        /** モデルバージョンです。 */
        private final String modelVersion;

        /** 目的変数名です。 */
        private final String targetName;

        /** ビン番号です。 */
        private final Integer binIndex;

        /** 予測確率合計です。 */
        private BigDecimal predictedProbSum = BigDecimal.ZERO;

        /** 実測率合計です。 */
        private BigDecimal actualRateSum = BigDecimal.ZERO;

        /** 実測率件数です。 */
        private int actualRateCount;

        /** 総サンプル件数です。 */
        private int totalSampleCount;

        /**
         * コンストラクタです。
         *
         * @param modelName モデル名
         * @param modelVersion モデルバージョン
         * @param targetName 目的変数名
         * @param binIndex ビン番号
         */
        private CalibrationAccumulator(
                String modelName,
                String modelVersion,
                String targetName,
                Integer binIndex) {
            this.modelName = modelName;
            this.modelVersion = modelVersion;
            this.targetName = targetName;
            this.binIndex = binIndex;
        }

        /**
         * 1件分を反映します。
         *
         * @param predictedProbAvg 予測確率
         * @param actualRate 実測率または実ラベル
         * @param sampleCount サンプル件数
         */
        private void accept(
                BigDecimal predictedProbAvg,
                BigDecimal actualRate,
                Integer sampleCount) {

            int count = sampleCount == null || sampleCount <= 0 ? 1 : sampleCount;

            if (predictedProbAvg != null) {
                this.predictedProbSum = this.predictedProbSum.add(
                        predictedProbAvg.multiply(BigDecimal.valueOf(count)));
            }

            if (actualRate != null) {
                this.actualRateSum = this.actualRateSum.add(
                        actualRate.multiply(BigDecimal.valueOf(count)));
                this.actualRateCount += count;
            }

            this.totalSampleCount += count;
        }

        /**
         * 平均予測確率を返します。
         *
         * @return 平均予測確率
         */
        private BigDecimal predictedProbAverage() {

            if (totalSampleCount == 0) {
                return BigDecimal.ZERO;
            }

            return predictedProbSum.divide(
                    BigDecimal.valueOf(totalSampleCount),
                    SCALE,
                    RoundingMode.HALF_UP);
        }

        /**
         * 実測率平均を返します。
         *
         * @return 実測率
         */
        private BigDecimal actualRateAverage() {

            if (actualRateCount == 0) {
                return BigDecimal.ZERO;
            }

            return actualRateSum.divide(
                    BigDecimal.valueOf(actualRateCount),
                    SCALE,
                    RoundingMode.HALF_UP);
        }
    }
}
