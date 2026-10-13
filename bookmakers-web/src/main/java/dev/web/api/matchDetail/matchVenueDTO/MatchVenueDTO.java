package dev.web.api.matchDetail.matchVenueDTO;

import java.math.BigDecimal;

import lombok.Data;

/**
 * スタジアムの地理情報（team_location_master）
 * @author shiraishitoshio
 */
@Data
public class MatchVenueDTO {

	/** STADIUM（スタジアム名で一致） / HOME_TEAM（ホームチームのホームスタジアム） */
	private String matchedBy;

	/** マスタのスタジアム名（日本語） */
	private String stadiumName;

	/** 英語名（display_name_en → stadium_name_translate） */
	private String stadiumNameEn;

	/** ホームタウン（日本語） */
	private String homeCity;

	/** ホームタウン（英語） */
	private String homeCityEn;

	/** 国（日本語） */
	private String country;

	/** 住所（address → address_en） */
	private String address;

	private BigDecimal latitude;

	private BigDecimal longitude;

	/** Google の place_id */
	private String placeId;

	/** マスタのホームチーム（このスタジアムを使うチーム） */
	private String teamName;

	/** Google マップのリンク（座標が無ければ null） */
	private String mapUrl;
}
