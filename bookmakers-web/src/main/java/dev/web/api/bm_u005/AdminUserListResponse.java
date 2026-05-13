package dev.web.api.bm_u005;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserListResponse {

	/** レスポンスコード */
    private String responseCode;

    /** メッセージ */
    private String message;

    /** ユーザー情報 */
    private List<AdminUserItemResponse> users;

}
