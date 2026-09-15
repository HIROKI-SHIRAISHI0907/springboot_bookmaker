package dev.web.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.auth0.jwt.interfaces.DecodedJWT;

import dev.common.config.MailConfig;
import dev.common.constant.MailIdConstant;
import dev.common.constant.S3BucketConstant;
import dev.common.constant.S3Const;
import dev.common.mail.PutMailNoticeJson;
import dev.common.util.MailConvertS3BucketUtil;
import dev.web.api.bm_a028.AdminApproveActionResponse;
import dev.web.api.bm_a028.AdminApproveListResponse;
import dev.web.api.bm_a028.AdminApproveService;
import dev.web.api.bm_a028.ApproveActionRequest;
import dev.web.api.bm_a028.ApproveFlowConstants;
import dev.web.api.bm_a028.CreateInstructionRequest;
import dev.web.api.bm_a028.CreateRequestRequest;
import dev.web.jwt.JwtService;
import dev.web.mail.MailSendResponse;
import dev.web.mail.MailSendService;
import dev.web.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 承認フロー（依頼/指令）コントローラー。
 *
 * 依頼: 担当者(ROLE_ADMIN_SUB) が起票し、管理者(ROLE_ADMIN) が承認/差し戻しする。
 * 指令: 管理者(ROLE_ADMIN) が起票し、担当者(ROLE_ADMIN_SUB)全員が確認する。
 *
 * ロール判定はAuthController#loginで発行したJWTの"roles"クレームを見て行う想定。
 * JwtServiceの実装に応じて、クレーム名・取得方法は調整してください。
 *
 * -----------------------------------------------------------------
 * 【通知メールについての方針】
 * 依頼/指令の targetKind は NOTICE / SCREEN / MAIL_INFO のいずれもあり得るが、
 * 「受付・承認・差し戻し・取り消し・削除・発行されました」という
 * “承認フロー自体の状態変化を知らせる通知メール” は、targetKindに関係なく
 * 常に同じ固定テンプレート（mail_info_master.mail_id = MailIdConstant.BM_MAIL_XXX、
 * 事前に登録済みであること）を使う。
 *
 * targetKind=MAIL_INFO の場合にAdminApproveServiceが返す keyId/toMailAddress は
 * 「承認対象として登録されようとしている“新しいメール種別”自身の情報」であり、
 * これは承認されるまでmail_info_masterに存在しない（存在してはいけない）。
 * 以前の実装はこの「対象の情報」を誤って「通知メール自体のテンプレートID」として
 * 使ってしまっており、承認される前は必ず存在しないIDを参照することになるため、
 * 通知メールの送信が常に失敗していた。
 *
 * @author shiraishitoshio
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/approve")
@Slf4j
public class AdminApproveController {

	private final JwtService jwtService;
	private final UserRepository userRepository;
	private final AdminApproveService approveService;
	private final MailSendService mailSendService;
	private final PutMailNoticeJson putMailNoticeJson;
	private final MailConfig mailConfig;

	// ------------------------------------------------------------
	// 依頼（担当者 → 管理者）
	// ------------------------------------------------------------

	/** 担当者が依頼を起票する */
	@PostMapping("/requests")
	public ResponseEntity<AdminApproveActionResponse> createRequest(
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
			@RequestBody CreateRequestRequest req) {
		CurrentUser current = resolveCurrentUser(authorizationHeader);
		if (current == null) {
			return unauthorized();
		}
		if (!current.roles.contains("ROLE_ADMIN_SUB")) {
			return forbidden("担当者のみ依頼を起票できます。");
		}
		log.info("依頼起票リクエスト: リクエスト:({})", req);
		AdminApproveActionResponse res = approveService.createRequest(current.userId, req);
		log.info("依頼起票レスポンス: ユーザー:({}),レスポンス:({})", current, res);
		if ("200".equals(res.getResponseCode())) {
			Map<String, String> placeholders = new HashMap<String, String>();
			placeholders.put("SUBJECT_TAG_NAME", "受付されました。");
			placeholders.put("USER_NAME", String.valueOf(current.userId));
			placeholders.put("FILL_NAME", "受付");
			placeholders.put("TARGET_KIND_LABEL", ApproveFlowConstants.TYPE_REVIEW);
			placeholders.put("TARGET_SUMMARY", "NONE");
			placeholders.put("TARGET_TITLE", ApproveFlowConstants.TYPE_REVIEW);
			placeholders.put("TARGET_NAME", String.valueOf(current.userId));
			placeholders.put("REJECTED_AT", String.valueOf(res.getReturnDate()));
			placeholders.put("APPROVE_ID", res.getApproveId());
			placeholders.put("MIX_BUCKET", S3BucketConstant.S3_MAIL_ACCEPT);
			notifyApproveFlow(current.email, placeholders, S3BucketConstant.S3_MAIL_ACCEPT);
		}
		return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
	}

	/**
	 * 依頼の一覧を取得する。
	 * 管理者: 全件（承認/差し戻しモーダルの元データ）。
	 * 担当者: 自分が申請した依頼のみ。
	 */
	@GetMapping("/requests")
	public ResponseEntity<AdminApproveListResponse> listRequests(
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
		CurrentUser current = resolveCurrentUser(authorizationHeader);
		if (current == null) {
			return unauthorizedList();
		}
		AdminApproveListResponse res;
		if (current.roles.contains("ROLE_ADMIN")) {
			res = approveService.getRequestsForAdmin();
		} else if (current.roles.contains("ROLE_ADMIN_SUB")) {
			res = approveService.getMyRequests(current.userId);
		} else {
			return forbiddenList("管理者または担当者のみ依頼一覧を確認できます。");
		}
		return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
	}

	/** 管理者が依頼を承認する */
	@PatchMapping("/requests/{approveId}/approve")
	public ResponseEntity<AdminApproveActionResponse> approveRequest(
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
			@PathVariable String approveId) {
		CurrentUser current = resolveCurrentUser(authorizationHeader);
		if (current == null) {
			return unauthorized();
		}
		if (!current.roles.contains("ROLE_ADMIN")) {
			return forbidden("管理者のみ承認できます。");
		}
		log.info("依頼承認リクエスト: 承認ID:({})", approveId);
		AdminApproveActionResponse res = approveService.approveRequest(approveId, current.userId);
		log.info("依頼承認レスポンス: ユーザー:({}),レスポンス:({})", current, res);
		if ("200".equals(res.getResponseCode())) {
			Map<String, String> placeholders = new HashMap<String, String>();
			placeholders.put("SUBJECT_TAG_NAME", "承認されました。");
			placeholders.put("USER_NAME", String.valueOf(current.userId));
			placeholders.put("FILL_NAME", "承認");
			placeholders.put("TARGET_KIND_LABEL", ApproveFlowConstants.REVIEW_STATUS_APPROVED);
			placeholders.put("TARGET_SUMMARY", "NONE");
			placeholders.put("TARGET_TITLE", ApproveFlowConstants.REVIEW_STATUS_APPROVED);
			placeholders.put("TARGET_NAME", String.valueOf(mailConfig.getSourceMailAddress()));
			placeholders.put("REJECTED_AT", String.valueOf(res.getReturnDate()));
			placeholders.put("APPROVE_ID", res.getApproveId());
			placeholders.put("MIX_BUCKET", S3BucketConstant.S3_MAIL_ACCEPT);
			notifyApproveFlow(current.email, placeholders, S3BucketConstant.S3_MAIL_ACCEPT);
		}
		return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
	}

	/** 管理者が依頼を差し戻す */
	@PatchMapping("/requests/{approveId}/reject")
	public ResponseEntity<AdminApproveActionResponse> rejectRequest(
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
			@PathVariable String approveId,
			@RequestBody ApproveActionRequest req) {
		CurrentUser current = resolveCurrentUser(authorizationHeader);
		if (current == null) {
			return unauthorized();
		}
		if (!current.roles.contains("ROLE_ADMIN")) {
			return forbidden("管理者のみ差し戻しできます。");
		}
		log.info("依頼差し戻しリクエスト: リクエスト:({})", req);
		AdminApproveActionResponse res = approveService.rejectRequest(approveId, current.userId, req.getComment());
		log.info("依頼差し戻しレスポンス: ユーザー:({}),レスポンス:({})", current, res);
		if ("200".equals(res.getResponseCode())) {
			Map<String, String> placeholders = new HashMap<String, String>();
			placeholders.put("SUBJECT_TAG_NAME", "差し戻しされました。");
			placeholders.put("USER_NAME", String.valueOf(current.userId));
			placeholders.put("FILL_NAME", "差し戻し");
			placeholders.put("TARGET_KIND_LABEL", ApproveFlowConstants.REVIEW_STATUS_REJECTED);
			placeholders.put("TARGET_SUMMARY", "NONE");
			placeholders.put("TARGET_TITLE", ApproveFlowConstants.REVIEW_STATUS_REJECTED);
			placeholders.put("TARGET_NAME", String.valueOf(mailConfig.getSourceMailAddress()));
			placeholders.put("REJECTED_AT", String.valueOf(res.getReturnDate()));
			placeholders.put("APPROVE_ID", res.getApproveId());
			placeholders.put("REASON_SENTENCE", res.getComment());
			placeholders.put("MIX_BUCKET", S3BucketConstant.S3_MAIL_REJECT);
			notifyApproveFlow(current.email, placeholders, S3BucketConstant.S3_MAIL_REJECT);
		}
		return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
	}

	/** 担当者が自分の依頼を取り消す */
	@PatchMapping("/requests/{approveId}/cancel")
	public ResponseEntity<AdminApproveActionResponse> cancelRequest(
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
			@PathVariable String approveId,
			@RequestBody ApproveActionRequest req) {
		CurrentUser current = resolveCurrentUser(authorizationHeader);
		if (current == null) {
			return unauthorized();
		}
		if (!current.roles.contains("ROLE_ADMIN_SUB")) {
			return forbidden("担当者のみ依頼を取り消せます。");
		}
		log.info("依頼取り消しリクエスト: リクエスト:({})", req);
		AdminApproveActionResponse res = approveService.cancelRequest(approveId, current.userId, req.getComment());
		log.info("依頼取り消しレスポンス: ユーザー:({}),レスポンス:({})", current, res);
		if ("200".equals(res.getResponseCode())) {
			Map<String, String> placeholders = new HashMap<String, String>();
			placeholders.put("SUBJECT_TAG_NAME", "取り消しされました。");
			placeholders.put("USER_NAME", String.valueOf(current.userId));
			placeholders.put("FILL_NAME", "取り消し");
			placeholders.put("TARGET_KIND_LABEL", ApproveFlowConstants.REVIEW_STATUS_CANCELLED);
			placeholders.put("TARGET_SUMMARY", "NONE");
			placeholders.put("TARGET_TITLE", ApproveFlowConstants.REVIEW_STATUS_CANCELLED);
			placeholders.put("TARGET_NAME", String.valueOf(current.userId));
			placeholders.put("REJECTED_AT", String.valueOf(res.getReturnDate()));
			placeholders.put("APPROVE_ID", res.getApproveId());
			placeholders.put("MIX_BUCKET", S3BucketConstant.S3_MAIL_CANCEL);
			notifyApproveFlow(current.email, placeholders, S3BucketConstant.S3_MAIL_CANCEL);
		}
		return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
	}

	/**
	 * 担当者が自分の依頼を削除する。
	 * 「取り消す」(cancel)とは異なり、approve_flowの行自体を完全に削除する(復元不可)。
	 * ステータスは問わない(申請済でも、既に承認/差し戻し/取り消し済みでも削除可能)。
	 * 自分が申請した依頼以外は削除できない(AdminApproveService#deleteRequestでチェック)。
	 */
	@DeleteMapping("/requests/{approveId}")
	public ResponseEntity<AdminApproveActionResponse> deleteRequest(
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
			@PathVariable String approveId) {
		CurrentUser current = resolveCurrentUser(authorizationHeader);
		if (current == null) {
			return unauthorized();
		}
		if (!current.roles.contains("ROLE_ADMIN_SUB")) {
			return forbidden("担当者のみ依頼を削除できます。");
		}
		log.info("依頼削除リクエスト: 削除ID:({})", approveId);
		AdminApproveActionResponse res = approveService.deleteRequest(approveId, current.userId);
		log.info("依頼削除レスポンス: ユーザー:({}),レスポンス:({})", current, res);
		if ("200".equals(res.getResponseCode())) {
			Map<String, String> placeholders = new HashMap<String, String>();
			placeholders.put("SUBJECT_TAG_NAME", "削除されました。");
			placeholders.put("USER_NAME", String.valueOf(current.userId));
			placeholders.put("FILL_NAME", "削除");
			placeholders.put("TARGET_KIND_LABEL", ApproveFlowConstants.REVIEW_STATUS_DELETED);
			placeholders.put("TARGET_SUMMARY", "NONE");
			placeholders.put("TARGET_TITLE", ApproveFlowConstants.REVIEW_STATUS_DELETED);
			placeholders.put("TARGET_NAME", String.valueOf(current.userId));
			placeholders.put("REJECTED_AT", String.valueOf(res.getReturnDate()));
			placeholders.put("APPROVE_ID", res.getApproveId());
			placeholders.put("MIX_BUCKET", S3BucketConstant.S3_MAIL_DELETE);
			notifyApproveFlow(current.email, placeholders, S3BucketConstant.S3_MAIL_DELETE);
		}
		return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
	}

	// ------------------------------------------------------------
	// 指令（管理者 → 担当者全員）
	// ------------------------------------------------------------

	/** 管理者が指令を発行する（その時点の担当者全員へ一斉送信する） */
	@PostMapping("/instructions")
	public ResponseEntity<AdminApproveActionResponse> createInstruction(
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
			@RequestBody CreateInstructionRequest req) {
		CurrentUser current = resolveCurrentUser(authorizationHeader);
		if (current == null) {
			return unauthorized();
		}
		if (!current.roles.contains("ROLE_ADMIN")) {
			return forbidden("管理者のみ指令を発行できます。");
		}
		log.info("指令発行リクエスト: リクエスト:({})", req);
		AdminApproveActionResponse res = approveService.createInstruction(current.userId, req);
		log.info("指令発行レスポンス: ユーザー:({}),レスポンス:({})", current, res);
		if ("200".equals(res.getResponseCode())) {
			Map<String, String> placeholders = new HashMap<String, String>();
			placeholders.put("SUBJECT_TAG_NAME", "発行されました。");
			placeholders.put("USER_NAME", String.valueOf(mailConfig.getSourceMailAddress()));
			placeholders.put("FILL_NAME", "発行");
			placeholders.put("TARGET_KIND_LABEL", ApproveFlowConstants.TYPE_INSTRUCTION);
			placeholders.put("TARGET_SUMMARY", "NONE");
			placeholders.put("TARGET_TITLE", ApproveFlowConstants.TYPE_INSTRUCTION);
			placeholders.put("TARGET_NAME", String.valueOf(current.userId));
			placeholders.put("REJECTED_AT", String.valueOf(res.getReturnDate()));
			placeholders.put("APPROVE_ID", res.getApproveId());
			placeholders.put("MIX_BUCKET", S3BucketConstant.S3_MAIL_INSTRUCTION);
			// 指令は宛先の担当者全員へ通知する（1通ずつ登録する）。
			if (res.getToMailAddressList() != null) {
				for (String toAddress : res.getToMailAddressList()) {
					notifyApproveFlow(toAddress, placeholders, S3BucketConstant.S3_MAIL_INSTRUCTION);
				}
			}
		}
		return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
	}

	/**
	 * 指令の一覧を取得する。
	 * 管理者: 自分が発行した指令＋宛先ごとの確認状況の内訳を返す。
	 * 担当者: 自分が宛先に含まれる指令＋自分の確認状況を返す。
	 */
	@GetMapping("/instructions")
	public ResponseEntity<AdminApproveListResponse> listInstructions(
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
		CurrentUser current = resolveCurrentUser(authorizationHeader);
		if (current == null) {
			return unauthorizedList();
		}
		AdminApproveListResponse res;
		if (current.roles.contains("ROLE_ADMIN")) {
			res = approveService.getInstructionsForAdmin(current.userId);
		} else if (current.roles.contains("ROLE_ADMIN_SUB")) {
			res = approveService.getInstructionsForRecipient(current.userId);
		} else {
			return forbiddenList("管理者または担当者のみ指令一覧を確認できます。");
		}
		return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
	}

	/** 担当者が指令を確認する */
	@PatchMapping("/instructions/{approveId}/confirm")
	public ResponseEntity<AdminApproveActionResponse> confirmInstruction(
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
			@PathVariable String approveId) {
		CurrentUser current = resolveCurrentUser(authorizationHeader);
		if (current == null) {
			return unauthorized();
		}
		if (!current.roles.contains("ROLE_ADMIN_SUB")) {
			return forbidden("担当者のみ指令を確認できます。");
		}
		AdminApproveActionResponse res = approveService.confirmInstruction(approveId, current.userId);
		return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
	}

	/** 管理者が自分の出した指令を差し戻す */
	@PatchMapping("/instructions/{approveId}/reject")
	public ResponseEntity<AdminApproveActionResponse> rejectInstruction(
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
			@PathVariable String approveId,
			@RequestBody ApproveActionRequest req) {
		CurrentUser current = resolveCurrentUser(authorizationHeader);
		if (current == null) {
			return unauthorized();
		}
		if (!current.roles.contains("ROLE_ADMIN")) {
			return forbidden("管理者のみ指令を差し戻せます。");
		}
		log.info("指令差し戻しリクエスト: リクエスト:({})", req);
		AdminApproveActionResponse res = approveService.rejectInstruction(approveId, current.userId, req.getComment());
		log.info("指令差し戻しレスポンス: ユーザー:({}),レスポンス:({})", current, res);
		if ("200".equals(res.getResponseCode())) {
			// 元の実装ではここに通知メール送信が無かったが、cancelInstructionと
			// 対称の挙動にするため追加している。宛先(担当者全員)へは
			// AdminApproveServiceが宛先一覧を返すようにしないと送れないため、
			// 現状は指令を発行した管理者自身への確認通知にとどめている。
			Map<String, String> placeholders = new HashMap<String, String>();
			placeholders.put("SUBJECT_TAG_NAME", "差し戻しされました。");
			placeholders.put("USER_NAME", String.valueOf(mailConfig.getSourceMailAddress()));
			placeholders.put("FILL_NAME", "差し戻し");
			placeholders.put("TARGET_KIND_LABEL", ApproveFlowConstants.INSTRUCTION_STATUS_REJECTED);
			placeholders.put("TARGET_SUMMARY", "NONE");
			placeholders.put("TARGET_TITLE", ApproveFlowConstants.INSTRUCTION_STATUS_REJECTED);
			placeholders.put("TARGET_NAME", String.valueOf(current.userId));
			placeholders.put("REJECTED_AT", String.valueOf(res.getReturnDate()));
			placeholders.put("APPROVE_ID", res.getApproveId());
			placeholders.put("REASON_SENTENCE", res.getComment());
			placeholders.put("MIX_BUCKET", S3BucketConstant.S3_MAIL_REJECT);
			notifyApproveFlow(current.email, placeholders, S3BucketConstant.S3_MAIL_REJECT);
		}
		return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
	}

	/** 管理者が自分の出した指令を取り消す */
	@PatchMapping("/instructions/{approveId}/cancel")
	public ResponseEntity<AdminApproveActionResponse> cancelInstruction(
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
			@PathVariable String approveId,
			@RequestBody ApproveActionRequest req) {
		CurrentUser current = resolveCurrentUser(authorizationHeader);
		if (current == null) {
			return unauthorized();
		}
		if (!current.roles.contains("ROLE_ADMIN")) {
			return forbidden("管理者のみ指令を取り消せます。");
		}
		log.info("指令取り消しリクエスト: リクエスト:({})", req);
		AdminApproveActionResponse res = approveService.cancelInstruction(approveId, current.userId, req.getComment());
		log.info("指令取り消しレスポンス: ユーザー:({}),レスポンス:({})", current, res);
		if ("200".equals(res.getResponseCode())) {
			Map<String, String> placeholders = new HashMap<String, String>();
			placeholders.put("SUBJECT_TAG_NAME", "取り消しされました。");
			placeholders.put("USER_NAME", String.valueOf(mailConfig.getSourceMailAddress()));
			placeholders.put("FILL_NAME", "取り消し");
			placeholders.put("TARGET_KIND_LABEL", ApproveFlowConstants.INSTRUCTION_STATUS_CANCELLED);
			placeholders.put("TARGET_SUMMARY", "NONE");
			placeholders.put("TARGET_TITLE", ApproveFlowConstants.INSTRUCTION_STATUS_CANCELLED);
			placeholders.put("TARGET_NAME", String.valueOf(current.userId));
			placeholders.put("REJECTED_AT", String.valueOf(res.getReturnDate()));
			placeholders.put("APPROVE_ID", res.getApproveId());
			placeholders.put("MIX_BUCKET", S3BucketConstant.S3_MAIL_CANCEL);
			notifyApproveFlow(current.email, placeholders, S3BucketConstant.S3_MAIL_CANCEL);
		}
		return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
	}

	// ------------------------------------------------------------
	// 共通処理
	// ------------------------------------------------------------

	/**
	 * 承認フロー（依頼/指令）の状態変化を知らせる通知メールを登録する。
	 *
	 * targetKindがNOTICE/SCREEN/MAIL_INFOのいずれであっても、常に同じ固定テンプレート
	 * （MailIdConstant.BM_MAIL_XXX。あらかじめmail_info_masterに登録済みであること）を使う。
	 * 承認対象そのもの（mailInfo.getMailId()など、まだマスタ未登録かもしれない値）を
	 * ここに使ってはいけない。
	 *
	 * メール送信登録はベストエフォートとして扱い、失敗しても承認フロー自体の処理結果
	 * （既にコミット済み）には影響させない。
	 */
	private void notifyApproveFlow(String toAddress, Map<String, String> placeholders, String s3BucketKind) {
		try {
			MailSendResponse response = mailSendService.sendSystemNotification(
					MailIdConstant.BM_MAIL_XXX, toAddress, placeholders, false);
			if (!"200".equals(response.getResponseCode())) {
				log.warn("承認フロー通知メールの登録に失敗しました。responseCode={}, message={}, toAddress={}",
						response.getResponseCode(), response.getMessage(), toAddress);
				return;
			}
			String mailSendKey = response.getMailSendKey();
			if (mailSendKey != null) {
				putMailNoticeJson.putJson(
						MailConvertS3BucketUtil.getS3Bucket(MailIdConstant.BM_MAIL_XXX, null, s3BucketKind)
								+ S3Const.JSON,
						mailSendKey);
			}
		} catch (Exception e) {
			log.error("承認フロー通知メールの送信登録中に例外が発生しました。toAddress={}", toAddress, e);
		}
	}

	/** Authorizationヘッダーからログイン中ユーザー（userId・roles）を解決する */
	private CurrentUser resolveCurrentUser(String authorizationHeader) {
		if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
			return null;
		}
		String token = authorizationHeader.substring("Bearer ".length()).trim();
		try {
			DecodedJWT decoded = jwtService.verifyToken(token);
			String email = decoded.getSubject();
			java.util.List<String> roles = decoded.getClaim("roles").asList(String.class);
			java.util.Optional<Long> userId = userRepository.findUserIdByEmail(email);
			if (userId.isEmpty() || roles == null) {
				return null;
			}
			return new CurrentUser(email, userId.get(), roles);
		} catch (Exception e) {
			log.warn("トークン検証に失敗しました: {}", e.getMessage());
			return null;
		}
	}

	private static class CurrentUser {
		final String email;
		final Long userId;
		final java.util.List<String> roles;

		CurrentUser(String email, Long userId, java.util.List<String> roles) {
			this.email = email;
			this.userId = userId;
			this.roles = roles;
		}
	}

	private ResponseEntity<AdminApproveActionResponse> unauthorized() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
				AdminApproveActionResponse.builder().responseCode("401").message("認証情報が不正です。").build());
	}

	private ResponseEntity<AdminApproveActionResponse> forbidden(String message) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
				AdminApproveActionResponse.builder().responseCode("403").message(message).build());
	}

	private ResponseEntity<AdminApproveListResponse> unauthorizedList() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
				AdminApproveListResponse.builder().responseCode("401").message("認証情報が不正です。").build());
	}

	private ResponseEntity<AdminApproveListResponse> forbiddenList(String message) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
				AdminApproveListResponse.builder().responseCode("403").message(message).build());
	}

	private static int parseStatus(String code) {
		try {
			int status = Integer.parseInt(code);
			return (status >= 100 && status <= 599) ? status : 500;
		} catch (Exception e) {
			return 500;
		}
	}
}