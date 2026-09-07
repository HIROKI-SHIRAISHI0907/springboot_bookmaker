package dev.web.api.bm_a028;

/**
 * 承認フロー（依頼/指令）で使う定数群。
 *
 * DBにはコードではなく、この画面がそのまま表示できるように日本語ラベルを
 * 直接登録する方針（AdminApproveEntityのjavadocコメントの方針を踏襲）。
 *
 * @author shiraishitoshio
 */
public final class ApproveFlowConstants {

    private ApproveFlowConstants() {
    }

    // ------------------------------------------------------------
    // instructionOrReview（種別）
    // ------------------------------------------------------------
    /** 管理者 → 担当者（全員）への一斉送信 */
    public static final String TYPE_INSTRUCTION = "指令";
    /** 担当者 → 管理者への申請 */
    public static final String TYPE_REVIEW = "依頼";

    // ------------------------------------------------------------
    // targetKind（targetApprovementInfoの中身の種類）
    // ------------------------------------------------------------
    /** targetApprovementInfo に notice テーブルの id が入る */
    public static final String TARGET_KIND_NOTICE = "NOTICE";
    /** targetApprovementInfo に画面名（自由入力）が入る */
    public static final String TARGET_KIND_SCREEN = "SCREEN";

    // ------------------------------------------------------------
    // flowStatus（依頼）
    // ------------------------------------------------------------
    /** 担当者が申請した直後 */
    public static final String REVIEW_STATUS_REQUESTED = "申請済";
    /** 管理者が承認した */
    public static final String REVIEW_STATUS_APPROVED = "承認";
    /** 管理者が差し戻した */
    public static final String REVIEW_STATUS_REJECTED = "差し戻し";
    /** 申請者（担当者本人）が取り消した */
    public static final String REVIEW_STATUS_CANCELLED = "取り消し";
    /**
     * 保留（申請済のまま申請者〈担当者〉が退会したため、管理者が処理を継続できなくなった状態）。
     * {@code AdminApproveService#handleUserWithdrawal} 参照。
     */
    public static final String REVIEW_STATUS_PENDING = "保留";

    // ------------------------------------------------------------
    // flowStatus（指令）… ヘッダー全体の状態
    // ------------------------------------------------------------
    /** 管理者が発行した直後（まだ全担当者が確認済みではない） */
    public static final String INSTRUCTION_STATUS_UNCONFIRMED = "未確認";
    /** 宛先の担当者全員が確認済みになった */
    public static final String INSTRUCTION_STATUS_CONFIRMED = "確認済";
    /** 管理者が指令自体を差し戻し（撤回・修正）した */
    public static final String INSTRUCTION_STATUS_REJECTED = "差し戻し";
    /** 管理者が指令を取り消した */
    public static final String INSTRUCTION_STATUS_CANCELLED = "取り消し";

    // ------------------------------------------------------------
    // chkFlg（指令の宛先ごとの確認状況：admin_approve_recipient）
    // ------------------------------------------------------------
    public static final String CHK_FLG_UNCONFIRMED = "未確認";
    public static final String CHK_FLG_CONFIRMED = "確認済";
}
