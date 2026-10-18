package dev.web.api.bm_a031;

/**
 * 未ログインのアップロード（誰がアップロードしたか残せないため受け付けない）
 *
 * @author shiraishitoshio
 */
public class UnauthorizedUploadException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public UnauthorizedUploadException(String message) {
		super(message);
	}
}
