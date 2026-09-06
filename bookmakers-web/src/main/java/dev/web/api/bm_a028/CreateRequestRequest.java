package dev.web.api.bm_a028;

import lombok.Data;

/**
 * 依頼作成リクエスト（担当者 → 管理者）。
 * fromUserId はクライアント入力ではなく、認証情報（JWT）から解決したuserIdを使用する。
 */
@Data
public class CreateRequestRequest {

    /** ApproveFlowConstants.TARGET_KIND_NOTICE または TARGET_KIND_SCREEN */
    private String targetKind;

    /** targetKindがNOTICEならnoticeのid、SCREENなら画面名 */
    private String targetApprovementInfo;
}
