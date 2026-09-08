package dev.web.exception;

/**
 * AWS呼び出し・入力チェックで発生した業務エラーをラップする例外。
 * メッセージには認証情報(アクセスキー/シークレットキー)を絶対に含めないこと。
 */
public class AwsCostException extends RuntimeException {

    private final int httpStatus;

    public AwsCostException(String message) {
        this(message, 400);
    }

    public AwsCostException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public AwsCostException(String message, Throwable cause, int httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
