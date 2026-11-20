package dev.batch.bm_b096;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.batch.repository.bm.MailSendBatchRepository;
import dev.batch.repository.master.MailInfoMasterBatchRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.MailInfoMasterEntity;
import dev.common.entity.MailSendManagementEntity;
import dev.common.enums.BatchCodeToMailEnum;
import dev.common.enums.MailNoticeEnum;
import dev.common.enums.ScrapeCodeToMailEnum;
import dev.common.logger.ManageLoggerComponent;
import dev.common.mail.MailSendComponent;
import dev.common.mail.PutMailNoticeJson;
import lombok.extern.slf4j.Slf4j;

/**
 * MailLaunchServiceロジック
 * @author shiraishitoshio
 *
 */
@Component
@Slf4j
public class MailLaunchService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MailLaunchService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();
	/** クラス名 */
	private static final String CLASS_NAME = MailLaunchService.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "MAIL_LAUNCH";

	/** パスワード再設定URLのプレースホルダー */
	private static final String PASSWORD_RESET_URL_PLACEHOLDER = "{{PASSWORD_RESET_URL}}";

	/** バッチコードのプレースホルダー */
	private static final String BATCH_NAME_PLACEHOLDER = "BATCH_NAME";

	/** スクレイプコードのプレースホルダー */
	private static final String SCRAPE_NAME_PLACEHOLDER = "SCRAPE_NAME";

	/** バケットのプレースホルダー */
	private static final String MIX_BUCKET = "MIX_BUCKET";

	/**
	 * パスワード再設定画面のベースURL（例: https://bm-stats-real.com/reset-password）。
	 * 環境ごとにapplication.properties/application.ymlで切り替える想定。
	 */
	@Value("${app.password-reset.base-url}")
	private String passwordResetBaseUrl;

	@Autowired
	private MailSendComponent mailSendComponent;

	@Autowired
	private PutMailNoticeJson putMailNoticeJson;

	@Autowired
	private MailSendBatchRepository mailSendBatchRepository;

	@Autowired
	private MailInfoMasterBatchRepository mailInfoMasterBatchRepository;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * メール送信バッチ実行
	 */
	public void execute() throws Exception {
		final String METHOD_NAME = "execute";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		log.info("passwordResetBaseUrl: {}", passwordResetBaseUrl);

		// 現在メール送信管理に登録されている通知ステータスが0のもの and 送信失敗数が2以下のものを取得
		List<MailSendManagementEntity> noticeStatusPendingList = mailSendBatchRepository.findPendingNoticeStatus();

		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"メール送信管理: " + noticeStatusPendingList);

		// メール送信して、ステータスをupdate
		for (MailSendManagementEntity entity : noticeStatusPendingList) {
			String mailSendKey = entity.getMailSendKey();
			String mailId = entity.getMailId();
			String envelopeFrom = entity.getEnvelopeFrom();
			String toAddress = entity.getToAddress();
			String bucket = entity.getBucketInfo();
			int failSendCount = entity.getFailSendCount();
			String bikou = entity.getBikou();

			MailInfoMasterEntity mailIdKeyDTO = mailInfoMasterBatchRepository.findMailByMailIdInfo(mailId);
			log.info("mail master check, mailIdKeyDTO={}", mailIdKeyDTO);
			if (mailIdKeyDTO == null) {
				// 送信失敗数をインクリメントして更新
				mailSendBatchRepository.updateFailSendCount(mailSendKey, failSendCount + 1);
				continue;
			}

			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"メール送信キー: " + mailSendKey);

			BikouDTO bikouSubjectDTO = applyBikouPlaceholders(mailIdKeyDTO.getMailSubject(), bikou);
			String mailSubject = bikouSubjectDTO.getText();
			String mailBody = mailIdKeyDTO.getMailBody();
			String mixBucket = bikouSubjectDTO.getMixBucket();

			if (mailBody != null && mailBody.contains(PASSWORD_RESET_URL_PLACEHOLDER)) {
				String encodedKey = URLEncoder.encode(mailSendKey, StandardCharsets.UTF_8);
				String passwordResetUrl = passwordResetBaseUrl + "?key=" + encodedKey;
				mailBody = mailBody.replace(PASSWORD_RESET_URL_PLACEHOLDER, passwordResetUrl);
			}

			BikouDTO bikouBodyDTO = applyBikouPlaceholders(mailBody, bikou);
			mailBody = bikouBodyDTO.getText();
			if (mixBucket == null)
				mixBucket = bikouBodyDTO.getMixBucket();
			// 取得できるバッチスクレイピングコードは本文でも同一のため取得なし

			// メール送信（これが成功したら「送信できた」とみなす。S3の重複通知防止JSON更新は
			// あくまで補助的な処理であり、これが解決できないことを理由にメール送信自体を
			// スキップしてはいけない）
			log.info("mail send check, mailIdKeyDTO={},envelopeFrom={},toAddress={}"
					+ ",mailSubject={},mailBody={}", mailIdKeyDTO, envelopeFrom, toAddress
					, mailSubject, mailBody);
			try {
				mailSendComponent.send(mailIdKeyDTO.getFromAddress(), envelopeFrom, toAddress,
						mailSubject, mailBody);
			} catch (Exception e) {
				// 送信失敗数をインクリメントして更新
				mailSendBatchRepository.updateFailSendCount(mailSendKey, failSendCount + 1);
				continue;
			}

			// 通知ステータスを1に更新
			mailSendBatchRepository.updateFromPendingToSendedStatus(
					mailSendKey, MailNoticeEnum.NOTIFY_STATUS_SENDED.getNoticeStatus());

			// S3上の重複通知防止JSON更新はベストエフォート。
			// 承認フロー系の通知(bm-mail-xxx等)のように、1回きりのイベントで
			// そもそも重複防止JSONに対応するバケットを一意に特定できないメールIDもあるため、
			// ここで失敗してもメール送信自体は既に成功しているので処理を継続する。
			log.info("mail send json check, bucket={},mailSendKey={},", bucket, mailSendKey);
			try {
				if (bucket != null && !bucket.isBlank()) {
					putMailNoticeJson.updateNoticeCompleted(bucket, mailSendKey);
				} else {
					log.warn("メールIDからS3バケットを特定できなかったため、重複通知防止JSONの更新をスキップします。"
							+ "bucket={}, mailSendKey={}",
							bucket, mailSendKey);
				}
			} catch (Exception e) {
				log.warn("重複通知防止JSONの更新に失敗しましたが、メール送信自体は成功しているため処理を継続します。"
						+ "bucket={}, mailSendKey={}, e={}", bucket, mailSendKey, e);
			}

			// 少しスリープする
			try {
				Thread.sleep(100);
			} catch (Exception e) {
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME,
						METHOD_NAME, MessageCdConst.MCD00099I_LOG, e,
						"スレッドスリープでエラーが起こりました。: " + e);
			}
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();

	}

	/**
	 * mail_send_manage.bikouを "KEY1=VALUE1,KEY2=VALUE2" 形式のkey=valueペアとして解釈し、
	 * text中に含まれる "{{KEY1}}" のようなプレースホルダーをVALUE1に置換する。
	 * 件名・本文どちらに対しても同じロジックで使える汎用メソッド。
	 */
	private BikouDTO applyBikouPlaceholders(String text, String bikou) {
	    BikouDTO bikouDto = new BikouDTO();
	    if (text == null || bikou == null || bikou.isBlank()) {
	        bikouDto.setText(text);
	        bikouDto.setBatchScrapeCd(null);
	        return bikouDto;
	    }
	    String result = text;
	    for (String pair : bikou.split(",")) {
	        String[] kv = pair.split("=", 2);
	        if (kv.length != 2) {
	            continue;
	        }
	        String key = kv[0].trim();
	        String value = kv[1].trim();
	        if (key.isEmpty()) {
	            continue;
	        }
	        if (BATCH_NAME_PLACEHOLDER.equals(key))
	            bikouDto.setBatchScrapeCd(value);
	        value = BatchCodeToMailEnum.resolveBatchName(value);

	        if (SCRAPE_NAME_PLACEHOLDER.equals(key))
	            bikouDto.setBatchScrapeCd(value);
	        value = ScrapeCodeToMailEnum.resolveScrapeName(value);

	        if (MIX_BUCKET.equals(key))
	        	bikouDto.setMixBucket(value);

	        result = result.replace("（" + key + "）", value);
	        result = result.replace("{{" + key + "}}", value);
	    }

	    // bikouに対応するkeyがなかった、あるいは {{ key }} のように空白入りで
	    // 一致しなかった等で置換されずに残ったプレースホルダーを削除する
	    result = result.replaceAll("\\{\\{\\s*[^{}]*?\\s*\\}\\}", "");

	    bikouDto.setText(result);
	    return bikouDto;
	}
}