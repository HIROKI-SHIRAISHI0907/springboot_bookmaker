package dev.web.api.bm_a037;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * サブリーグ保有国リーグ登録設定: 国・リーグ 1 件
 * @author shiraishitoshio
 */
@Data
public class SubLeagueSeasonDTO {

	/** 未設定 */
	public static final String STATUS_UNSET = "UNSET";

	/** サブリーグあり */
	public static final String STATUS_HAS = "HAS";

	/** サブリーグなし */
	public static final String STATUS_NONE = "NONE";

	private String country;

	private String league;

	/** UNSET / HAS / NONE */
	private String status;

	/** サブリーグ名（表示順。HAS のときだけ） */
	private List<String> subLeagues = new ArrayList<>();

	/** 日本時間 "yyyy-MM-dd HH:mm:ss"（未設定は null） */
	private String updateTime;

	private String updateId;
}
