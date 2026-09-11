package dev.common.util;

import java.util.UUID;

public class ProcessKeyUtil {

	/**
	 * メール送信キーを定義する
	 *
	 */
	public static String getMailSendKey() {
		return UUID.randomUUID().toString();
	}

}
