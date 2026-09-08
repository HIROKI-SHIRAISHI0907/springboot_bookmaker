package dev.web.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a030.AwsCostService;
import dev.web.api.bm_a030.AwsCredentialsRequest;
import dev.web.api.bm_a030.CostQueryRequest;
import dev.web.api.bm_a030.CostQueryResponse;
import dev.web.api.bm_a030.CsvExportService;
import dev.web.api.bm_a030.PdfExportService;
import dev.web.api.bm_a030.VerifyResponse;


/**
 * AWS利用料金の照会・ダウンロード用API。
 *
 * すべてのエンドポイントは画面から都度渡されるAWS認証情報(accessKeyId/secretAccessKey)を
 * 使ってAWS Cost Explorer APIを呼び出す。サーバー側でIAM認証情報を保持しないため、
 * 有効な認証情報を持たないユーザーはどのエンドポイントも実行できない
 * (ログイン済みユーザーなら誰でも使える、という状態を避けるための設計)。
 */
@RestController
@RequestMapping("/api/aws-cost")
public class AdminCostController {

    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private final AwsCostService awsCostService;
    private final CsvExportService csvExportService;
    private final PdfExportService pdfExportService;

    public AdminCostController(AwsCostService awsCostService, CsvExportService csvExportService, PdfExportService pdfExportService) {
        this.awsCostService = awsCostService;
        this.csvExportService = csvExportService;
        this.pdfExportService = pdfExportService;
    }

    /** 画面の入り口: 入力されたキーが有効か確認する(GetCallerIdentity) */
    @PostMapping("/verify")
    public ResponseEntity<VerifyResponse> verify(@RequestBody AwsCredentialsRequest request) {
        return ResponseEntity.ok(awsCostService.verify(request));
    }

    /** 画面表示用のコスト照会 */
    @PostMapping("/query")
    public ResponseEntity<CostQueryResponse> query(@RequestBody CostQueryRequest request) {
        return ResponseEntity.ok(awsCostService.getCost(request));
    }

    /** CSVダウンロード */
    @PostMapping(value = "/download/csv")
    public ResponseEntity<byte[]> downloadCsv(@RequestBody CostQueryRequest request) {
        CostQueryResponse data = awsCostService.getCost(request);
        byte[] csv = csvExportService.toCsv(data);
        String filename = buildFileName(request, "csv");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(filename))
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    /** PDFダウンロード */
    @PostMapping(value = "/download/pdf")
    public ResponseEntity<byte[]> downloadPdf(@RequestBody CostQueryRequest request) {
        CostQueryResponse data = awsCostService.getCost(request);
        byte[] pdf = pdfExportService.toPdf(data);
        String filename = buildFileName(request, "pdf");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(filename))
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private String buildFileName(CostQueryRequest request, String extension) {
        String start = safeDate(request.getStartDate());
        String end = safeDate(request.getEndDate());
        return "aws-cost_" + start + "_" + end + "." + extension;
    }

    private String safeDate(String isoDate) {
        try {
            return LocalDate.parse(isoDate).format(FILE_DATE_FMT);
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** 日本語ファイル名でも文字化けしないよう filename* (RFC 5987) 形式で付与する */
    private String contentDisposition(String filename) {
        String encoded = java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded;
    }
}
