package dev.web.api.bm_a029;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccountActionResponse {

    private String responseCode;

    private String message;
}
