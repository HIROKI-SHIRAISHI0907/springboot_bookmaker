package dev.web.api.bm_a028;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 「指令」の宛先（担当者）ごとの確認状況Entity。
 *
 * admin_approve_recipient テーブルの1行に対応する。
 * 指令は担当者全員に一斉送信されるため、誰が確認したかをここで管理する。
 * 「依頼」は宛先が管理者1名固定（既存仕様上、管理者は常に最大1人）のため、
 * このEntityの行は作らず {@link AdminApproveEntity#getFlowStatus()} のみで管理する。
 *
 * @author shiraishitoshio
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminApproveRecipientEntity {

    /** 宛先ID */
    private Long recipientId;

    /** admin_approve.approveId */
    private String approveId;

    /** 宛先の担当者user_id。usersテーブルのuser_id（BIGINT）に合わせLong。 */
    private Long userId;

    /** 未確認 / 確認済 */
    private String chkFlg;

    /** 確認した日時（未確認の場合はnull） */
    private OffsetDateTime confirmedTime;

    /** 登録日時 */
    private OffsetDateTime registerTime;

    /** 更新日時 */
    private OffsetDateTime updateTime;
}
