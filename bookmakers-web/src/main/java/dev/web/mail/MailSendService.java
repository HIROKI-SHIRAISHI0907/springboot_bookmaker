// dev/web/mail/MailSendService.java
package dev.web.mail;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import dev.common.entity.MailInfoMasterEntity;
import dev.common.entity.MailSendManagementEntity;
import dev.common.enums.MailNoticeEnum;
import dev.web.jwt.JwtCurrentUserService;
import dev.web.jwt.JwtCurrentUserService.CurrentUser;
import dev.web.repository.bm.MailSendManagementRepository;
import dev.web.repository.master.MailInfoMasterRepository;
import dev.web.repository.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MailSendServiceクラス
 *
 * メールID（mail_info_master.mail_id）をキーに件名・本文を取得し、
 * メール送信管理テーブル（mail_send_management）へ送信予定として登録する。
 *
 * 実際のSMTP送信（Transport.send）は別バッチで行う想定のため、
 * このクラスは「送信内容の確定 ＋ 送信管理テーブルへのinsert」までを担う。
 *
 * @author shiraishitoshio
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailSendService {

    /** メール情報マスタが見つからない等、システム都合で送信できない場合のメッセージ */
    private static final String SYSTEM_ERROR_MESSAGE = "システムエラーが起きました。システム管理者に連絡してください。";

    private static final String DUPLICATE_MESSAGE = "登録されているメールIDです。";

    private static final String ENVELOPE_ADDRESS = "no-reply@sample.com";

    private final MailInfoMasterRepository mailInfoMasterRepository;
    private final MailSendManagementRepository mailSendManagementRepository;
    private final UserRepository userRepository;
    private final JwtCurrentUserService jwtCurrentUserService;

    /**
     * ログイン中ユーザー宛にメール送信を登録する。
     * 送信先メールアドレスは、現在のHTTPリクエストのAuthorizationヘッダーをJWTとして
     * 検証し、JwtCurrentUserService経由で解決する。
     *
     * @param mailId メール情報マスタのメールID
     * @return 発行したメール送信キー（mail_send_management.mail_send_key）
     */
    public MailSendResponse send(String mailId) {
        String toAddress = resolveCurrentUserEmail();
        return send(mailId, toAddress);
    }

    /**
     * 送信先メールアドレスを直接指定してメール送信を登録する。
     * パスワード再発行など、未ログイン状態で画面から入力されたメールアドレスを使うケースで使用する。
     * 送信先は必ずusersテーブルに登録済みのメールアドレスである必要がある
     * （ユーザー向けの通知のため。管理者への固定アドレス通知等はsendSystemNotification()を使う）。
     *
     * @param mailId    メール情報マスタのメールID
     * @param toAddress 送信先メールアドレス（画面入力値など、呼び出し元で確定済みのもの）
     * @return 発行したメール送信キー（mail_send_management.mail_send_key）
     */
    public MailSendResponse send(String mailId, String toAddress) {
        // メール情報マスタに存在するか
        MailInfoMasterEntity mailInfo = mailInfoMasterRepository.findById(mailId)
                .orElseThrow(() -> {
                    log.error("メール情報マスタに該当データがありません。mailId={}", mailId);
                    return new RuntimeException(SYSTEM_ERROR_MESSAGE);
                });

        MailSendResponse response = new MailSendResponse();

        // 送信先メールアドレスが登録されているものか
        if (userRepository.findEmail(toAddress) == 0) {
            response.setResponseCode("404");
            response.setMessage("使用されていないメールアドレスです。");
            return response;
        }

        return insertManagement(mailInfo, toAddress, null);
    }

    /**
     * システム通知メール（バッチ・ECSタスク完了通知など）を、送信先を固定アドレスとして登録する。
     * send()と違い、送信先がusersテーブルに登録済みかどうかのチェックは行わない
     * （管理者への通知アドレスは、必ずしもログインユーザーのメールアドレスとは限らないため）。
     *
     * placeholdersはmail_send_manage.bikouに"KEY=VALUE,KEY=VALUE"の形式で保存され、
     * バッチ側（MailLaunchService.applyBikouPlaceholders）でメール件名・本文中の
     * {{KEY}}プレースホルダーの置換に使われる。
     *
     * ※値の中にカンマ（,）や等号（=）を含めないこと。含まれているとbikouのパースが
     *   壊れて他のキーの置換もできなくなる（例: エラーメッセージ全文などの自由文はNG）。
     *
     * @param mailId       メール情報マスタのメールID
     * @param toAddress    送信先メールアドレス（管理者通知用の固定アドレス等）
     * @param placeholders 件名・本文中の{{KEY}}を置換するためのkey-valueペア（無ければnullでよい）
     * @return 発行したメール送信キー（mail_send_management.mail_send_key）
     */
    public MailSendResponse sendSystemNotification(String mailId, String toAddress, Map<String, String> placeholders) {
        MailInfoMasterEntity mailInfo = mailInfoMasterRepository.findById(mailId)
                .orElseThrow(() -> {
                    log.error("メール情報マスタに該当データがありません。mailId={}", mailId);
                    return new RuntimeException(SYSTEM_ERROR_MESSAGE);
                });

        return insertManagement(mailInfo, toAddress, toBikou(placeholders));
    }

    /**
     * メール送信管理テーブルへ、送信予定として1件登録する共通処理。
     * send() / sendSystemNotification() のどちらからも呼ばれる。
     *
     * @param mailInfo  メール情報マスタのEntity（件名・本文・送信元アドレスの取得元）
     * @param toAddress 送信先メールアドレス
     * @param bikou     mail_send_manage.bikouに保存する値（プレースホルダー置換用。無ければnull）
     * @return 発行したメール送信キー（mail_send_management.mail_send_key）
     */
    private MailSendResponse insertManagement(MailInfoMasterEntity mailInfo, String toAddress, String bikou) {
        MailSendResponse response = new MailSendResponse();

        // メール送信キーを取得
        String mailSendKey = getMailSendKey();

        MailSendManagementEntity management = new MailSendManagementEntity();
        management.setMailSendKey(mailSendKey);
        management.setMessageId(null); // 実際の送信は別バッチのため、message_idはその際に更新する想定
        management.setToAddress(toAddress);
        management.setMailId(mailInfo.getMailId());
        management.setEnvelopeFrom(ENVELOPE_ADDRESS);
        management.setNotifyStatus(MailNoticeEnum.NOTIFY_STATUS_PENDING.getNoticeStatus());
        management.setFailSendCount(0);
        management.setBikou(bikou);

        try {
            int result = mailSendManagementRepository.insert(management);
            if (result != 1) {
                response.setResponseCode("500");
                response.setMessage("メール送信管理に登録できませんでした。");
                return response;
            }
        } catch (Exception e) {
            response.setResponseCode("500");
            response.setMessage(SYSTEM_ERROR_MESSAGE);
            return response;
        }

        response.setResponseCode("200");
        response.setMessage("処理が成功しました。");
        response.setMailSendKey(mailSendKey);
        return response;
    }

    /**
     * KEY-VALUEのMapを、mail_send_manage.bikouに保存する"KEY1=VALUE1,KEY2=VALUE2"形式に変換する。
     *
     * @param placeholders 件名・本文中の{{KEY}}を置換するためのkey-valueペア
     * @return "KEY1=VALUE1,KEY2=VALUE2"形式の文字列。placeholdersがnull/空の場合はnull
     */
    private String toBikou(Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return null;
        }
        return placeholders.entrySet().stream()
                .filter(e -> e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null)
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(","));
    }

    /**
     * メール情報マスタにデータを登録する。
     * メール送信管理のテーブルのメールIDに対応するマスタ情報である
     *
     * @param mailInfoMasterEntity メール情報マスタのEntity
     * @return 登録結果
     */
    public MailSendResponse regMailMaster(MailInfoMasterEntity mailInfoMasterEntity) {
        MailSendResponse response = new MailSendResponse();

        if (!mailInfoMasterRepository.findById(mailInfoMasterEntity.getMailId()).isEmpty()) {
            response.setResponseCode("404");
            response.setMessage(DUPLICATE_MESSAGE);
            return response;
        }

        try {
            int result = mailInfoMasterRepository.insert(mailInfoMasterEntity);
            if (result != 1) {
                response.setResponseCode("9");
                response.setMessage("メール送信管理に登録できませんでした。");
                return response;
            }
        } catch (Exception e) {
            response.setResponseCode("500");
            response.setMessage(SYSTEM_ERROR_MESSAGE);
            return response;
        }

        response.setResponseCode("200");
        response.setMessage("登録成功しました。");
        return response;
    }

    /**
     * メール情報マスタにデータを更新する。
     *
     * @param mailInfoMasterEntity メール情報マスタのEntity
     * @return 更新結果
     */
    public MailSendResponse updMailMaster(MailInfoMasterEntity mailInfoMasterEntity) {
        MailSendResponse response = new MailSendResponse();

        try {
            int result = mailInfoMasterRepository.update(mailInfoMasterEntity);
            if (result != 1) {
                response.setResponseCode("500");
                response.setMessage("メール送信管理を更新できませんでした。");
                return response;
            }
        } catch (Exception e) {
            response.setResponseCode("500");
            response.setMessage(SYSTEM_ERROR_MESSAGE);
            return response;
        }

        response.setResponseCode("200");
        response.setMessage("更新成功しました。");
        return response;
    }

    /**
     * メール情報マスタデータを全件取得する。
     * メール送信管理のテーブルのメールIDに対応するマスタ情報である
     *
     * @return メール情報マスタの全件リスト。取得失敗時はnull
     */
    public List<MailInfoMasterEntity> getMailMaster() {
        try {
            return mailInfoMasterRepository.findAll();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * メール情報マスタデータを1件取得する。
     *
     * @param mailId メールID
     * @return メール情報マスタのEntity
     */
    public MailInfoMasterEntity getMailMasterByMailId(String mailId) {
        if (mailId == null || mailId.isEmpty()) {
            log.error("mailIdがnullです。");
            new RuntimeException(SYSTEM_ERROR_MESSAGE);
        }

        MailInfoMasterEntity mailInfo = mailInfoMasterRepository.findById(mailId)
                .orElseThrow(() -> {
                    log.error("メール情報マスタに該当データがありません。mailId={}", mailId);
                    return new RuntimeException(SYSTEM_ERROR_MESSAGE);
                });
        return mailInfo;
    }

    /**
     * メール送信キーを発行する。
     * すでに登録済のキーであれば再帰的に取得する。
     *
     * @return 未使用のメール送信キー
     */
    private String getMailSendKey() {
        String mailSendKey = UUID.randomUUID().toString();
        return (!mailSendManagementRepository.findByMailSendKey(mailSendKey).isEmpty())
                ? getMailSendKey() : mailSendKey;
    }

    /**
     * 現在のHTTPリクエストのAuthorizationヘッダーからJWTを検証し、
     * ログイン中ユーザーのメールアドレスを取得する。
     *
     * JWTが無効・未ログインの場合はJwtCurrentUserService側でResponseStatusException
     * （401 Unauthorized）が投げられるので、ここでは特にcatchしていません。
     *
     * @return ログイン中ユーザーのメールアドレス
     */
    private String resolveCurrentUserEmail() {
        HttpServletRequest request = currentHttpRequest();
        String authorizationHeader = request.getHeader("Authorization");
        CurrentUser currentUser = jwtCurrentUserService.resolve(authorizationHeader);
        return currentUser.getEmail();
    }

    /**
     * 現在のHTTPリクエストを取得する。
     * HTTPリクエストコンテキスト外から呼び出された場合はRuntimeExceptionを投げる。
     *
     * @return 現在のHttpServletRequest
     */
    private HttpServletRequest currentHttpRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            log.error("HTTPリクエストコンテキスト外からsend(mailId)が呼び出されました。");
            throw new RuntimeException(SYSTEM_ERROR_MESSAGE);
        }
        ServletRequestAttributes servletAttributes = (ServletRequestAttributes) attributes;
        return servletAttributes.getRequest();
    }
}