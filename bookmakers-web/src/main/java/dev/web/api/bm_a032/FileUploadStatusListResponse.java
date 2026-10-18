package dev.web.api.bm_a032;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * ファイルアップロード状況の一覧
 *
 * @author shiraishitoshio
 */
@Data
public class FileUploadStatusListResponse {

	/** 1 始まり */
	private int page;

	private int size;

	/** 条件に合う件数 */
	private int total;

	/** 状態ごとの件数（状態の条件は外して数える） */
	private List<FileUploadStatusSummaryDTO> summary = new ArrayList<>();

	private List<FileUploadStatusDTO> items = new ArrayList<>();
}
