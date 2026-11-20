package dev.batch.repository.bm;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * メール送信管理(mail_send_manage) 削除用repository
 *
 * mail_send_manage は Web 側が直接 SMTP 送信した際の「送信ログ」兼
 * 「パスワード再設定リンクのキー保管」として使う。B095 で古いものを掃除する。
 */
@Mapper
public interface MailSendBatchRepository {

	/**
	 * 登録から指定日数を経過したメール送信管理データを削除する
	 * （パスワード再設定リンクの有効期限は10分なので、1日以上前のものは不要）。
	 *
	 * @param keepDays 保持日数
	 * @return 削除件数
	 */
	@Delete("""
			 DELETE
				FROM mail_send_manage
			 WHERE
			 	register_time < CURRENT_TIMESTAMP - make_interval(days => #{keepDays})
			""")
	int deleteData(@Param("keepDays") int keepDays);

}
