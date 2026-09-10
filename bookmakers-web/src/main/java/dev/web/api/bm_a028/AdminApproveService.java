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

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.entity.MailInfoMasterEntity;
import dev.common.util.DateOffsetDecisionUtil;
import dev.web.mail.MailSendResponse;
import dev.web.mail.MailSendService;
import dev.web.repository.user.ApproveFlowRepository;
import dev.web.repository.user.NoticeRepository;
import dev.web.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * 承認フロー（依頼/指令）サービス。
 *
 * 依頼 : 担当者(authFlg=2) が起票し、管理者(authFlg=1) が承認 or 差し戻しする。
 *        申請者本人は、管理者が処理する前であれば取り消せる。
 *        targetKind=NOTICE の依頼は「お知らせ登録の承認」を表し、承認されると
 *        対象のお知らせ(notices)が自動的にPUBLISHEDになる（下記approveRequest参照）。
 *        targetKind=MAIL_INFO の依頼は「メール情報登録の承認」を表し、承認されると
 *        targetApprovementInfoにJSON文字列で保持しているメール情報が実際にメール情報マスタへ
 *        登録される（担当者はメール情報登録画面から直接登録できないため、この依頼経由で登録する）。
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
    private final NoticeRepository noticeRepository;
    private final MailSendService mailSendService;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    /**
     * 依頼を承認する。
     * targetKind=NOTICE の依頼の場合、承認と同時に対象のお知らせ(notices)をPUBLISHEDにする。
     * お知らせの公開に失敗した場合(対象が見つからない等)は、依頼のステータスも変更しない
     * （@Transactionalなので、ここでエラーレスポンスを返す＝何もコミットされない）。
     * targetKind=MAIL_INFO の依頼の場合、承認と同時にtargetApprovementInfoに保持している
     * メール情報を実際にメール情報マスタへ登録する（登録できて初めてメール一覧に出てくる）。
     * 登録に失敗した場合(承認前に同じmailIdが別経路で登録された等)も、依頼のステータスは
     * 変更しない。
     */
    @Transactional
    public AdminApproveActionResponse approveRequest(String approveId, Long adminUserId) {
        AdminApproveEntity entity = approveFlowRepository.findByIdForUpdate(approveId);
        if (entity == null || !ApproveFlowConstants.TYPE_REVIEW.equals(entity.getInstructionOrReview())) {
            return notFound("対象の依頼が見つかりません。");
        }
        if (!ApproveFlowConstants.REVIEW_STATUS_REQUESTED.equals(entity.getFlowStatus())) {
            return conflict("この依頼は既に処理済みです。");
        }

        if (ApproveFlowConstants.TARGET_KIND_NOTICE.equals(entity.getTargetKind())) {
            Long noticeId;
            try {
                noticeId = Long.valueOf(entity.getTargetApprovementInfo());
            } catch (NumberFormatException e) {
                return badRequest("対象のお知らせIDが不正です。");
            }
            int published = noticeRepository.publish(noticeId, String.valueOf(adminUserId));
            if (published != 1) {
                return notFound("対象のお知らせが見つかりません。承認前に削除された可能性があります。");
            }
        }

        if (ApproveFlowConstants.TARGET_KIND_MAIL_INFO.equals(entity.getTargetKind())) {
            MailInfoMasterEntity mailInfo;
            try {
                mailInfo = objectMapper.readValue(entity.getTargetApprovementInfo(), MailInfoMasterEntity.class);
            } catch (Exception e) {
                return badRequest("対象のメール情報の内容が不正です。");
            }
            // regMailMaster自体がmailIdの重複チェック・実際のinsertまで行う。
            // ここではその結果(responseCode/message)をそのまま依頼の承認結果として返す
            // (承認済にできるのはinsertが成功した"200"の場合のみ)。
            MailSendResponse regResult = mailSendService.regMailMaster(mailInfo);
            if (!"200".equals(regResult.getResponseCode())) {
                return AdminApproveActionResponse.builder()
                        .responseCode(regResult.getResponseCode())
                        .message(regResult.getMessage())
                        .approveId(approveId)
                        .build();
            }
        }

        approveFlowRepository.updateStatus(approveId, ApproveFlowConstants.REVIEW_STATUS_APPROVED, null);
        return AdminApproveActionResponse.builder()
                .responseCode("200")
                .message("承認しました。")
                .approveId(approveId)
                .build();
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
    // 担当者退会時の後始末
    // ==================================================================

    /**
     * 担当者が退会した際に呼び出す処理。
     *
     * <p><b>呼び出し方</b>： 担当者の {@code users."authFlg"} を退会済みの値
     * （{@link UserRepository#AUTH_FLG_WITHDRAWN}）に更新する既存の退会処理
     * （本zipには含まれていない {@code AdminUserService} 等）の中から、
     * 同一トランザクション内でこのメソッドを呼び出してください。
     * {@code authFlg} の更新とこのメソッドの処理が同一トランザクションでコミット／
     * ロールバックされないと、退会したのに依頼・指令の状態だけ更新されない
     * （またはその逆の）不整合が起こり得ます。
     *
     * <p>行う処理（観点5に対応）：
     * <ol>
     *   <li>退会した担当者が起票した依頼のうち「申請済」のものを「保留」にする
     *       （「差し戻し」のものはそのまま）。</li>
     *   <li>退会した担当者が宛先の指令について、{@code admin_approve_recipient} の
     *       確認状況を（元々「確認済」だった場合を含め）強制的に「未確認」に戻す。</li>
     *   <li>2.の結果、宛先全員確認済みで「確認済」になっていた指令ヘッダーがあれば、
     *       「未確認」に差し戻す（「差し戻し」「取り消し」済みのヘッダーは対象外）。</li>
     * </ol>
     *
     * <p>退会した担当者の表示名・メールアドレスは、{@code authFlg} が退会済みの値に
     * なっていれば {@link UserRepository#findUserNamesByUserIds} 側で自動的に
     * 「退会済み」にマスクされるため、ここでの対応は不要。
     *
     * @param withdrawnUserId 退会した担当者のuser_id
     */
    @Transactional
    public void handleUserWithdrawal(Long withdrawnUserId) {
        approveFlowRepository.pendRequestsBySubmitter(withdrawnUserId);

        List<String> affectedInstructionIds =
                approveFlowRepository.findInstructionApproveIdsByRecipientUser(withdrawnUserId);
        approveFlowRepository.resetRecipientsToUnconfirmedByUser(withdrawnUserId);
        approveFlowRepository.revertConfirmedHeadersToUnconfirmed(affectedInstructionIds);
    }

    // ==================================================================
    // 共通処理
    // ==================================================================

    private String validateTarget(String targetKind, String targetApprovementInfo) {
        if (!StringUtils.hasText(targetKind)
                || !(ApproveFlowConstants.TARGET_KIND_NOTICE.equals(targetKind)
                        || ApproveFlowConstants.TARGET_KIND_SCREEN.equals(targetKind)
                        || ApproveFlowConstants.TARGET_KIND_MAIL_INFO.equals(targetKind))) {
            return "targetKind は NOTICE、SCREEN または MAIL_INFO を指定してください。";
        }
        if (!StringUtils.hasText(targetApprovementInfo)) {
            return "targetApprovementInfo は必須です。";
        }
        if (ApproveFlowConstants.TARGET_KIND_MAIL_INFO.equals(targetKind)) {
            // 起票の時点でJSONとして壊れていないかだけ検証しておく
            // (実際のmailId重複チェック等はregMailMaster任せで、承認時に行う)。
            try {
                objectMapper.readValue(targetApprovementInfo, MailInfoMasterEntity.class);
            } catch (Exception e) {
                return "targetApprovementInfo(メール情報)の形式が不正です。";
            }
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
