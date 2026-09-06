package dev.web.api.bm_a028;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dev.common.util.DateOffsetDecisionUtil;
import dev.web.repository.user.ApproveFlowRepository;
import dev.web.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * 承認フロー（依頼/指令）サービス。
 *
 * 依頼 : 担当者(authFlg=2) が起票し、管理者(authFlg=1) が承認 or 差し戻しする。
 *        申請者本人は、管理者が処理する前であれば取り消せる。
 * 指令 : 管理者(authFlg=1) が起票し、その時点の担当者(authFlg=2)全員へ一斉送信する。
 *        各担当者は確認のみ行える。宛先全員が確認済みになるとヘッダーも自動的に「確認済」になる。
 *        管理者は自分が出した指令を差し戻し・取り消しできる（理由はcommentに残す）。
 *
 * ※ AuthController/AdminUserServiceに合わせ、responseCodeは"200"/"400"/"404"/"409"等の文字列で返す。
 *
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class AdminApproveService {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(DateOffsetDecisionUtil.getZoneId());

    private final ApproveFlowRepository approveFlowRepository;
    private final UserRepository userRepository;

    // ==================================================================
    // 依頼（担当者 → 管理者）
    // ==================================================================

    @Transactional
    public AdminApproveActionResponse createRequest(Long fromUserId, CreateRequestRequest req) {
        String validationError = validateTarget(req.getTargetKind(), req.getTargetApprovementInfo());
        if (validationError != null) {
            return badRequest(validationError);
        }
        String approveId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now(DateOffsetDecisionUtil.getZoneId());
        AdminApproveEntity entity = AdminApproveEntity.builder()
                .approveId(approveId)
                .instructionOrReview(ApproveFlowConstants.TYPE_REVIEW)
                .fromUserId(fromUserId)
                .targetKind(req.getTargetKind())
                .targetApprovementInfo(req.getTargetApprovementInfo())
                .flowStatus(ApproveFlowConstants.REVIEW_STATUS_REQUESTED)
                .registerTime(now)
                .updateTime(now)
                .build();
        approveFlowRepository.insert(entity);
        return AdminApproveActionResponse.builder()
                .responseCode("200")
                .message("依頼を登録しました。")
                .approveId(approveId)
                .build();
    }

    /** 管理者向け：依頼の一覧を取得する */
    public AdminApproveListResponse getRequestsForAdmin() {
        List<AdminApproveEntity> requests = approveFlowRepository.findRequests();
        Map<Long, String> userNames = resolveUserNames(requests.stream()
                .map(AdminApproveEntity::getFromUserId)
                .collect(Collectors.toList()));
        List<AdminApproveItemResponse> items = requests.stream()
                .map(e -> toItemResponse(e, userNames.get(e.getFromUserId())))
                .collect(Collectors.toList());
        return AdminApproveListResponse.builder()
                .responseCode("200")
                .message("OK")
                .items(items)
                .build();
    }

    /** 担当者向け：自分が申請した依頼の一覧を取得する */
    public AdminApproveListResponse getMyRequests(Long fromUserId) {
        List<AdminApproveEntity> requests = approveFlowRepository.findRequestsBySubmitter(fromUserId);
        String myName = resolveUserName(fromUserId);
        List<AdminApproveItemResponse> items = requests.stream()
                .map(e -> toItemResponse(e, myName))
                .collect(Collectors.toList());
        return AdminApproveListResponse.builder()
                .responseCode("200")
                .message("OK")
                .items(items)
                .build();
    }

    @Transactional
    public AdminApproveActionResponse approveRequest(String approveId, Long adminUserId) {
        return changeRequestStatus(approveId, ApproveFlowConstants.REVIEW_STATUS_APPROVED, null,
                ApproveFlowConstants.REVIEW_STATUS_REQUESTED, "承認しました。");
    }

    @Transactional
    public AdminApproveActionResponse rejectRequest(String approveId, Long adminUserId, String comment) {
        if (!StringUtils.hasText(comment)) {
            return badRequest("差し戻し時はコメントが必須です。");
        }
        return changeRequestStatus(approveId, ApproveFlowConstants.REVIEW_STATUS_REJECTED, comment,
                ApproveFlowConstants.REVIEW_STATUS_REQUESTED, "差し戻しました。");
    }

    @Transactional
    public AdminApproveActionResponse cancelRequest(String approveId, Long requesterUserId, String comment) {
        AdminApproveEntity entity = approveFlowRepository.findByIdForUpdate(approveId);
        if (entity == null || !ApproveFlowConstants.TYPE_REVIEW.equals(entity.getInstructionOrReview())) {
            return notFound("対象の依頼が見つかりません。");
        }
        if (!entity.getFromUserId().equals(requesterUserId)) {
            return forbidden("自分が申請した依頼のみ取り消せます。");
        }
        if (!ApproveFlowConstants.REVIEW_STATUS_REQUESTED.equals(entity.getFlowStatus())) {
            return conflict("この依頼は既に処理済みのため取り消せません。");
        }
        approveFlowRepository.updateStatus(approveId, ApproveFlowConstants.REVIEW_STATUS_CANCELLED, comment);
        return AdminApproveActionResponse.builder()
                .responseCode("200")
                .message("依頼を取り消しました。")
                .approveId(approveId)
                .build();
    }

    private AdminApproveActionResponse changeRequestStatus(String approveId, String newStatus, String comment,
            String requiredCurrentStatus, String successMessage) {
        AdminApproveEntity entity = approveFlowRepository.findByIdForUpdate(approveId);
        if (entity == null || !ApproveFlowConstants.TYPE_REVIEW.equals(entity.getInstructionOrReview())) {
            return notFound("対象の依頼が見つかりません。");
        }
        if (!requiredCurrentStatus.equals(entity.getFlowStatus())) {
            return conflict("この依頼は既に処理済みです。");
        }
        approveFlowRepository.updateStatus(approveId, newStatus, comment);
        return AdminApproveActionResponse.builder()
                .responseCode("200")
                .message(successMessage)
                .approveId(approveId)
                .build();
    }

    // ==================================================================
    // 指令（管理者 → 担当者全員）
    // ==================================================================

    @Transactional
    public AdminApproveActionResponse createInstruction(Long adminUserId, CreateInstructionRequest req) {
        String validationError = validateTarget(req.getTargetKind(), req.getTargetApprovementInfo());
        if (validationError != null) {
            return badRequest(validationError);
        }
        // authFlg=2（担当者）を都度取得して一斉送信する。0人の場合でも指令自体は作成する。
        List<Long> assigneeUserIds = userRepository.findUserIdsByAuthFlg(2);

        String approveId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now(DateOffsetDecisionUtil.getZoneId());
        AdminApproveEntity entity = AdminApproveEntity.builder()
                .approveId(approveId)
                .instructionOrReview(ApproveFlowConstants.TYPE_INSTRUCTION)
                .fromUserId(adminUserId)
                .targetKind(req.getTargetKind())
                .targetApprovementInfo(req.getTargetApprovementInfo())
                .flowStatus(ApproveFlowConstants.INSTRUCTION_STATUS_UNCONFIRMED)
                .registerTime(now)
                .updateTime(now)
                .build();
        approveFlowRepository.insert(entity);

        if (!assigneeUserIds.isEmpty()) {
            List<AdminApproveRecipientEntity> recipients = new ArrayList<>();
            for (Long userId : assigneeUserIds) {
                recipients.add(AdminApproveRecipientEntity.builder()
                        .approveId(approveId)
                        .userId(userId)
                        .chkFlg(ApproveFlowConstants.CHK_FLG_UNCONFIRMED)
                        .registerTime(now)
                        .updateTime(now)
                        .build());
            }
            approveFlowRepository.insertRecipients(recipients);
        }

        return AdminApproveActionResponse.builder()
                .responseCode("200")
                .message("指令を発行しました。（宛先" + assigneeUserIds.size() + "名）")
                .approveId(approveId)
                .build();
    }

    /** 管理者向け：自分が発行した指令の一覧（宛先ごとの確認状況つき）を取得する */
    public AdminApproveListResponse getInstructionsForAdmin(Long adminUserId) {
        List<AdminApproveEntity> instructions = approveFlowRepository.findInstructions();
        List<AdminApproveItemResponse> items = new ArrayList<>();
        for (AdminApproveEntity e : instructions) {
            List<AdminApproveRecipientEntity> recipients = approveFlowRepository.findRecipientsByApproveId(e.getApproveId());
            Map<Long, String> userNames = resolveUserNames(recipients.stream()
                    .map(AdminApproveRecipientEntity::getUserId)
                    .collect(Collectors.toList()));
            long confirmedCount = recipients.stream()
                    .filter(r -> ApproveFlowConstants.CHK_FLG_CONFIRMED.equals(r.getChkFlg()))
                    .count();
            AdminApproveItemResponse item = toItemResponse(e, resolveUserName(e.getFromUserId()));
            item.setTotalRecipientCount(recipients.size());
            item.setConfirmedRecipientCount((int) confirmedCount);
            item.setRecipients(recipients.stream()
                    .map(r -> AdminApproveRecipientItemResponse.builder()
                            .userId(r.getUserId())
                            .userName(userNames.get(r.getUserId()))
                            .chkFlg(r.getChkFlg())
                            .confirmedTime(r.getConfirmedTime() == null ? null : FMT.format(r.getConfirmedTime().toInstant()))
                            .build())
                    .collect(Collectors.toList()));
            items.add(item);
        }
        return AdminApproveListResponse.builder()
                .responseCode("200")
                .message("OK")
                .items(items)
                .build();
    }

    /** 担当者向け：自分が宛先に含まれる指令の一覧（自分の確認状況つき）を取得する */
    public AdminApproveListResponse getInstructionsForRecipient(Long userId) {
        List<AdminApproveEntity> instructions = approveFlowRepository.findInstructionsForRecipient(userId);
        Map<Long, String> fromUserNames = resolveUserNames(instructions.stream()
                .map(AdminApproveEntity::getFromUserId)
                .collect(Collectors.toList()));
        List<AdminApproveItemResponse> items = new ArrayList<>();
        for (AdminApproveEntity e : instructions) {
            AdminApproveRecipientEntity myRecipient = approveFlowRepository.findRecipient(e.getApproveId(), userId);
            AdminApproveItemResponse item = toItemResponse(e, fromUserNames.get(e.getFromUserId()));
            item.setConfirmedByMe(myRecipient != null
                    && ApproveFlowConstants.CHK_FLG_CONFIRMED.equals(myRecipient.getChkFlg()));
            items.add(item);
        }
        return AdminApproveListResponse.builder()
                .responseCode("200")
                .message("OK")
                .items(items)
                .build();
    }

    @Transactional
    public AdminApproveActionResponse confirmInstruction(String approveId, Long userId) {
        AdminApproveEntity header = approveFlowRepository.findByIdForUpdate(approveId);
        if (header == null || !ApproveFlowConstants.TYPE_INSTRUCTION.equals(header.getInstructionOrReview())) {
            return notFound("対象の指令が見つかりません。");
        }
        if (ApproveFlowConstants.INSTRUCTION_STATUS_REJECTED.equals(header.getFlowStatus())
                || ApproveFlowConstants.INSTRUCTION_STATUS_CANCELLED.equals(header.getFlowStatus())) {
            return conflict("この指令は既に差し戻し・取り消しされています。");
        }
        AdminApproveRecipientEntity recipient = approveFlowRepository.findRecipientForUpdate(approveId, userId);
        if (recipient == null) {
            return forbidden("自分宛ての指令ではありません。");
        }
        if (ApproveFlowConstants.CHK_FLG_CONFIRMED.equals(recipient.getChkFlg())) {
            return AdminApproveActionResponse.builder()
                    .responseCode("200")
                    .message("既に確認済みです。")
                    .approveId(approveId)
                    .build();
        }
        OffsetDateTime now = OffsetDateTime.now(DateOffsetDecisionUtil.getZoneId());
        approveFlowRepository.updateRecipientChkFlg(approveId, userId, ApproveFlowConstants.CHK_FLG_CONFIRMED, now);

        // 宛先全員が確認済みになったら、ヘッダーのステータスも「確認済」にする。
        int unconfirmedCount = approveFlowRepository.countUnconfirmedRecipients(approveId);
        if (unconfirmedCount == 0) {
            approveFlowRepository.updateStatus(approveId, ApproveFlowConstants.INSTRUCTION_STATUS_CONFIRMED, null);
        }
        return AdminApproveActionResponse.builder()
                .responseCode("200")
                .message("確認しました。")
                .approveId(approveId)
                .build();
    }

    @Transactional
    public AdminApproveActionResponse rejectInstruction(String approveId, Long adminUserId, String comment) {
        if (!StringUtils.hasText(comment)) {
            return badRequest("差し戻し時はコメントが必須です。");
        }
        return changeInstructionStatusByAdmin(approveId, adminUserId, comment,
                ApproveFlowConstants.INSTRUCTION_STATUS_REJECTED, "指令を差し戻しました。");
    }

    @Transactional
    public AdminApproveActionResponse cancelInstruction(String approveId, Long adminUserId, String comment) {
        if (!StringUtils.hasText(comment)) {
            return badRequest("取り消し時はコメントが必須です。");
        }
        return changeInstructionStatusByAdmin(approveId, adminUserId, comment,
                ApproveFlowConstants.INSTRUCTION_STATUS_CANCELLED, "指令を取り消しました。");
    }

    private AdminApproveActionResponse changeInstructionStatusByAdmin(String approveId, Long adminUserId,
            String comment, String newStatus, String successMessage) {
        AdminApproveEntity header = approveFlowRepository.findByIdForUpdate(approveId);
        if (header == null || !ApproveFlowConstants.TYPE_INSTRUCTION.equals(header.getInstructionOrReview())) {
            return notFound("対象の指令が見つかりません。");
        }
        if (!header.getFromUserId().equals(adminUserId)) {
            return forbidden("自分が発行した指令のみ操作できます。");
        }
        if (ApproveFlowConstants.INSTRUCTION_STATUS_REJECTED.equals(header.getFlowStatus())
                || ApproveFlowConstants.INSTRUCTION_STATUS_CANCELLED.equals(header.getFlowStatus())) {
            return conflict("この指令は既に差し戻し・取り消しされています。");
        }
        approveFlowRepository.updateStatus(approveId, newStatus, comment);
        return AdminApproveActionResponse.builder()
                .responseCode("200")
                .message(successMessage)
                .approveId(approveId)
                .build();
    }

    // ==================================================================
    // 共通処理
    // ==================================================================

    private String validateTarget(String targetKind, String targetApprovementInfo) {
        if (!StringUtils.hasText(targetKind)
                || !(ApproveFlowConstants.TARGET_KIND_NOTICE.equals(targetKind)
                        || ApproveFlowConstants.TARGET_KIND_SCREEN.equals(targetKind))) {
            return "targetKind は NOTICE または SCREEN を指定してください。";
        }
        if (!StringUtils.hasText(targetApprovementInfo)) {
            return "targetApprovementInfo は必須です。";
        }
        return null;
    }

    private AdminApproveItemResponse toItemResponse(AdminApproveEntity e, String fromUserName) {
        return AdminApproveItemResponse.builder()
                .approveId(e.getApproveId())
                .instructionOrReview(e.getInstructionOrReview())
                .fromUserId(e.getFromUserId())
                .fromUserName(fromUserName)
                .targetKind(e.getTargetKind())
                .targetApprovementInfo(e.getTargetApprovementInfo())
                .flowStatus(e.getFlowStatus())
                .comment(e.getComment())
                .registerTime(e.getRegisterTime() == null ? null : FMT.format(e.getRegisterTime().toInstant()))
                .updateTime(e.getUpdateTime() == null ? null : FMT.format(e.getUpdateTime().toInstant()))
                .build();
    }

    private String resolveUserName(Long userId) {
        return resolveUserNames(List.of(userId)).get(userId);
    }

    private Map<Long, String> resolveUserNames(List<Long> userIds) {
        List<Long> distinct = userIds.stream().distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return userRepository.findUserNamesByUserIds(distinct);
    }

    private AdminApproveActionResponse badRequest(String message) {
        return AdminApproveActionResponse.builder().responseCode("400").message(message).build();
    }

    private AdminApproveActionResponse notFound(String message) {
        return AdminApproveActionResponse.builder().responseCode("404").message(message).build();
    }

    private AdminApproveActionResponse forbidden(String message) {
        return AdminApproveActionResponse.builder().responseCode("403").message(message).build();
    }

    private AdminApproveActionResponse conflict(String message) {
        return AdminApproveActionResponse.builder().responseCode("409").message(message).build();
    }
}
