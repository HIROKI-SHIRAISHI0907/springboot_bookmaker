package dev.web.api.bm_a007;

/**
 * NotFoundExceptionクラス
 * @author shiraishitoshio
 *
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
