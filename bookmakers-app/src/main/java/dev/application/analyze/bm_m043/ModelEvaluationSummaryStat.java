package dev.application.analyze.bm_m043;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.application.analyze.common.util.ModelTaskType;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.entity.BookDataEntity;
import lombok.RequiredArgsConstructor;

/**
 * 予測モデル評価サマリを算出するサービスです。
 *
 * <p>分類モデル・回帰モデルの評価値をモデル単位で集約し、
 * ModelEvaluationSummaryEntity として保存します。</p>
 *
 * <p>入力データ中の modelName / modelVersion / taskType / targetName /
 * seasonRange / validationMethod をキーとしてまとめ、
 * 各評価指標の平均値を算出します。</p>
 */
@Component
@RequiredArgsConstructor
public class ModelEvaluationSummaryStat implements AnalyzeEntityIF {

    /** 小数スケールです。 */
    private static final int SCALE = 6;

    /** 記録日時フォーマット候補1です。 */
    private static final DateTimeFormatter RECORD_TIME_FORMATTER_1 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX");

    /** 記録日時フォーマット候補2です。 */
    private static final DateTimeFormatter RECORD_TIME_FORMATTER_2 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX");

    /** Writer です。 */
    private final ModelEvaluationSummaryWriter modelEvaluationSummaryWriter;

    /**
     * モデル評価サマリを算出します。
     *
     * @param entities 国・リーグ単位にグルーピングされた入力データ
     */
    @Override
    public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {

        if (entities == null || entities.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Map<String, List<BookDataEntity>>> countryEntry : entities.entrySet()) {

            String country = trimToNull(countryEntry.getKey());
            Map<String, List<BookDataEntity>> leagueMap = countryEntry.getValue();

            if (leagueMap == null || leagueMap.isEmpty()) {
                continue;
            }

            for (Map.Entry<String, List<BookDataEntity>> leagueEntry : leagueMap.entrySet()) {

                String leagueKey = trimToNull(leagueEntry.getKey());
                List<BookDataEntity> sourceEntities = leagueEntry.getValue();

                if (sourceEntities == null || sourceEntities.isEmpty()) {
                    continue;
                }

                String leagueId = resolveLeagueId(leagueKey);
                String leagueName = resolveLeagueName(leagueKey);

                decideBasedMain(country, leagueId, leagueName, sourceEntities);
            }
        }
    }

    /**
     * リーグ単位の入力からモデル評価サマリを集約・保存します。
     *
     * @param country 国
     * @param leagueId リーグID
     * @param leagueName リーグ名
     * @param sourceEntities 入力データ
     */
    private void decideBasedMain(
            String country,
            String leagueId,
            String leagueName,
            List<BookDataEntity> sourceEntities) {

        Map<String, ModelEvaluationAccumulator> accumulatorMap = new LinkedHashMap<>();

        for (BookDataEntity source : sourceEntities) {

            if (!isEvaluationRow(source)) {
                continue;
            }

            String modelName = trimToNull(readString(source, "modelName", "model_name"));
            String modelVersion = trimToNull(readString(source, "modelVersion", "model_version"));
            ModelTaskType taskType = parseTaskType(readString(source, "taskType", "task_type"));
            String targetName = trimToNull(readString(source, "targetName", "target_name"));
            String seasonRange = trimToNull(readString(source, "seasonRange", "season_range"));
            String validationMethod = trimToNull(readString(source, "validationMethod", "validation_method"));

            String modelKey = buildModelKey(
                    country,
                    leagueId,
                    modelName,
                    modelVersion,
                    taskType,
                    targetName,
                    seasonRange,
                    validationMethod);

            ModelEvaluationAccumulator acc = accumulatorMap.computeIfAbsent(
                    modelKey,
                    k -> new ModelEvaluationAccumulator(
                            country,
                            leagueId,
                            leagueName,
                            modelName,
                            modelVersion,
                            taskType,
                            targetName,
                            seasonRange,
                            validationMethod));

            acc.accept(
                    readBigDecimal(source, "accuracy", "accuracyScore", "accuracy_score"),
                    readBigDecimal(source, "precisionScore", "precision", "precision_score"),
                    readBigDecimal(source, "recallScore", "recall", "recall_score"),
                    readBigDecimal(source, "f1Score", "f1", "f1_score"),
                    readBigDecimal(source, "rocAuc", "roc_auc", "rocAucScore"),
                    readBigDecimal(source, "prAuc", "pr_auc", "prAucScore"),
                    readBigDecimal(source, "brierScore", "brier", "brier_score"),
                    readBigDecimal(source, "mae"),
                    readBigDecimal(source, "rmse"),
                    readBigDecimal(source, "mape"),
                    readBigDecimal(source, "r2Score", "r2", "r2_score"),
                    parseLocalDateTime(readString(source, "evaluatedAt", "evaluated_at")),
                    trimToNull(readString(source, "note", "remarks", "memo")));
        }

        for (ModelEvaluationAccumulator acc : accumulatorMap.values()) {
            ModelEvaluationSummaryEntity entity = buildEntity(acc);
            modelEvaluationSummaryWriter.insert(entity);
        }
    }

    /**
     * 集計結果から Entity を構築します。
     *
     * @param acc 集計データ
     * @return モデル評価サマリEntity
     */
    private ModelEvaluationSummaryEntity buildEntity(ModelEvaluationAccumulator acc) {

        ModelEvaluationSummaryEntity entity = new ModelEvaluationSummaryEntity();

        entity.setMatchId(null);
        entity.setCountry(acc.country);
        entity.setLeagueId(acc.leagueId);
        entity.setLeagueName(acc.leagueName);
        entity.setTeamId(null);
        entity.setTeamName(null);
        entity.setOpponentTeamId(null);
        entity.setOpponentTeamName(null);

        entity.setModelName(acc.modelName);
        entity.setModelVersion(acc.modelVersion);
        entity.setTaskType(acc.taskType);
        entity.setTargetName(acc.targetName);
        entity.setSeasonRange(acc.seasonRange);
        entity.setValidationMethod(acc.validationMethod);

        entity.setAccuracy(acc.accuracy.average());
        entity.setPrecisionScore(acc.precisionScore.average());
        entity.setRecallScore(acc.recallScore.average());
        entity.setF1Score(acc.f1Score.average());
        entity.setRocAuc(acc.rocAuc.average());
        entity.setPrAuc(acc.prAuc.average());
        entity.setBrierScore(acc.brierScore.average());
        entity.setMae(acc.mae.average());
        entity.setRmse(acc.rmse.average());
        entity.setMape(acc.mape.average());
        entity.setR2Score(acc.r2Score.average());

        entity.setEvaluatedAt(acc.latestEvaluatedAt);
        entity.setNote(buildNote(acc));

        return entity;
    }

    /**
     * 備考を組み立てます。
     *
     * @param acc 集計データ
     * @return 備考
     */
    private String buildNote(ModelEvaluationAccumulator acc) {

        StringBuilder sb = new StringBuilder();

        sb.append("aggregatedModelEvaluation=true");
        sb.append(", sampleCount=").append(acc.sampleCount);

        if (acc.modelName != null) {
            sb.append(", modelName=").append(acc.modelName);
        }
        if (acc.modelVersion != null) {
            sb.append(", modelVersion=").append(acc.modelVersion);
        }
        if (acc.taskType != null) {
            sb.append(", taskType=").append(acc.taskType.name());
        }
        if (acc.targetName != null) {
            sb.append(", targetName=").append(acc.targetName);
        }
        if (acc.validationMethod != null) {
            sb.append(", validationMethod=").append(acc.validationMethod);
        }
        if (acc.noteSamples != null && !acc.noteSamples.isEmpty()) {
            sb.append(", source=").append(String.join(" | ", acc.noteSamples));
        }

        return sb.toString();
    }

    /**
     * 評価行かどうかを判定します。
     *
     * @param source 対象
     * @return 評価系データなら true
     */
    private boolean isEvaluationRow(BookDataEntity source) {

        return trimToNull(readString(source, "modelName", "model_name")) != null
                || trimToNull(readString(source, "targetName", "target_name")) != null
                || readBigDecimal(source, "accuracy", "accuracyScore", "accuracy_score") != null
                || readBigDecimal(source, "mae") != null
                || readBigDecimal(source, "rmse") != null
                || readBigDecimal(source, "r2Score", "r2", "r2_score") != null;
    }

    /**
     * モデルキーを生成します。
     *
     * @param country 国
     * @param leagueId リーグID
     * @param modelName モデル名
     * @param modelVersion モデルVer
     * @param taskType タスク種別
     * @param targetName 目的変数
     * @param seasonRange シーズン範囲
     * @param validationMethod 検証方法
     * @return モデルキー
     */
    private String buildModelKey(
            String country,
            String leagueId,
            String modelName,
            String modelVersion,
            ModelTaskType taskType,
            String targetName,
            String seasonRange,
            String validationMethod) {

        return String.valueOf(country)
                + "|" + String.valueOf(leagueId)
                + "|" + String.valueOf(modelName)
                + "|" + String.valueOf(modelVersion)
                + "|" + String.valueOf(taskType)
                + "|" + String.valueOf(targetName)
                + "|" + String.valueOf(seasonRange)
                + "|" + String.valueOf(validationMethod);
    }

    /**
     * タスク種別を解決します。
     *
     * @param raw 生文字列
     * @return ModelTaskType
     */
    private ModelTaskType parseTaskType(String raw) {

        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toUpperCase();

        if ("CLASSIFICATION".equals(normalized) || "分類".equals(value)) {
            return ModelTaskType.CLASSIFICATION;
        }
        if ("REGRESSION".equals(normalized) || "回帰".equals(value)) {
            return ModelTaskType.REGRESSION;
        }

        try {
            return ModelTaskType.valueOf(normalized);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * リーグIDを解決します。
     *
     * @param leagueKey リーグキー
     * @return リーグID
     */
    private String resolveLeagueId(String leagueKey) {

        if (leagueKey == null) {
            return null;
        }

        int idx = leagueKey.indexOf("_");
        if (idx < 0) {
            return leagueKey;
        }
        return trimToNull(leagueKey.substring(0, idx));
    }

    /**
     * リーグ名を解決します。
     *
     * @param leagueKey リーグキー
     * @return リーグ名
     */
    private String resolveLeagueName(String leagueKey) {

        if (leagueKey == null) {
            return null;
        }

        int idx = leagueKey.indexOf("_");
        if (idx < 0 || idx + 1 >= leagueKey.length()) {
            return leagueKey;
        }
        return trimToNull(leagueKey.substring(idx + 1));
    }

    /**
     * LocalDateTime に変換します。
     *
     * @param value 文字列
     * @return LocalDateTime
     */
    private LocalDateTime parseLocalDateTime(String value) {

        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }

        try {
            return OffsetDateTime.parse(trimmed).toLocalDateTime();
        } catch (Exception e) {
            // no-op
        }

        try {
            return OffsetDateTime.parse(trimmed, RECORD_TIME_FORMATTER_1).toLocalDateTime();
        } catch (Exception e) {
            // no-op
        }

        try {
            return OffsetDateTime.parse(trimmed, RECORD_TIME_FORMATTER_2).toLocalDateTime();
        } catch (Exception e) {
            // no-op
        }

        try {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
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
     * 数値集計用の内部クラスです。
     */
    private static class DecimalAccumulator {

        /** 合計です。 */
        private BigDecimal sum = BigDecimal.ZERO;

        /** 件数です。 */
        private int count;

        /**
         * 値を加算します。
         *
         * @param value 値
         */
        private void add(BigDecimal value) {
            if (value == null) {
                return;
            }
            this.sum = this.sum.add(value);
            this.count++;
        }

        /**
         * 平均を返します。
         *
         * @return 平均。未設定なら null
         */
        private BigDecimal average() {
            if (count == 0) {
                return null;
            }
            return sum.divide(BigDecimal.valueOf(count), SCALE, RoundingMode.HALF_UP);
        }
    }

    /**
     * モデル評価集計用の内部クラスです。
     */
    private static class ModelEvaluationAccumulator {

        /** 国です。 */
        private final String country;

        /** リーグIDです。 */
        private final String leagueId;

        /** リーグ名です。 */
        private final String leagueName;

        /** モデル名です。 */
        private final String modelName;

        /** モデルバージョンです。 */
        private final String modelVersion;

        /** タスク種別です。 */
        private final ModelTaskType taskType;

        /** 目的変数名です。 */
        private final String targetName;

        /** 対象シーズン範囲です。 */
        private final String seasonRange;

        /** 検証方法です。 */
        private final String validationMethod;

        /** Accuracy 集計です。 */
        private final DecimalAccumulator accuracy = new DecimalAccumulator();

        /** Precision 集計です。 */
        private final DecimalAccumulator precisionScore = new DecimalAccumulator();

        /** Recall 集計です。 */
        private final DecimalAccumulator recallScore = new DecimalAccumulator();

        /** F1 集計です。 */
        private final DecimalAccumulator f1Score = new DecimalAccumulator();

        /** ROC-AUC 集計です。 */
        private final DecimalAccumulator rocAuc = new DecimalAccumulator();

        /** PR-AUC 集計です。 */
        private final DecimalAccumulator prAuc = new DecimalAccumulator();

        /** Brier Score 集計です。 */
        private final DecimalAccumulator brierScore = new DecimalAccumulator();

        /** MAE 集計です。 */
        private final DecimalAccumulator mae = new DecimalAccumulator();

        /** RMSE 集計です。 */
        private final DecimalAccumulator rmse = new DecimalAccumulator();

        /** MAPE 集計です。 */
        private final DecimalAccumulator mape = new DecimalAccumulator();

        /** R2 集計です。 */
        private final DecimalAccumulator r2Score = new DecimalAccumulator();

        /** サンプル数です。 */
        private int sampleCount;

        /** 最新評価日時です。 */
        private LocalDateTime latestEvaluatedAt;

        /** 備考サンプルです。 */
        private final List<String> noteSamples = new ArrayList<>();

        /**
         * コンストラクタです。
         *
         * @param country 国
         * @param leagueId リーグID
         * @param leagueName リーグ名
         * @param modelName モデル名
         * @param modelVersion モデルVer
         * @param taskType タスク種別
         * @param targetName 目的変数名
         * @param seasonRange シーズン範囲
         * @param validationMethod 検証方法
         */
        private ModelEvaluationAccumulator(
                String country,
                String leagueId,
                String leagueName,
                String modelName,
                String modelVersion,
                ModelTaskType taskType,
                String targetName,
                String seasonRange,
                String validationMethod) {

            this.country = country;
            this.leagueId = leagueId;
            this.leagueName = leagueName;
            this.modelName = modelName;
            this.modelVersion = modelVersion;
            this.taskType = taskType;
            this.targetName = targetName;
            this.seasonRange = seasonRange;
            this.validationMethod = validationMethod;
        }

        /**
         * 1件分の評価値を反映します。
         *
         * @param accuracy Accuracy
         * @param precisionScore Precision
         * @param recallScore Recall
         * @param f1Score F1
         * @param rocAuc ROC-AUC
         * @param prAuc PR-AUC
         * @param brierScore Brier Score
         * @param mae MAE
         * @param rmse RMSE
         * @param mape MAPE
         * @param r2Score R2
         * @param evaluatedAt 評価日時
         * @param note 備考
         */
        private void accept(
                BigDecimal accuracy,
                BigDecimal precisionScore,
                BigDecimal recallScore,
                BigDecimal f1Score,
                BigDecimal rocAuc,
                BigDecimal prAuc,
                BigDecimal brierScore,
                BigDecimal mae,
                BigDecimal rmse,
                BigDecimal mape,
                BigDecimal r2Score,
                LocalDateTime evaluatedAt,
                String note) {

            this.sampleCount++;

            this.accuracy.add(accuracy);
            this.precisionScore.add(precisionScore);
            this.recallScore.add(recallScore);
            this.f1Score.add(f1Score);
            this.rocAuc.add(rocAuc);
            this.prAuc.add(prAuc);
            this.brierScore.add(brierScore);
            this.mae.add(mae);
            this.rmse.add(rmse);
            this.mape.add(mape);
            this.r2Score.add(r2Score);

            if (evaluatedAt != null) {
                if (this.latestEvaluatedAt == null || evaluatedAt.isAfter(this.latestEvaluatedAt)) {
                    this.latestEvaluatedAt = evaluatedAt;
                }
            }

            if (note != null && !note.isBlank() && this.noteSamples.size() < 5) {
                this.noteSamples.add(note);
            }
        }
    }
}
