package dev.web.repository.user;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_a028.AdminApproveEntity;
import dev.web.api.bm_a028.AdminApproveRecipientEntity;
import dev.web.api.bm_a028.ApproveFlowConstants;
import lombok.RequiredArgsConstructor;

/**
 * 承認フロー（依頼/指令）用リポジトリ。
 *
 * {@link NoticeRepository} と同様、NamedParameterJdbcTemplateで直接SQLを発行する方式。
 *
 * 依頼   : instruction_or_review = '依頼'（担当者 → 管理者）
 * 指令   : instruction_or_review = '指令'（管理者 → 担当者全員。宛先はadmin_approve_recipientで管理）
 *
 * @author shiraishitoshio
 */
@Repository
@RequiredArgsConstructor
public class ApproveFlowRepository {

    private final @Qualifier("webUserJdbcTemplate") NamedParameterJdbcTemplate jdbc;

    private static final String APPROVE_COLUMNS = """
              approve_id, instruction_or_review, from_user_id, target_kind,
              target_approvement_info, flow_status, comment, register_time, update_time
            """;

    private static final RowMapper<AdminApproveEntity> APPROVE_ROW_MAPPER = (rs, rowNum) -> AdminApproveEntity.builder()
            .approveId(rs.getString("approve_id"))
            .instructionOrReview(rs.getString("instruction_or_review"))
            .fromUserId(rs.getLong("from_user_id"))
            .targetKind(rs.getString("target_kind"))
            .targetApprovementInfo(rs.getString("target_approvement_info"))
            .flowStatus(rs.getString("flow_status"))
            .comment(rs.getString("comment"))
            .registerTime(rs.getObject("register_time", OffsetDateTime.class))
            .updateTime(rs.getObject("update_time", OffsetDateTime.class))
            .build();

    private static final RowMapper<AdminApproveRecipientEntity> RECIPIENT_ROW_MAPPER = (rs, rowNum) -> AdminApproveRecipientEntity.builder()
            .recipientId(rs.getLong("recipient_id"))
            .approveId(rs.getString("approve_id"))
            .userId(rs.getLong("user_id"))
            .chkFlg(rs.getString("chk_flg"))
            .confirmedTime(rs.getObject("confirmed_time", OffsetDateTime.class))
            .registerTime(rs.getObject("register_time", OffsetDateTime.class))
            .updateTime(rs.getObject("update_time", OffsetDateTime.class))
            .build();

    // ------------------------------------------------------------
    // 依頼（担当者 → 管理者）
    // ------------------------------------------------------------
    public List<AdminApproveEntity> findRequests() {
        String sql = """
                SELECT %s
                FROM approve_flow
                WHERE instruction_or_review = :type
                ORDER BY register_time DESC
                """.formatted(APPROVE_COLUMNS);
        return jdbc.query(sql, Map.of("type", ApproveFlowConstants.TYPE_REVIEW), APPROVE_ROW_MAPPER);
    }

    public List<AdminApproveEntity> findRequestsBySubmitter(Long fromUserId) {
        String sql = """
                SELECT %s
                FROM approve_flow
                WHERE instruction_or_review = :type
                  AND from_user_id = :fromUserId
                ORDER BY register_time DESC
                """.formatted(APPROVE_COLUMNS);
        Map<String, Object> params = Map.of(
                "type", ApproveFlowConstants.TYPE_REVIEW,
                "fromUserId", fromUserId);
        return jdbc.query(sql, params, APPROVE_ROW_MAPPER);
    }

    // ------------------------------------------------------------
    // 指令（管理者 → 担当者全員）
    // ------------------------------------------------------------

    public List<AdminApproveEntity> findInstructions() {
        String sql = """
                SELECT %s
                FROM approve_flow
                WHERE instruction_or_review = :type
                ORDER BY register_time DESC
                """.formatted(APPROVE_COLUMNS);
        return jdbc.query(sql, Map.of("type", ApproveFlowConstants.TYPE_INSTRUCTION), APPROVE_ROW_MAPPER);
    }

    public List<AdminApproveEntity> findInstructionsForRecipient(Long userId) {
        String sql = """
                SELECT a.approve_id, a.instruction_or_review, a.from_user_id, a.target_kind,
                       a.target_approvement_info, a.flow_status, a.comment, a.register_time, a.update_time
                FROM approve_flow a
                JOIN approve_flow_recipient r ON r.approve_id = a.approve_id
                WHERE a.instruction_or_review = :type
                  AND r.user_id = :userId
                ORDER BY a.register_time DESC
                """;
        Map<String, Object> params = Map.of(
                "type", ApproveFlowConstants.TYPE_INSTRUCTION,
                "userId", userId);
        return jdbc.query(sql, params, APPROVE_ROW_MAPPER);
    }

    // ------------------------------------------------------------
    // ヘッダー（approve_flow）共通
    // ------------------------------------------------------------
    public AdminApproveEntity findById(String approveId) {
        String sql = """
                SELECT %s
                FROM approve_flow
                WHERE approve_id = :approveId
                """.formatted(APPROVE_COLUMNS);
        return queryForOne(sql, Map.of("approveId", approveId), APPROVE_ROW_MAPPER);
    }

    /** SELECT ... FOR UPDATE。承認/差し戻し/確認などの状態遷移中に同一approveIdへの競合更新を防ぐ。 */
    public AdminApproveEntity findByIdForUpdate(String approveId) {
        String sql = """
                SELECT %s
                FROM approve_flow
                WHERE approve_id = :approveId
                FOR UPDATE
                """.formatted(APPROVE_COLUMNS);
        return queryForOne(sql, Map.of("approveId", approveId), APPROVE_ROW_MAPPER);
    }

    public int insert(AdminApproveEntity entity) {
        String sql = """
                INSERT INTO approve_flow (
                  approve_id, instruction_or_review, from_user_id, target_kind,
                  target_approvement_info, flow_status, comment, register_id,
                  register_time, update_id, update_time
                )
                VALUES (
                  :approveId, :instructionOrReview, :fromUserId, :targetKind,
                  :targetApprovementInfo, :flowStatus, :comment, 'SYSTEM',
                  :registerTime, 'SYSTEM', :updateTime
                )
                """;
        Map<String, Object> params = new HashMap<>();
        params.put("approveId", entity.getApproveId());
        params.put("instructionOrReview", entity.getInstructionOrReview());
        params.put("fromUserId", entity.getFromUserId());
        params.put("targetKind", entity.getTargetKind());
        params.put("targetApprovementInfo", entity.getTargetApprovementInfo());
        params.put("flowStatus", entity.getFlowStatus());
        params.put("comment", entity.getComment()); // null OK
        params.put("registerTime", entity.getRegisterTime());
        params.put("updateTime", entity.getUpdateTime());
        return jdbc.update(sql, params);
    }

    public int updateStatus(String approveId, String flowStatus, String comment) {
        String sql = """
                UPDATE approve_flow
                SET
                  flow_status = :flowStatus,
                  comment     = :comment,
                  update_time = CURRENT_TIMESTAMP
                WHERE approve_id = :approveId
                """;
        Map<String, Object> params = new HashMap<>();
        params.put("approveId", approveId);
        params.put("flowStatus", flowStatus);
        params.put("comment", comment); // null OK（承認・確認時など理由不要のケース）
        return jdbc.update(sql, params);
    }

    /**
     * approve_flowの行自体を削除する。
     * 「依頼」の削除機能(担当者が自分の依頼を完全に削除する)から使用する。
     * 「指令」はapprove_flow_recipientに宛先行が紐づくため、このメソッドでは削除しない
     * (必要になった場合はapprove_flow_recipientを先に削除するメソッドを別途用意すること)。
     */
    public int deleteById(String approveId) {
        String sql = """
                DELETE FROM approve_flow
                WHERE approve_id = :approveId
                """;
        return jdbc.update(sql, Map.of("approveId", approveId));
    }

    // ------------------------------------------------------------
    // 宛先（admin_approve_recipient）… 指令のみで使用
    // ------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public int insertRecipients(List<AdminApproveRecipientEntity> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO approve_flow_recipient (
                  approve_id, user_id, chk_flg, confirmed_time, register_id,
                  register_time, update_id, update_time
                )
                VALUES (
                  :approveId, :userId, :chkFlg, :confirmedTime, 'SYSTEM',
                  :registerTime, 'SYSTEM', :updateTime
                )
                """;
        Map<String, Object>[] batchParams = recipients.stream()
                .map(r -> {
                    Map<String, Object> p = new HashMap<>();
                    p.put("approveId", r.getApproveId());
                    p.put("userId", r.getUserId());
                    p.put("chkFlg", r.getChkFlg());
                    p.put("confirmedTime", r.getConfirmedTime()); // null OK
                    p.put("registerTime", r.getRegisterTime());
                    p.put("updateTime", r.getUpdateTime());
                    return p;
                })
                .toArray(Map[]::new);
        int[] results = jdbc.batchUpdate(sql, batchParams);
        return Arrays.stream(results).sum();
    }

    public List<AdminApproveRecipientEntity> findRecipientsByApproveId(String approveId) {
        String sql = """
                SELECT recipient_id, approve_id, user_id, chk_flg, confirmed_time, register_time, update_time
                FROM approve_flow_recipient
                WHERE approve_id = :approveId
                ORDER BY register_time
                """;
        return jdbc.query(sql, Map.of("approveId", approveId), RECIPIENT_ROW_MAPPER);
    }

    public AdminApproveRecipientEntity findRecipient(String approveId, Long userId) {
        String sql = """
                SELECT recipient_id, approve_id, user_id, chk_flg, confirmed_time, register_time, update_time
                FROM approve_flow_recipient
                WHERE approve_id = :approveId
                  AND user_id = :userId
                """;
        Map<String, Object> params = Map.of("approveId", approveId, "userId", userId);
        return queryForOne(sql, params, RECIPIENT_ROW_MAPPER);
    }

    /** SELECT ... FOR UPDATE。確認処理中に同一宛先への競合更新（二重確認）を防ぐ。 */
    public AdminApproveRecipientEntity findRecipientForUpdate(String approveId, Long userId) {
        String sql = """
                SELECT recipient_id, approve_id, user_id, chk_flg, confirmed_time, register_time, update_time
                FROM approve_flow_recipient
                WHERE approve_id = :approveId
                  AND user_id = :userId
                FOR UPDATE
                """;
        Map<String, Object> params = Map.of("approveId", approveId, "userId", userId);
        return queryForOne(sql, params, RECIPIENT_ROW_MAPPER);
    }

    public int updateRecipientChkFlg(String approveId, Long userId, String chkFlg, OffsetDateTime confirmedTime) {
        String sql = """
                UPDATE approve_flow_recipient
                SET
                  chk_flg        = :chkFlg,
                  confirmed_time = :confirmedTime,
                  update_time    = CURRENT_TIMESTAMP
                WHERE approve_id = :approveId
                  AND user_id    = :userId
                """;
        Map<String, Object> params = new HashMap<>();
        params.put("approveId", approveId);
        params.put("userId", userId);
        params.put("chkFlg", chkFlg);
        params.put("confirmedTime", confirmedTime); // null OK（未確認に戻す場合）
        return jdbc.update(sql, params);
    }

    public int countUnconfirmedRecipients(String approveId) {
        String sql = """
                SELECT COUNT(*)
                FROM approve_flow_recipient
                WHERE approve_id = :approveId
                  AND chk_flg <> :confirmed
                """;
        Map<String, Object> params = Map.of(
                "approveId", approveId,
                "confirmed", ApproveFlowConstants.CHK_FLG_CONFIRMED);
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count == null ? 0 : count;
    }

    // ------------------------------------------------------------
    // 担当者退会時の後始末（AdminApproveService#handleUserWithdrawal から使用）
    // ------------------------------------------------------------

    /**
     * 退会した担当者〈userId〉が起票者の依頼のうち、「申請済」のものを「保留」にする。
     * 「差し戻し」「承認」「取り消し」のものはそのまま変更しない。
     *
     * @return 更新件数
     */
    public int pendRequestsBySubmitter(Long fromUserId) {
        String sql = """
                UPDATE approve_flow
                SET
                  flow_status = :pending,
                  update_time = CURRENT_TIMESTAMP
                WHERE instruction_or_review = :type
                  AND from_user_id = :fromUserId
                  AND flow_status = :requested
                """;
        Map<String, Object> params = Map.of(
                "pending", ApproveFlowConstants.REVIEW_STATUS_PENDING,
                "type", ApproveFlowConstants.TYPE_REVIEW,
                "fromUserId", fromUserId,
                "requested", ApproveFlowConstants.REVIEW_STATUS_REQUESTED);
        return jdbc.update(sql, params);
    }

    /**
     * 退会した担当者〈userId〉が宛先になっている指令のapproveId一覧を取得する。
     * ヘッダーのステータス再判定（{@link #revertConfirmedHeadersToUnconfirmed}）に使用するため、
     * {@link #resetRecipientsToUnconfirmedByUser} で更新する前に呼び出すこと。
     */
    public List<String> findInstructionApproveIdsByRecipientUser(Long userId) {
        String sql = """
                SELECT approve_id
                FROM approve_flow_recipient
                WHERE user_id = :userId
                """;
        return jdbc.query(sql, Map.of("userId", userId), (rs, rowNum) -> rs.getString("approve_id"));
    }

    /**
     * 退会した担当者〈userId〉の指令の宛先ごとの確認状況を、現在の値によらず強制的に「未確認」に戻す
     * （「元々確認済だった場合は、未確認に上書き」という要件のため）。
     *
     * @return 更新件数
     */
    public int resetRecipientsToUnconfirmedByUser(Long userId) {
        String sql = """
                UPDATE approve_flow_recipient
                SET
                  chk_flg        = :unconfirmed,
                  confirmed_time = NULL,
                  update_time    = CURRENT_TIMESTAMP
                WHERE user_id = :userId
                """;
        Map<String, Object> params = Map.of(
                "unconfirmed", ApproveFlowConstants.CHK_FLG_UNCONFIRMED,
                "userId", userId);
        return jdbc.update(sql, params);
    }

    /**
     * 指定したapproveIdのうち、ヘッダーが「確認済」だった指令を「未確認」に差し戻す。
     * 「差し戻し」「取り消し」済みのヘッダーは対象外（そのまま変更しない）。
     * 宛先の誰か（退会した担当者）の確認が取り消されたことで、
     * 「全員確認済み」という不変条件が崩れた場合の後始末に使用する。
     *
     * @return 更新件数
     */
    public int revertConfirmedHeadersToUnconfirmed(List<String> approveIds) {
        if (approveIds == null || approveIds.isEmpty()) {
            return 0;
        }
        String sql = """
                UPDATE approve_flow
                SET
                  flow_status = :unconfirmed,
                  update_time = CURRENT_TIMESTAMP
                WHERE approve_id IN (:approveIds)
                  AND instruction_or_review = :type
                  AND flow_status = :confirmed
                """;
        Map<String, Object> params = Map.of(
                "unconfirmed", ApproveFlowConstants.INSTRUCTION_STATUS_UNCONFIRMED,
                "approveIds", approveIds,
                "type", ApproveFlowConstants.TYPE_INSTRUCTION,
                "confirmed", ApproveFlowConstants.INSTRUCTION_STATUS_CONFIRMED);
        return jdbc.update(sql, params);
    }

    // ------------------------------------------------------------
    // 共通処理
    // ------------------------------------------------------------

    private <T> T queryForOne(String sql, Map<String, ?> params, RowMapper<T> rowMapper) {
        List<T> list = jdbc.query(sql, params, rowMapper);
        return list.stream().findFirst().orElse(null);
    }
}
