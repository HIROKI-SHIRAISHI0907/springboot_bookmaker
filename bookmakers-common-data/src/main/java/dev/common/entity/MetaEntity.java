package dev.common.entity;

import dev.common.constant.MetaConst;
import dev.common.util.DateUtil;

/**
 * メタ情報のエンティティ
 * @author shiraishitoshio
 *
 */
public class MetaEntity {

	/**
	 * 登録IDを取得する
	 * @return
	 */
	public String getRegisterId() {
		return MetaConst.BM_USER;
	}

	/**
	 * 登録時間を取得する
	 * @return
	 */
	public String getRegisterTime() {
		return DateUtil.getSysDate();
	}

	/**
	 * 更新IDを取得する
	 * @return
	 */
	public String getUpdateId() {
		return MetaConst.BM_USER;
	}

	/**
	 * 更新時間を取得する
	 * @return
	 */
	public String getUpdateTime() {
		return DateUtil.getSysDate();
	}


}
