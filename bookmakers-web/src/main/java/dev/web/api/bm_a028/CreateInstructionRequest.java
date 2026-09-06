package dev.web.api.bm_a028;

import lombok.Data;

/**
 * 指令作成リクエスト（管理者 → 担当者全員への一斉送信）。
 * fromUserId はクライアント入力ではなく、認証情報（JWT）から解決したuserIdを使用する。
 * 宛先（担当者一覧）はクライアントからは受け取らず、サーバー側で
 * authFlg=2 の全ユーザーを都度取得して一斉送信する。
 */
@Data
public class CreateInstructionRequest {

    /** ApproveFlowConstants.TARGET_KIND_NOTICE または TARGET_KIND_SCREEN */
    private String targetKind;

    /** targetKindがNOTICEならnoticeのid、SCREENなら画面名 */
    private String targetApprovementInfo;
}
