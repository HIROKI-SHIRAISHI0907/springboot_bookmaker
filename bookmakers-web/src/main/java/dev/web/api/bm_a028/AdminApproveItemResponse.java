package dev.web.api.bm_a028;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * 依頼/指令 一覧表示用の1件分レスポンス。
 *
 * 依頼一覧（管理者向け）・指令一覧（管理者向け／担当者向け）のいずれでも
 * このDTOを使い回す。用途によって使わない項目はnullのままにする。
 */
@Data
@Builder
public class AdminApproveItemResponse {

    private String approveId;

    /** "指令" or "依頼" */
    private String instructionOrReview;

    private Long fromUserId;

    private String fromUserName;

    private String toEmail;

    /** "NOTICE" or "SCREEN" */
    private String targetKind;

    private String targetApprovementInfo;

    /** 依頼: 申請済/承認/差し戻し/取り消し/保留(申請者〈担当者〉退会時)　指令: 未確認/確認済/差し戻し/取り消し */
    private String flowStatus;

    private String comment;

    private String registerTime;

    private String updateTime;

    // ---- 指令（管理者向け一覧）でのみ使用：宛先の確認進捗 ----
    /** 宛先の担当者数（指令のみ） */
    private Integer totalRecipientCount;

    /** 確認済みの担当者数（指令のみ） */
    private Integer confirmedRecipientCount;

    /** 宛先ごとの確認状況一覧（指令・管理者向け一覧のみ） */
    private List<AdminApproveRecipientItemResponse> recipients;

    // ---- 指令（担当者向け一覧）でのみ使用 ----
    /** ログイン中の担当者自身が確認済みかどうか（指令・担当者向け一覧のみ） */
    private Boolean confirmedByMe;
}
