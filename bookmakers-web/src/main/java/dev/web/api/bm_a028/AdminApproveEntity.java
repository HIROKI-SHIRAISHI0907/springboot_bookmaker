package dev.web.api.bm_a028;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 承認フローEntity（担当者が管理者にレビュー依頼を行う、もしくは管理者が担当者に指令を出すことに使用する）。
 *
 * admin_approve テーブルの1行に対応するヘッダー情報。
 * 「指令」は担当者全員への一斉送信になるため、宛先ごとの確認状況は
 * 本Entityではなく {@link AdminApproveRecipientEntity}（admin_approve_recipientテーブル）で管理する。
 *
 * @author shiraishitoshio
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminApproveEntity {

    /** 承認ID */
    private String approveId;

    /** 指令or依頼（管理者から担当者なら「指令」逆なら「依頼」で登録） */
    private String instructionOrReview;

    /** 起票者のuser_id（依頼なら担当者本人、指令なら管理者）。usersテーブルのuser_id（BIGINT）に合わせLong。 */
    private Long fromUserId;

    /**
     * 対象承認情報の種別。
     * "NOTICE": targetApprovementInfo に notice テーブルの id（文字列）が入る。
     * "SCREEN": targetApprovementInfo に画面名（自由入力）が入る。
     */
    private String targetKind;

    /** 対象承認情報（お知らせに関するものはnoticeのid、それ以外は画面名） */
    private String targetApprovementInfo;

    /**
     * ステータス。
     * 依頼: 申請済 / 承認 / 差し戻し / 取り消し / 保留（申請者〈担当者〉が退会し申請済のまま宙に浮いた状態）
     * 指令: 未確認 / 確認済 / 差し戻し / 取り消し（宛先全員確認済みで自動的に「確認済」へ。
     *       確認済みの宛先〈担当者〉が退会すると、その宛先の確認は取り消され、
     *       ヘッダーが「確認済」だった場合は「未確認」に差し戻される）
     */
    private String flowStatus;

    /** 差し戻しおよび取り消し時のコメント */
    private String comment;

    /** 登録日時 */
    private OffsetDateTime registerTime;

    /** 更新日時 */
    private OffsetDateTime updateTime;
}
