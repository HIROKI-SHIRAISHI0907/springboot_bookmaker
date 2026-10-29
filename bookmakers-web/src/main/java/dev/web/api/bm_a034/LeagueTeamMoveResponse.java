package dev.web.api.bm_a034;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * リーグ所属チームの付け替え結果
 * @author shiraishitoshio
 */
@Data
public class LeagueTeamMoveResponse {
	/** "0" = 成功 / "9" = エラー */
	private String responseCode;
	private String message;
	private int movedCount;
	/** 移動先に同じチームがいたため付け替えなかったチーム */
	private List<String> skippedTeams = new ArrayList<>();
}
