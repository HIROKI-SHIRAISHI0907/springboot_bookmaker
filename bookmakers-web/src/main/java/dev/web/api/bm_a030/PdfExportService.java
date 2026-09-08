package dev.web.api.bm_a030;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import dev.web.exception.AwsCostException;

/**
 * コストデータをPDFに変換する。
 *
 * 【重要】日本語フォントについて
 * PDFBoxが標準搭載しているフォント(Helvetica等)には日本語グリフが無いため、
 * このクラスは classpath: fonts/ipaexg.ttf を読み込んで埋め込む。
 *
 * 事前準備として、以下のいずれかの日本語TrueTypeフォントを
 * `src/main/resources/fonts/ipaexg.ttf` として配置すること。
 *   - IPAexゴシック (IPAフォントライセンスで再配布可): https://moji.or.jp/ipafont/
 *   - Noto Sans JP など、配布ライセンス上問題のないフォント
 * (ライセンスの都合上、本ソース一式にはフォントファイル本体を同梱していない)
 */
@Service
public class PdfExportService {

    private static final String FONT_RESOURCE_PATH = "fonts/ipaexg.ttf";
    private static final float MARGIN = 40f;
    private static final float LINE_HEIGHT = 16f;
    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;

    public byte[] toPdf(CostQueryResponse data) {
        try (PDDocument document = new PDDocument()) {
            PDFont font = loadJapaneseFont(document);

            PageContext ctx = new PageContext(document, font);

            ctx.writeTitle("AWS 利用料金レポート");
            ctx.writeLine("対象期間: " + data.getStartDate() + " 〜 " + data.getEndDate() + "  (粒度: " + nullToEmpty(data.getGranularity()) + ")");
            ctx.writeLine("通貨: " + nullToEmpty(data.getCurrency()));
            ctx.writeLine("合計金額: " + format(data.getTotalAmount()) + " " + nullToEmpty(data.getCurrency()));
            ctx.writeBlank();

            ctx.writeSubTitle("サービス別内訳");
            ctx.writeTableHeader(new String[] { "サービス名", "金額" }, new float[] { 350f, 120f });
            if (data.getServicesSummary() != null) {
                for (ServiceCostItem item : data.getServicesSummary()) {
                    ctx.writeTableRow(new String[] { item.getServiceName(), format(item.getAmount()) }, new float[] { 350f, 120f });
                }
            }

            ctx.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AwsCostException("PDFの生成に失敗しました。フォントファイル(" + FONT_RESOURCE_PATH + ")が配置されているか確認してください。", e, 500);
        }
    }

    private PDFont loadJapaneseFont(PDDocument document) throws IOException {
        ClassPathResource resource = new ClassPathResource(FONT_RESOURCE_PATH);
        if (!resource.exists()) {
            throw new AwsCostException(
                    "PDF生成用の日本語フォントが見つかりません。src/main/resources/" + FONT_RESOURCE_PATH + " にフォントファイルを配置してください。",
                    500);
        }
        try (InputStream is = resource.getInputStream()) {
            return PDType0Font.load(document, is);
        }
    }

    private String format(BigDecimal amount) {
        return amount == null ? "-" : amount.toPlainString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * ページ跨ぎと文字書き込みをまとめて面倒みる小さなヘルパー。
     * 縦位置が下端に近づいたら自動的に改ページする。
     */
    private static final class PageContext {
        private final PDDocument document;
        private final PDFont font;
        private PDPage currentPage;
        private PDPageContentStream stream;
        private float y;

        PageContext(PDDocument document, PDFont font) throws IOException {
            this.document = document;
            this.font = font;
            newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            currentPage = new PDPage(PAGE_SIZE);
            document.addPage(currentPage);
            stream = new PDPageContentStream(document, currentPage);
            y = PAGE_SIZE.getHeight() - MARGIN;
        }

        private void ensureSpace() throws IOException {
            if (y < MARGIN + LINE_HEIGHT) {
                newPage();
            }
        }

        void writeTitle(String text) throws IOException {
            ensureSpace();
            drawText(text, 18f, true);
            y -= LINE_HEIGHT * 1.5f;
        }

        void writeSubTitle(String text) throws IOException {
            ensureSpace();
            drawText(text, 13f, true);
            y -= LINE_HEIGHT * 0.3f;
        }

        void writeLine(String text) throws IOException {
            ensureSpace();
            drawText(text, 11f, false);
        }

        void writeBlank() {
            y -= LINE_HEIGHT * 0.5f;
        }

        void writeTableHeader(String[] cols, float[] widths) throws IOException {
            ensureSpace();
            drawRow(cols, widths, true);
            y -= 4f;
        }

        void writeTableRow(String[] cols, float[] widths) throws IOException {
            ensureSpace();
            drawRow(cols, widths, false);
        }

        private void drawRow(String[] cols, float[] widths, boolean bold) throws IOException {
            float x = MARGIN;
            for (int i = 0; i < cols.length; i++) {
                stream.beginText();
                stream.setFont(font, bold ? 11f : 10.5f);
                stream.newLineAtOffset(x, y);
                stream.showText(nullToEmpty(cols[i]));
                stream.endText();
                x += widths[i];
            }
            y -= LINE_HEIGHT;
        }

        private void drawText(String text, float fontSize, boolean bold) throws IOException {
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(text);
            stream.endText();
            y -= LINE_HEIGHT;
        }

        void close() throws IOException {
            if (stream != null) {
                stream.close();
            }
        }
    }
}
