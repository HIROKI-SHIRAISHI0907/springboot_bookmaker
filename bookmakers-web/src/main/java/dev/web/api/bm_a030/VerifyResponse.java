package dev.web.api.bm_a030;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyResponse {
    private boolean valid;
    private String accountId;
    private String arn;
    private String userId;
}
