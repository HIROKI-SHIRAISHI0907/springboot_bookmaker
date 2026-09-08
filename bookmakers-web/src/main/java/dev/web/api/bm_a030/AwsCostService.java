package dev.web.api.bm_a030;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import dev.web.exception.AwsCostException;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.Dimension;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageResponse;
import software.amazon.awssdk.services.costexplorer.model.Granularity;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinition;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinitionType;
import software.amazon.awssdk.services.costexplorer.model.ResultByTime;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

/**
 * AWS Cost Explorer から料金情報を取得するサービス。
 *
 * 注意:
 * - Cost Explorer API は呼び出し課金($0.01/リクエスト、2024年時点)があるため、
 *   画面側で無闇に連打・自動更新しない運用にすること。
 * - GetCostAndUsage の TimePeriod.End は「その日を含まない」仕様のため、
 *   画面上「終了日を含む」挙動にするには +1日して呼び出す必要がある。
 */
@Service
public class AwsCostService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String METRIC = "UnblendedCost";

    private final AwsClientFactory clientFactory;

    public AwsCostService(AwsClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    /** 画面の入り口で「入力されたキーが有効か」を確認するための疎通チェック */
    public VerifyResponse verify(AwsCredentialsRequest req) {
        AwsClientFactory.requireCredentials(req);
        try (StsClient sts = clientFactory.createStsClient(req.getAccessKeyId(), req.getSecretAccessKey(), req.getSessionToken())) {
            GetCallerIdentityResponse identity = sts.getCallerIdentity();
            return new VerifyResponse(true, identity.account(), identity.arn(), identity.userId());
        }
    }

    public CostQueryResponse getCost(CostQueryRequest req) {
        validate(req);

        LocalDate start = LocalDate.parse(req.getStartDate(), DATE_FMT);
        // AWS側のEndは「含まない」仕様なので、画面上「終了日を含む」ようにするため+1日する
        LocalDate endExclusive = LocalDate.parse(req.getEndDate(), DATE_FMT).plusDays(1);

        Granularity granularity = "DAILY".equalsIgnoreCase(req.getGranularity()) ? Granularity.DAILY : Granularity.MONTHLY;

        GetCostAndUsageRequest.Builder builder = GetCostAndUsageRequest.builder()
                .timePeriod(DateInterval.builder()
                        .start(start.format(DATE_FMT))
                        .end(endExclusive.format(DATE_FMT))
                        .build())
                .granularity(granularity)
                .metrics(METRIC);

        if (req.isGroupByService()) {
            builder.groupBy(GroupDefinition.builder()
                    .type(GroupDefinitionType.DIMENSION)
                    .key(Dimension.SERVICE.toString())
                    .build());
        }

        try (CostExplorerClient client = clientFactory.createCostExplorerClient(
                req.getAccessKeyId(), req.getSecretAccessKey(), req.getSessionToken())) {

            List<ResultByTime> allResults = new ArrayList<>();
            String nextPageToken = null;
            do {
                GetCostAndUsageRequest pageRequest = builder.nextPageToken(nextPageToken).build();
                GetCostAndUsageResponse response = client.getCostAndUsage(pageRequest);
                allResults.addAll(response.resultsByTime());
                nextPageToken = response.nextPageToken();
            } while (nextPageToken != null && !nextPageToken.isBlank());

            return toResponse(req, allResults);
        }
    }

    private CostQueryResponse toResponse(CostQueryRequest req, List<ResultByTime> results) {
        List<PeriodCost> timeline = new ArrayList<>();
        Map<String, BigDecimal> serviceTotals = new LinkedHashMap<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        String currency = "USD";

        for (ResultByTime result : results) {
            List<ServiceCostItem> periodServices = new ArrayList<>();
            BigDecimal periodTotal = BigDecimal.ZERO;

            if (result.groups() != null && !result.groups().isEmpty()) {
                // dev.web.api.bm_a030 パッケージ内(または他のimport)に別の Group クラスが
                // 既に存在しているようなので、衝突を避けるため完全修飾名で書く
                for (software.amazon.awssdk.services.costexplorer.model.Group group : result.groups()) {
                    String serviceName = group.keys() != null && !group.keys().isEmpty() ? group.keys().get(0) : "unknown";
                    var metric = group.metrics().get(METRIC);
                    if (metric == null) {
                        continue;
                    }
                    BigDecimal amount = new BigDecimal(metric.amount());
                    currency = metric.unit() != null ? metric.unit() : currency;

                    periodServices.add(new ServiceCostItem(serviceName, amount, currency));
                    periodTotal = periodTotal.add(amount);
                    serviceTotals.merge(serviceName, amount, BigDecimal::add);
                }
                periodServices.sort(Comparator.comparing(ServiceCostItem::getAmount).reversed());
            } else if (result.total() != null && result.total().get(METRIC) != null) {
                var metric = result.total().get(METRIC);
                BigDecimal amount = new BigDecimal(metric.amount());
                currency = metric.unit() != null ? metric.unit() : currency;
                periodTotal = amount;
            }

            grandTotal = grandTotal.add(periodTotal);

            timeline.add(new PeriodCost(
                    result.timePeriod().start(),
                    result.timePeriod().end(),
                    periodTotal,
                    periodServices));
        }

        List<ServiceCostItem> summary = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : serviceTotals.entrySet()) {
            summary.add(new ServiceCostItem(entry.getKey(), entry.getValue(), currency));
        }
        summary.sort(Comparator.comparing(ServiceCostItem::getAmount).reversed());

        CostQueryResponse response = new CostQueryResponse();
        response.setStartDate(req.getStartDate());
        response.setEndDate(req.getEndDate());
        response.setGranularity(req.getGranularity());
        response.setCurrency(currency);
        response.setTotalAmount(grandTotal);
        response.setServicesSummary(summary);
        response.setTimeline(timeline);
        return response;
    }

    private void validate(CostQueryRequest req) {
        AwsClientFactory.requireCredentials(new AwsCredentialsRequest(req.getAccessKeyId(), req.getSecretAccessKey(), req.getSessionToken()));

        if (req.getStartDate() == null || req.getEndDate() == null) {
            throw new AwsCostException("開始日と終了日を指定してください。");
        }
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(req.getStartDate(), DATE_FMT);
            end = LocalDate.parse(req.getEndDate(), DATE_FMT);
        } catch (Exception e) {
            throw new AwsCostException("日付の形式が不正です(yyyy-MM-dd で指定してください)。");
        }
        if (end.isBefore(start)) {
            throw new AwsCostException("終了日は開始日以降を指定してください。");
        }
        if (start.plusYears(1).isBefore(end)) {
            throw new AwsCostException("期間は最大1年以内で指定してください。");
        }
    }
}
