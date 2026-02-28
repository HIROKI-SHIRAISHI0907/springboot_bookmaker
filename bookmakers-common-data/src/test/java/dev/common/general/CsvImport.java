package dev.common.general;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Csvインポート汎用
 * @author shiraishitoshio
 *
 */
public class CsvImport {

    /**
     * CSV → Entity 一括変換（汎用）
     */
    public static <T> List<T> importCsv(
            String csvRelativePath,
            Class<T> entityClass,
            Map<String, String> headerMap,
            BiConsumer<T, CsvEntityLoader.RowContext> enricher
    ) throws Exception {

        Path path = Path.of(csvRelativePath);

        if (!path.toFile().exists()) {
            throw new IllegalArgumentException("CSV not found: " + path.toAbsolutePath());
        }

        List<T> list = CsvEntityLoader.load(
                path,
                entityClass,
                headerMap,
                enricher
        );

        System.out.println("[CSV] loaded: " + path + " rows=" + list.size());
        return list;
    }

    /**
     * enricher 不要版
     */
    public static <T> List<T> importCsv(
            String csvRelativePath,
            Class<T> entityClass,
            Map<String, String> headerMap
    ) throws Exception {
        return importCsv(csvRelativePath, entityClass, headerMap, null);
    }
}
