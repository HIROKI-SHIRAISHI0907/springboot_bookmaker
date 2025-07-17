package dev.application.analyze.bm_m098;

import lombok.Data;

/**
 * file_chk_tmpテーブルentity
 * @author shiraishitoshio
 *
 */
@Data
public class UpdFileDataInfoEntity {

	/** 国 */
	private String country;

	/** リーグ名 */
	private String league;

	/** ファイル名 */
	private String fileName;

	/** 更新前通番リスト */
	private String befSeqList;

	/** 更新後通番リスト */
	private String afSeqList;

	/** 更新前ハッシュ */
	private String befFileHash;

	/** 更新後ハッシュ */
	private String afFileHash;

	/** 登録ID */
	private String registerId;

	/** 登録時間 */
	private String registerTime;

	/** 更新ID */
	private String updateId;

	/** 更新時間 */
	private String updateTime;

}
