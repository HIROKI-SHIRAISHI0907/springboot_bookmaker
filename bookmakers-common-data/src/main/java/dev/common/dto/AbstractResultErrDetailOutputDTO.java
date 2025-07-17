package dev.common.dto;

import lombok.Data;

/**
 * エラー詳細抽象DTO
 * @author shiraishitoshio
 *
 */
@Data
public class AbstractResultErrDetailOutputDTO {

	/**
	 * 例外が起きたプロジェクト名
	 */
	private String exceptionProject;

	/**
	 * 例外が起きたクラス
	 */
	private String exceptionClass;

	/**
	 * 例外が起きたメソッド
	 */
	private String exceptionMethod;

	/**
	 * 例外内容
	 */
	private Throwable throwAble;

	/**
	 * エラーメッセージ
	 */
	private String errMessage;

}
