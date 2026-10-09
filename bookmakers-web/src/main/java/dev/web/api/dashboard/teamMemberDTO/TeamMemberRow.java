package dev.web.api.dashboard.teamMemberDTO;

import lombok.Data;

/**
 * team_member_master の1行（DB の値そのまま）
 * @author shiraishitoshio
 */
@Data
public class TeamMemberRow {

	private String jersey;

	private String member;

	private String position;

	private String age;

	private String height;

	private String marketValue;

	private String injury;

	private String loanBelong;

	private String facePicPath;

	private String latestInfoDate;
}
