package dev.batch.general;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * ローダー
 * @author shiraishitoshio
 *
 */
public class CsvEntityLoader {

    /**
     * @param csvPath CSVファイル
     * @param entityClass Entityクラス
     * @param headerToField ヘッダー名(日本語など) -> フィールド名(Java) の対応表
     * @param enricher CSVに無い固定値などをセットしたい場合に使う（不要なら null）
     */
    public static <T> List<T> load(
            Path csvPath,
            Class<T> entityClass,
            Map<String, String> headerToField,
            BiConsumer<T, RowContext> enricher
    ) throws IOException {

        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {

            String headerLine = reader.readLine();
            if (headerLine == null) return List.of();

            String[] headers = parseCsvLine(headerLine);
            Map<Integer, Field> columnFieldMap = buildColumnFieldMap(headers, entityClass, headerToField);

            List<T> result = new ArrayList<>();
            String line;
            long rowNo = 1; // header = 1行目

            while ((line = reader.readLine()) != null) {
                rowNo++;
                if (line.isBlank()) continue;

                String[] values = parseCsvLine(line);
                T entity = entityClass.getDeclaredConstructor().newInstance();

                int max = Math.min(values.length, headers.length);
                for (int i = 0; i < max; i++) {
                    Field field = columnFieldMap.get(i);
                    if (field == null) continue;

                    field.setAccessible(true);
                    Object converted = convert(values[i], field.getType());
                    field.set(entity, converted);
                }

                if (enricher != null) {
                    enricher.accept(entity, new RowContext(csvPath, rowNo));
                }

                result.add(entity);
            }

            return result;

        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to create entity: " + entityClass.getSimpleName(), e);
        }
    }

    public static <T> List<T> load(Path csvPath, Class<T> entityClass, Map<String, String> headerToField) throws IOException {
        return load(csvPath, entityClass, headerToField, null);
    }

    // -----------------------
    // マッピング（ヘッダー -> フィールド）
    // -----------------------
    private static <T> Map<Integer, Field> buildColumnFieldMap(
            String[] headers,
            Class<T> entityClass,
            Map<String, String> headerToField
    ) {
        Map<String, Field> allFields = getAllFields(entityClass);

        Map<Integer, Field> result = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String header = normalize(headers[i]);

            // ① 日本語ヘッダー対応表でフィールド名へ
            String mappedFieldName = headerToField.getOrDefault(header, null);
            if (mappedFieldName == null) {
                // ② 直接一致（英語ヘッダーが来た場合など）
                mappedFieldName = header;
            }

            Field f = allFields.get(normalize(mappedFieldName));
            if (f != null) {
                result.put(i, f);
            }
        }
        return result;
    }

    private static Map<String, Field> getAllFields(Class<?> clazz) {
        Map<String, Field> map = new HashMap<>();
        Class<?> cur = clazz;
        while (cur != null && cur != Object.class) {
            for (Field f : cur.getDeclaredFields()) {
                map.put(normalize(f.getName()), f);
            }
            cur = cur.getSuperclass();
        }
        return map;
    }

    private static String normalize(String s) {
        return (s == null ? "" : s)
                .trim()
                .toLowerCase()
                .replace("　", "")      // 全角スペース
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "");
    }

    // -----------------------
    // CSVパース（ダブルクォート簡易対応）
    // -----------------------
    private static String[] parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                // "" は " として扱う
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (c == ',' && !inQuotes) {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        out.add(sb.toString());
        return out.toArray(new String[0]);
    }

    // -----------------------
    // 型変換（必要最低限）
    // -----------------------
    private static Object convert(String raw, Class<?> targetType) {
        if (raw == null) return null;

        String v = raw.trim();
        if (v.isEmpty()) return null;

        if (targetType == String.class) return v;
        if (targetType == Integer.class || targetType == int.class) return Integer.parseInt(v);
        if (targetType == Long.class || targetType == long.class) return Long.parseLong(v);
        if (targetType == Boolean.class || targetType == boolean.class) return Boolean.parseBoolean(v);

        // 必要なら（MetaEntityに日付がある場合など）
        if (targetType == LocalDateTime.class) return LocalDateTime.parse(v);
        if (targetType == OffsetDateTime.class) return OffsetDateTime.parse(v);

        throw new IllegalArgumentException("Unsupported field type: " + targetType.getName());
    }

    // CSVに無い値を注入したいとき用
    public static class RowContext {
        private final Path csvPath;
        private final long rowNo;

        public RowContext(Path csvPath, long rowNo) {
            this.csvPath = csvPath;
            this.rowNo = rowNo;
        }

        public Path getCsvPath() { return csvPath; }
        public long getRowNo() { return rowNo; }
    }
}
