package dev.web.api.bm_u004;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import dev.common.entity.MailSendManagementEntity;
import dev.common.enums.MailNoticeEnum;
import dev.web.repository.bm.MailSendManagementRepository;
import dev.web.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String PASSWORD_RESET_MAIL_ID = "bm-mail-001";
    private static final long VALID_MINUTES = 10;

    /**
     * register_timeの補正時間（時間）。
     * DB（Postgres）はUTCでCURRENT_TIMESTAMPを書き込んでいるが、
     * JDBC側（JVMのデフォルトタイムゾーンがJST）で読み取ると、その
     * UTCの時刻文字列をJSTの時刻として解釈してしまうため、
     * 実際より9時間過去のInstantになる。
     * 正しい実時刻に戻すため、読み取ったregister_timeには+9時間する。
     */
    private static final long TIMEZONE_CORRECTION_HOURS = 9;

    private final MailSendManagementRepository mailSendManagementRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * リンクバリデーション
     * @param rawKey
     * @return
     */
    public AuthResponse validate(String rawKey) {
        AuthResponse res = new AuthResponse();
        if (findValidEntity(rawKey).isEmpty()) {
            res.setResponseCode("400");
            res.setMessage("リンクが無効です。もう一度パスワード再設定をお申し込みください。");
            return res;
        }
        res.setResponseCode("200");
        res.setMessage("有効なリンクです。");
        return res;
    }

    /**
     * パスワード入力確認
     * @param rawKey
     * @param newPassword
     * @return
     */
    public AuthResponse confirm(String rawKey, String newPassword) {
        AuthResponse res = new AuthResponse();

        if (newPassword == null || newPassword.length() < 8) {
            res.setResponseCode("400");
            res.setMessage("パスワードは8文字以上で入力してください。");
            return res;
        }

        Optional<MailSendManagementEntity> entityOpt = findValidEntity(rawKey);
        if (entityOpt.isEmpty()) {
            res.setResponseCode("400");
            res.setMessage("リンクが無効です。もう一度パスワード再設定をお申し込みください。");
            return res;
        }
        MailSendManagementEntity entity = entityOpt.get();

        // SENDED -> USED への遷移に成功した場合のみ後続処理を行う（二重クリック対策）
        int updated = mailSendManagementRepository.markSendedAsUsed(entity.getMailSendKey());
        if (updated != 1) {
            res.setResponseCode("409");
            res.setMessage("このリンクは既に使用されているか、無効になっています。");
            return res;
        }

        try {
            String passwordHash = passwordEncoder.encode(newPassword);
            int userUpdated = userRepository.updatePasswordByEmail(
                    entity.getToAddress(), passwordHash, "SYSTEM");
            if (userUpdated != 1) {
                log.error("パスワード更新対象のユーザーが見つかりません。email={}", entity.getToAddress());
                res.setResponseCode("500");
                res.setMessage("システムエラーが起きました。システム管理者に連絡してください。");
                return res;
            }
        } catch (Exception e) {
            log.error("パスワード更新に失敗しました。mailSendKey={}", entity.getMailSendKey(), e);
            res.setResponseCode("500");
            res.setMessage("システムエラーが起きました。システム管理者に連絡してください。");
            return res;
        }

        res.setResponseCode("200");
        res.setMessage("パスワードを再設定しました。");
        return res;
    }

    /**
     * 有効なEntityを取得する
     * @param rawKey
     * @return
     */
    private Optional<MailSendManagementEntity> findValidEntity(String rawKey) {
        String mailSendKey = decodeMailSendKey(rawKey);
        if (mailSendKey == null || mailSendKey.isBlank()) {
            return Optional.empty();
        }

        Optional<MailSendManagementEntity> entityOpt =
                mailSendManagementRepository.findByMailSendKey(mailSendKey);
        if (entityOpt.isEmpty()) {
            return Optional.empty();
        }

        MailSendManagementEntity entity = entityOpt.get();

        if (!PASSWORD_RESET_MAIL_ID.equals(entity.getMailId())) {
            return Optional.empty();
        }
        if (!MailNoticeEnum.NOTIFY_STATUS_SENDED.getNoticeStatus().equals(entity.getNotifyStatus())) {
            return Optional.empty();
        }
        if (isExpired(entity.getRegisterTime())) {
            // 発行から10分経過している（改ざん・使い回し対策）
        	// 使用済みとして更新する
        	try {
        		mailSendManagementRepository.markSendedAsUsed(entity.getMailSendKey());
        		log.info("{}分経過したリンクを踏まれたので、ステータスを「使用済み」にしました。"
        				+ " mailSendKey: {}", VALID_MINUTES, entity.getMailSendKey());
        	} catch (Exception e) {
        		log.error("{}分経過したリンクをステータスを「使用済み」に変換する際にエラーが発生しました。"
        				+ " mailSendKey: {}", VALID_MINUTES, entity.getMailSendKey());
        	}
            return Optional.empty();
        }
        return entityOpt;
    }

    /**
     * 期限チェック
     * @param registerTime
     * @return
     */
    private boolean isExpired(Timestamp registerTime) {
        if (registerTime == null) {
            return true;
        }
        Instant correctedRegisterTime = registerTime.toInstant().plusSeconds(TIMEZONE_CORRECTION_HOURS * 3600);
        Instant expiresAt = correctedRegisterTime.plusSeconds(VALID_MINUTES * 60);
        // Instant.now()はタイムゾーンに依存しない「真の現在時刻」なので補正不要
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * mailSendKeyのデコード
     */
    private String decodeMailSendKey(String rawKey) {
    	String encodedKey = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
        return encodedKey;
    }
}