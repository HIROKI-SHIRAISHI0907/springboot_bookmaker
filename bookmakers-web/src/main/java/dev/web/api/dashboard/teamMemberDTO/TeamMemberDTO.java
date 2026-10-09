package dev.web.api.dashboard.teamMemberDTO;

import lombok.Data;

/**
 * チームメンバー（画面用）
 * @author shiraishitoshio
 */
@Data
public class TeamMemberDTO {

	/** 背番号（不明は null） */
	private Integer jersey;

	/** 名前 */
	private String name;

	/** GK / DF / MF / FW / OTHER */
	private String positionGroup;

	/** 元のポジション名（例: ミッドフィルダー） */
	private String position;

	private Integer age;

	/** 身長（cm。不明は null） */
	private Integer height;

	/** 市場価値（例: €136k。不明は null） */
	private String marketValue;

	/** 負傷内容（無ければ null） */
	private String injury;

	/** レンタル元（レンタルでなければ null） */
	private String loanFrom;

	/** 顔写真 URL */
	private String facePicPath;
}
