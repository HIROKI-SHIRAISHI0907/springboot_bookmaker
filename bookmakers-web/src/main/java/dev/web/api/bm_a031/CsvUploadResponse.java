package dev.web.api.bm_a031;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * CSV アップロードの結果（受付の結果）
 *
 * <ul>
 *   <li>バリデーションを通ったとき: status = PENDING（判定前）。DB への反映はバッチサービスで行う。</li>
 *   <li>バリデーションで引っかかったとき: status = ERROR（異常終了）。errors に行ごとのエラー。
 *       エラー内容を書き足した zip（_error.zip）を error フォルダに置き、状況管理画面からダウンロードできる。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Data
public class CsvUploadResponse {

	/** 対象（season / team / future / realtime） */
	private String masterName;

	/** 状況管理の ID（file_upload_status_manage.id） */
	private Long uploadId;

	/** 状態（{@link CsvUploadStatus}: PENDING / ERROR） */
	private String status;

	/** エラーの段階（VALIDATION / STORE。エラーでなければ null） */
	private String errorPhase;

	/** メッセージ */
	private String message;

	/** アップロードしたファイル名 */
	private String fileName;

	/** CSV の行数（見出し・空行を除く。読めなかったときは 0） */
	private int totalCount;

	/** 行エラー（ファイル全体のエラーは rowNo = 0） */
	private List<CsvUploadRowError> errors = new ArrayList<>();

	/** 置いた S3 バケット */
	private String s3Bucket;

	/** 置いた S3 キー（uploaded/… または error/…） */
	private String s3Key;

	public CsvUploadResponse(String masterName) {
		this.masterName = masterName;
	}

	public boolean hasError() {
		return !errors.isEmpty();
	}
}
