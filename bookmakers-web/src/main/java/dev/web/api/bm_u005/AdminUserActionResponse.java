package dev.web.api.bm_u005;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserActionResponse {

	/** レスポンスコード */
    private String responseCode;

    /** メッセージ */
    private String message;

}
