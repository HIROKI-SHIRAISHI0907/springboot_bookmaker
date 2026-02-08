package dev.web.api.bm_w015;

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
