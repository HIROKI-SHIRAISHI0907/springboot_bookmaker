package dev.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * メールConfig
 * @author shiraishitoshio
 *
 */
@Configuration
@Data
public class MailConfig {

	/** システム通知（ECS稼働開始/終了、シーズン終了間近など）の送信元兼送り先アドレス */
	@Value("${mail.accounts.system.username}")
	private String sourceMailAddress;

}
