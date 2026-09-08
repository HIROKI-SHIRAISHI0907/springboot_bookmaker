package dev.web.api.bm_a030;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;

import dev.web.exception.AwsCostException;

/**
 * コストデータをCSV(UTF-8 BOM付き)に変換する。
 * BOMを付けることで、Excelでそのまま開いても日本語(サービス名の一部等)が文字化けしない。
 */
@Service
public class CsvExportService {

    private static final byte[] UTF8_BOM = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    public byte[] toCsv(CostQueryResponse data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(UTF8_BOM);
            try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                writer.write("期間," + data.getStartDate() + "," + data.getEndDate() + "\n");
                writer.write("通貨," + nullToEmpty(data.getCurrency()) + "\n");
                writer.write("合計金額," + format(data.getTotalAmount()) + "\n");
                writer.write("\n");

                writer.write("[サービス別内訳]\n");
                writer.write("サービス名,金額,通貨\n");
                if (data.getServicesSummary() != null) {
                    for (ServiceCostItem item : data.getServicesSummary()) {
                        writer.write(escape(item.getServiceName()) + "," + format(item.getAmount()) + "," + nullToEmpty(item.getUnit()) + "\n");
                    }
                }
                writer.write("\n");

                writer.write("[期間別内訳]\n");
                writer.write("開始日,終了日(翌日を含まない),金額,サービス名,サービス別金額\n");
                if (data.getTimeline() != null) {
                    for (PeriodCost period : data.getTimeline()) {
                        if (period.getServices() == null || period.getServices().isEmpty()) {
                            writer.write(period.getPeriodStart() + "," + period.getPeriodEnd() + "," + format(period.getAmount()) + ",,\n");
                        } else {
                            for (ServiceCostItem svc : period.getServices()) {
                                writer.write(period.getPeriodStart() + "," + period.getPeriodEnd() + "," + format(period.getAmount())
                                        + "," + escape(svc.getServiceName()) + "," + format(svc.getAmount()) + "\n");
                            }
                        }
                    }
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new AwsCostException("CSVの生成に失敗しました。", e, 500);
        }
    }

    private String format(java.math.BigDecimal amount) {
        return amount == null ? "" : amount.toPlainString();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String escape(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
