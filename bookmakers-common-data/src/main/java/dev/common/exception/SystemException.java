package dev.common.exception;

import lombok.Getter;

/**
 * システム例外クラス
 * @author shiraishitoshio
 *
 */
@Getter
public class SystemException extends RuntimeException {

	/** エラープロジェクト名 */
	private String errProjectName;

	/** エラークラス名 */
	private String errClassName;

	/** エラーメソッド名 */
	private String errMethodName;

	/**
	 * 例外設定メソッド
	 * @param errProjectName エラープロジェクト名
	 * @param errClassName エラークラス名
	 * @param errMethodName エラーメソッド名
	 * @param message メッセージ
	 */
	public SystemException(String errProjectName, String errClassName, String errMethodName, String message) {
		super(message);
		this.errProjectName = errProjectName;
		this.errClassName = errClassName;
		this.errMethodName = errMethodName;
	}

	/**
	 * 例外設定メソッド
	 * @param errProjectName エラープロジェクト名
	 * @param errClassName エラークラス名
	 * @param errMethodName エラーメソッド名
	 * @param message メッセージ
	 * @param throwable 例外クラス
	 */
	public SystemException(String errProjectName, String errClassName, String errMethodName, String message, Throwable throwable) {
		super(message, throwable);
		this.errProjectName = errProjectName;
		this.errClassName = errClassName;
		this.errMethodName = errMethodName;
	}

}
