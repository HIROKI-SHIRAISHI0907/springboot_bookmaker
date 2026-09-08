package dev.web.api.bm_a030;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** サービス(EC2, S3, RDS...)ごとの金額 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceCostItem {
    private String serviceName;
    private BigDecimal amount;
    private String unit;
}
