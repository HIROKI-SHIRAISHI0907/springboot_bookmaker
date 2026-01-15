package dev.web.exception;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * APIハンドラ用サブクラス
 * @author shiraishitoshio
 *
 */
@Data
@AllArgsConstructor
public class ApiError {

	/** 実行タイムスタンプ */
    private LocalDateTime timestamp;

    /** ステータス */
    private int status;

    /** エラーメッセージ形式文 */
    private String error;

    /** メッセージ */
    private String message;

    /** APIパス */
    private String path;

}
