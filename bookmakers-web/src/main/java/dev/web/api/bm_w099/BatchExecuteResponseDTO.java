package dev.web.api.bm_w099;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BatchExecuteResponseDTO {

	/** バッチコード */
    private String batchCode;

    /** 結果コード */
    private int resultCode;      // BatchConstant.BATCH_SUCCESS / BATCH_ERROR

    /** 結果ラベル */
    private String resultLabel;  // "SUCCESS" / "ERROR"

    /** 開始日時 */
    private LocalDateTime startedAt;

    /** 終了日時 */
    private LocalDateTime finishedAt;

    /** メッセージ*/
    private String message;

}
