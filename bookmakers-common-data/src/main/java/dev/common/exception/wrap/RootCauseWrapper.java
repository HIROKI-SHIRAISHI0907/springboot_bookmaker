package dev.common.exception.wrap;

import org.springframework.stereotype.Component;

import dev.common.logger.ManageLoggerComponent;

/**
 * 例外クラスマッパー
 * @author shiraishitoshio
 *
 */
@Component
public class RootCauseWrapper {

    private final ManageLoggerComponent manageLoggerComponent;

    public RootCauseWrapper(ManageLoggerComponent manageLoggerComponent) {
        this.manageLoggerComponent = manageLoggerComponent;
    }

    /** Throwable を Exception に揃える（非 Exception はラップ） */
    private static Exception toException(Throwable t) {
        if (t == null) return null;                 // null 許容（ログ側が null OK の前提）
        return (t instanceof Exception) ? (Exception) t : new Exception(t);
    }

    /**
     * cause を伴って SystemException を発生させるユーティリティ。
     * - 先に debugErrorLog を呼ぶ（Exception 型で渡す）
     * - その後 createSystemException(...) を呼ぶ（通常ここで throw されて戻らない想定）
     * - 万一戻ってきたらフォールバックで RuntimeException を throw
     */
    public void throwSystem(
            String project, String clazz, String method,
            String messageCd, Throwable cause, String... fillChar) {

        Exception exForLog = toException(cause);

        // 1) 業務ログ（必要に応じて error ログ API に変更）
        this.manageLoggerComponent.debugErrorLog(project, clazz, method, messageCd, exForLog, fillChar);

        // 2) 管理用の SystemException を作成/送出（戻らない想定）
        //    ※ createSystemException(...) が void でも、戻ってきたらフォールバックへ
        this.manageLoggerComponent.createSystemException(project, clazz, method, messageCd, cause, exForLog);

        // 3) フォールバック（念のため必ず例外を投げる）
        throw new RuntimeException(messageCd, exForLog);
    }

    /** cause なし版（必要なら fillChar を渡せる） */
    public void throwSystem(
            String project, String clazz, String method,
            String messageCd, String... fillChar) {
        throwSystem(project, clazz, method, messageCd, null, fillChar);
    }

    /**
     * 件数不一致を「疑似 cause」として投げる便利メソッド
     * @param project
     * @param clazz
     * @param method
     * @param messageCd
     * @param expected
     * @param actual
     * @param context
     * @param fillChar
     */
    public void throwUnexpectedRowCount(
            String project, String clazz, String method,
            String messageCd, int expected, int actual, String context, String... fillChar) {

        IllegalStateException pseudoCause = new IllegalStateException(
                String.format("Unexpected row count (expected=%d, actual=%d). context=[%s]",
                        expected, actual, context)
        );
        throwSystem(project, clazz, method, messageCd, pseudoCause, fillChar);
    }
}
