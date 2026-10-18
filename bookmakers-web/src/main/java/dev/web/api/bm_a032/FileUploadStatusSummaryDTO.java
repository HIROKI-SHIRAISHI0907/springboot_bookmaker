package dev.web.api.bm_a032;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 状態ごとの件数
 *
 * @author shiraishitoshio
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadStatusSummaryDTO {

	private String status;

	private int count;
}
