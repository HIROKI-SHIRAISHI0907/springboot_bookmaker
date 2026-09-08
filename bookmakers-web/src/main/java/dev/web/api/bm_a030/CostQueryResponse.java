package dev.web.api.bm_a030;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CostQueryResponse {

    /** 検索条件のエコーバック */
    private String startDate;
    private String endDate;
    private String granularity;

    /** 通貨単位(例: USD) */
    private String currency;

    /** 期間全体の合計金額 */
    private BigDecimal totalAmount;

    /** サービス別の合計(期間全体を集計、金額の降順) */
    private List<ServiceCostItem> servicesSummary;

    /** 粒度(DAILY/MONTHLY)ごとの内訳 */
    private List<PeriodCost> timeline;
}
