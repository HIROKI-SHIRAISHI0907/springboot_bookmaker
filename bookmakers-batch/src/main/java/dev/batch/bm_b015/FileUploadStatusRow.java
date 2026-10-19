package dev.batch.bm_b015;

import lombok.Data;

/**
 * file_upload_status_manage の 1 行（B015 で使う列だけ）
 * @author shiraishitoshio
 */
@Data
public class FileUploadStatusRow {

	/** ID（S3 のキーのフォルダにも使われている） */
	private Long id;

	/** 種類（season / team / future / realtime） */
	private String masterName;

	/** アップロードした zip の名前 */
	private String fileName;

	/** zip の中の CSV の名前 */
	private String csvFileName;

	/** 状態 */
	private String status;

	/** CSV の行数（web で数えたもの） */
	private Integer totalCount;

	/** アップロードしたログインユーザー */
	private String loginUser;

	/** 置いてある S3 バケット */
	private String s3Bucket;

	/** 置いてある S3 キー（uploaded/…） */
	private String s3Key;
}
