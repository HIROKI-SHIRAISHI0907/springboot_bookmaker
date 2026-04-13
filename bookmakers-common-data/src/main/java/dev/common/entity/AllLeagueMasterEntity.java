package dev.common.entity;

import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AllLeagueMasterEntity
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class AllLeagueMasterEntity extends MetaEntity implements Serializable {

	/** ID */
	private String id;

	/** スクレイピング国 */
	private String country;

	/** スクレイピングリーグ */
	private String league;

	/** サブリーグ */
	private String subLeague;

	/** 論理フラグ(0: 最新の国リーグ情報, 1: 旧名の国リーグ情報) */
	private String logicFlg;

	/** 表示フラグ(0: スクレイピング対象, 1: スクレイピング非対象) */
	private String dispFlg;

}
