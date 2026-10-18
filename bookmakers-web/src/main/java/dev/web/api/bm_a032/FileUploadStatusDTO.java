package dev.web.api.bm_a032;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dev.web.api.bm_a031.CsvUploadRowError;
import lombok.Data;

/**
 * ファイルアップロード状況（file_upload_status_manage の 1 行）
 *
 * @author shiraishitoshio
 */
@Data
public class FileUploadStatusDTO {

	private Long id;

	/** 種類（season / team / future / realtime） */
	private String masterName;

	private String fileName;

	private String csvFileName;

	private Long fileSize;

	/** 状態（PENDING 判定前 / PROCESSING 判定中 / SUCCESS 正常終了 / ERROR 異常終了） */
	private String status;

	/** エラーの段階（VALIDATION / STORE / DB。異常終了のときだけ） */
	private String errorPhase;

	private String message;

	private int totalCount;

	private int insertCount;

	private int updateCount;

	private int deleteCount;

	private int hideCount;

	private int showCount;

	private int errorCount;

	/** アップロードしたログインユーザー（メールアドレス） */
	private String loginUser;

	/** 受付日時（日本時間 yyyy-MM-dd HH:mm:ss） */
	private String startedAt;

	/** 終了日時（日本時間。処理中は null） */
	private String finishedAt;

	/** 処理時間（秒。処理中は null） */
	private Long elapsedSec;

	/** 置いた S3 バケット（置いていなければ null） */
	private String s3Bucket;

	/** 置いた S3 キー（uploaded/… ・ success/… ・ error/…） */
	private String s3Key;

	/** エラー行（詳細だけ。一覧では空） */
	private List<CsvUploadRowError> errors = new ArrayList<>();

	/** DB の error_detail（JSON）。画面には返さない */
	@JsonIgnore
	private String errorDetailJson;
}
