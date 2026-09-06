package dev.web.api.bm_a028;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminApproveListResponse {

    private String responseCode;

    private String message;

    private List<AdminApproveItemResponse> items;
}
