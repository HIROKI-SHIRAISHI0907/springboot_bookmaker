package dev.common.mail;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * fromAddress（送信元メールアドレス）ごとのSMTP認証情報。
 * application.yml側で以下のように定義する想定。
 *
 * mail:
 *   accounts:
 *     "no-reply@example.com":
 *       username: no-reply@example.com
 *       password: xxxxxxxxxxxxxxxx
 *     "support@example.com":
 *       username: support@example.com
 *       password: yyyyyyyyyyyyyyyy
 */
@Component
@ConfigurationProperties(prefix = "mail")
public class MailAccountsProperties {

    private Map<String, Account> accounts = new LinkedHashMap<>();

    public Map<String, Account> getAccounts() {
        return accounts;
    }

    public void setAccounts(Map<String, Account> accounts) {
        this.accounts = accounts;
    }

    public static class Account {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}