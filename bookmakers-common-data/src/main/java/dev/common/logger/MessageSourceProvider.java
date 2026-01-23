package dev.common.logger;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.stereotype.Component;

/**
 * ロガークラス
 * @author shiraishitoshio
 *
 */
@Component
public class MessageSourceProvider implements MessageSourceAware {

    private static MessageSource messageSource;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMessageSource(MessageSource source) {
        MessageSourceProvider.messageSource = source;
    }

    public static String getMessage(String code, Object[] args) {
        try {
            return messageSource.getMessage(code, args, Locale.JAPAN);
        } catch (Exception e) {
            // 取得できなかった場合はコードそのまま返す（ログが壊れないように）
            return code;
        }
    }
}
