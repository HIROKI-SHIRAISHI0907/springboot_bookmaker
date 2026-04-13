package dev.web.api.bm_a014;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

public class CsvExport {

    private CsvExport() {
    }

    public static <T> void exportCsv(
            String outputPath,
            List<T> list,
            Map<String, String> headerMap) throws IOException {
        exportCsv(outputPath, list, headerMap, 1L);
    }

    public static <T> void exportCsv(
            String outputPath,
            List<T> list,
            Map<String, String> headerMap,
            long startId) throws IOException {

        Path path = Paths.get(outputPath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        List<T> safeList = (list == null) ? Collections.emptyList() : list;

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {

            writer.write(toCsvLine(new ArrayList<>(headerMap.keySet())));
            writer.newLine();

            for (int rowIndex = 0; rowIndex < safeList.size(); rowIndex++) {
                T row = safeList.get(rowIndex);
                BeanWrapper beanWrapper = new BeanWrapperImpl(row);
                List<String> values = new ArrayList<>();

                long currentId = startId + rowIndex;

                for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                    String csvHeader = entry.getKey();
                    String propertyName = entry.getValue();

                    Object value;
                    try {
                        if ("id".equals(csvHeader)) {
                            value = currentId;
                        } else {
                            value = beanWrapper.getPropertyValue(propertyName);
                        }
                    } catch (Exception e) {
                        value = null;
                    }

                    values.add(normalizeValue(csvHeader, value));
                }

                writer.write(toCsvLine(values));
                writer.newLine();
            }
        }
    }

    private static String normalizeValue(String csvHeader, Object value) {
        if (value != null) {
            return String.valueOf(value);
        }

        switch (csvHeader) {
            case "del_flg":
            case "logic_flg":
            case "disp_flg":
                return "0";
            default:
                return "";
        }
    }

    private static String toCsvLine(List<String> values) {
        List<String> escaped = new ArrayList<>();
        for (String value : values) {
            escaped.add(escape(value));
        }
        return String.join(",", escaped);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }

        boolean needQuote = value.contains(",")
                || value.contains("\"")
                || value.contains("\r")
                || value.contains("\n");

        String escaped = value.replace("\"", "\"\"");

        return needQuote ? "\"" + escaped + "\"" : escaped;
    }
}
