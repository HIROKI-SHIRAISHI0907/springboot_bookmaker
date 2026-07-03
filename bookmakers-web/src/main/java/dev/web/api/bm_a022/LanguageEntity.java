package dev.web.api.bm_a022;

import lombok.Data;

/**
 * LanguageEntity
 * @author shiraishitoshio
 *
 */
@Data
public class LanguageEntity {

	/** ID */
	private Integer id;

	/** 国 */
	private String country;

	/** 言語（日本語） */
	private String lang;

	/** 言語コード */
	private String langCd;

}
