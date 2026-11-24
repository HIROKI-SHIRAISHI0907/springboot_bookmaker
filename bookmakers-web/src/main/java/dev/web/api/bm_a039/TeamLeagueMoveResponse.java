package dev.web.api.bm_a039;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 確定・取り消しの結果
 * @author shiraishitoshio
 */
@Data
public class TeamLeagueMoveResponse {

	/** 確定・取り消しにした件数 */
	private int updated;

	/** お気に入りを付け替えた件数 */
	private int favorites;

	/** 大会出場チームを付け替えた件数 */
	private int cupTeams;

	/** 処理した後の一覧（未確定だけ、または全件） */
	private List<TeamLeagueMoveDTO> items = new ArrayList<>();

	/** 確定済み・見つからないなどで処理しなかった id のメッセージ */
	private List<String> warnings = new ArrayList<>();
}
