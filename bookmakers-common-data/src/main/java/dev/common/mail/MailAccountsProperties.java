package dev.common.mail;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * fromAddress（送信元メールアドレス）ごとのSMTP認証情報。
 * application.yml側で以下のように定義する想定。
 *
 * mail:
 *   accounts:
 *     noreply:
 *       username: no-reply@example.com
 *       password: xxxxxxxxxxxxxxxx
 *     system:
 *       username: support@example.com
 *       password: yyyyyyyyyyyyyyyy
 */
@Component
@ConfigurationProperties(prefix = "mail")
public class MailAccountsProperties {

    private Map<String, Account> accounts = new LinkedHashMap<>();

    public Account require(String mail) {
    	Account cfg = mail == null ? null : accounts.get(mail);
        if (cfg == null) throw new IllegalArgumentException("Unknown mail: " + mail);
        return cfg;
    }

    public Map<String, Account> getAccounts() {
        return accounts;
    }

    public void setAccounts(Map<String, Account> accounts) {
        this.accounts = accounts;
    }

    @Data
    public static class Account {
        private String username;
        private String password;
    }
}