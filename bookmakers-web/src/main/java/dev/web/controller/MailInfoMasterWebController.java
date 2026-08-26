package dev.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.common.entity.MailInfoMasterEntity;
import dev.web.api.bm_a026.MailInfoMasterRequest;
import dev.web.mail.MailSendResponse;
import dev.web.mail.MailSendService;
import lombok.RequiredArgsConstructor;

/**
 * メール情報マスタコントローラー
 * @author shiraishitoshio
 *
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MailInfoMasterWebController {

	private final MailSendService service;

	/**
	 * メール情報マスタにデータを登録する。
	 *
	 * PATCH /api/mailinfo
	 */
	@PatchMapping("/mailinfo")
	public ResponseEntity<MailSendResponse> insert(
			@RequestBody MailInfoMasterRequest req) {

		MailInfoMasterEntity entity = new MailInfoMasterEntity();
		entity.setMailId(req.getMailId());
		entity.setMailSubject(req.getMailSubject());
		entity.setMailBody(req.getMailBody());
		entity.setFromAddress(req.getFromAddress());
		MailSendResponse res = service.regMailMaster(entity);

		HttpStatus status = switch (res.getResponseCode()) {
		case "200" -> HttpStatus.OK; // SUCCESS
		case "400" -> HttpStatus.BAD_REQUEST; // 必須不足
		case "404" -> HttpStatus.NOT_FOUND; // NOT_FOUND
		case "409" -> HttpStatus.CONFLICT; // LINK_ALREADY_USED
		default -> HttpStatus.INTERNAL_SERVER_ERROR; // ERROR
		};

		return ResponseEntity.status(status).body(res);
	}

	/**
	* メール情報マスタのデータを更新する。
	* PATCH /api/mailinfo/update
	*/
	@PatchMapping("/mailinfo/update")
	public ResponseEntity<MailSendResponse> update(
			@RequestBody MailInfoMasterRequest req) {

		MailInfoMasterEntity entity = new MailInfoMasterEntity();
		entity.setMailId(req.getMailId());
		entity.setMailSubject(req.getMailSubject());
		entity.setMailBody(req.getMailBody());
		entity.setFromAddress(req.getFromAddress());
		MailSendResponse res = service.updMailMaster(entity);

		HttpStatus status = switch (res.getResponseCode()) {
		case "200" -> HttpStatus.OK; // SUCCESS
		case "400" -> HttpStatus.BAD_REQUEST; // 必須不足
		case "404" -> HttpStatus.NOT_FOUND; // NOT_FOUND
		case "409" -> HttpStatus.CONFLICT; // LINK_ALREADY_USED
		default -> HttpStatus.INTERNAL_SERVER_ERROR; // ERROR
		};

		return ResponseEntity.status(status).body(res);
	}

	/**
	 * メール情報マスタにデータを取得する。
	 *
	 * GET /api/mailinfo
	 */
	@GetMapping("/mailinfo")
	public ResponseEntity<List<MailInfoMasterEntity>> getMailMaster() {
		return ResponseEntity.ok(service.getMailMaster());
	}

	/**
	 * メール情報マスタにデータを1件取得する。
	 *
	 * GET /api/mailinfo/{mailId}
	 */
	@GetMapping("/mailinfo/{mailId}")
	public ResponseEntity<MailInfoMasterEntity> getMailMasterByMailId(
			@PathVariable String mailId) {
		return ResponseEntity.ok(service.getMailMasterByMailId(mailId));
	}
}
