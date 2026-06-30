package dev.web.api.bm_a022;

import lombok.Data;

/**
 * GeograficDTO
 * @author shiraishitoshio
 *
 */
@Data
public class GeograficDTO {

	/** ID */
	private Integer id;

	/** 国 */
	private String country;

	/** チーム */
	private String teamName;

	/** ホーム都市 */
	private String homeCity;

}
