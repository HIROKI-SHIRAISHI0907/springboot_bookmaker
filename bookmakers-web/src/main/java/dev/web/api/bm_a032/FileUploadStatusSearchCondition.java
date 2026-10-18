package dev.web.api.bm_a032;

import lombok.Data;

/**
 * ファイルアップロード状況の検索条件（入力があるものだけ使う）
 *
 * @author shiraishitoshio
 */
@Data
public class FileUploadStatusSearchCondition {

	/** 種類（season / team / future / realtime） */
	private String masterName;

	/** 状態（PENDING / PROCESSING / SUCCESS / ERROR） */
	private String status;

	/** 受付日（から。yyyy-MM-dd・日本時間） */
	private String dateFrom;

	/** 受付日（まで。yyyy-MM-dd・日本時間。その日を含む） */
	private String dateTo;

	/** ファイル名（部分一致） */
	private String fileName;

	/** ログインユーザー（部分一致） */
	private String loginUser;

	/** 状態の条件を外した写し（状態ごとの件数を出すため） */
	public FileUploadStatusSearchCondition copyWithoutStatus() {
		FileUploadStatusSearchCondition c = new FileUploadStatusSearchCondition();
		c.setMasterName(masterName);
		c.setDateFrom(dateFrom);
		c.setDateTo(dateTo);
		c.setFileName(fileName);
		c.setLoginUser(loginUser);
		return c;
	}
}
