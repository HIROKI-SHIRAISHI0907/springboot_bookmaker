package dev.web.api.bm_a030;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 期間(日 or 月)ごとの金額とサービス別内訳 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeriodCost {
    private String periodStart;
    private String periodEnd;
    private BigDecimal amount;
    private List<ServiceCostItem> services;
}
