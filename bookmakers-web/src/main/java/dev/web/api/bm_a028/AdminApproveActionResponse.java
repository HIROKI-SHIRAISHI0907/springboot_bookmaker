package dev.web.api.bm_a028;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminApproveActionResponse {

    private String responseCode;

    private String message;

    /** 作成系アクション（依頼作成・指令作成）の場合、発行されたapproveIdを返す */
    private String approveId;

    /** キーID（メールID, 通知IDなど） */
    private String keyId;

    /** メールアドレス */
    private String toMailAddress;

    /** 複数メールアドレス */
    private List<String> toMailAddressList;
}
