package dev.web.mail;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import dev.common.constant.MailIdConstant;
import dev.common.entity.MailInfoMasterEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * アプリが起動時に直接参照する固定メールテンプレート（mail_info_master）を、
 * 存在しなければ自動登録する。
 *
 * 承認フローの共通通知テンプレート(MailIdConstant.BM_MAIL_XXX)のように、
 * コードから固定IDで参照するテンプレートは、手動SQL投入に頼ると
 * 環境構築時に入れ忘れる／DBを作り直した時に消える、といった事故が起きやすい。
 * ここに追加しておけば、どの環境でもアプリを起動するだけで自動的に用意される
 * （既に存在する場合は何もしない、何度起動しても安全）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SystemMailTemplateSeeder implements ApplicationRunner {

	private final MailSendService mailSendService;

	@Override
	public void run(ApplicationArguments args) {
		for (MailInfoMasterEntity template : systemTemplates()) {
			seedIfAbsent(template);
		}
	}

	private void seedIfAbsent(MailInfoMasterEntity template) {
		List<MailInfoMasterEntity> existing = mailSendService.getMailMaster();
		if (existing == null) {
			log.warn("メール情報マスタの取得に失敗したため、システム用テンプレートの自動登録確認をスキップします。mailId={}",
					template.getMailId());
			return;
		}
		boolean alreadyExists = existing.stream()
				.anyMatch(m -> template.getMailId().equals(m.getMailId()));
		if (alreadyExists) {
			return;
		}

		MailSendResponse result = mailSendService.regMailMaster(template);
		if ("200".equals(result.getResponseCode())) {
			log.info("システム用メールテンプレートを新規登録しました。mailId={}", template.getMailId());
		} else {
			log.warn("システム用メールテンプレートの登録に失敗しました。mailId={}, responseCode={}, message={}",
					template.getMailId(), result.getResponseCode(), result.getMessage());
		}
	}

	private List<MailInfoMasterEntity> systemTemplates() {
		MailInfoMasterEntity approveFlowNotice = new MailInfoMasterEntity();
		approveFlowNotice.setMailId(MailIdConstant.BM_MAIL_XXX);
		approveFlowNotice.setMailSubject("【bm-stats-real】ご依頼が{{SUBJECT_TAG_NAME}}");
		approveFlowNotice.setMailBody(
				"{{USER_NAME}} 様\n\n"
						+ "いつもBookmakersをご利用いただきありがとうございます。\n\n"
						+ "以下のご依頼が{{FILL_NAME}}されましたのでお知らせいたします。\n"
						+ "対象種別　：{{TARGET_KIND_LABEL}}\n"
						+ "内容　：{{TARGET_SUMMARY}}\n"
						+ "{{TARGET_TITLE}}者：{{TARGET_NAME}}\n"
						+ "{{TARGET_TITLE}}日時：{{REJECTED_AT}}\n"
						+ "受付番号　：{{APPROVE_ID}}\n"
						+ "{{REASON_SENTENCE}}\n\n"
						+ "内容をご確認ください。\n"
						+ "--------------------------------------------------\n"
						+ "Bookmakers 管理システム\n"
						+ "--------------------------------------------------");
		approveFlowNotice.setFromAddress("hirokishiraishi73@gmail.com");
		return List.of(approveFlowNotice);
	}
}