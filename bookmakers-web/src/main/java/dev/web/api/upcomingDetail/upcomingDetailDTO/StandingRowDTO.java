package dev.web.api.upcomingDetail.upcomingDetailDTO;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 順位表の 1 行（surface_overview_match のそのシーズン・リーグの集計）
 * <p>rank は 勝ち点 → 得失点差 → 得点 の順（同じなら同順位）。siteRank はそのチームの最後の試合のサイト表示の順位。</p>
 * @author shiraishitoshio
 */
@Data
public class StandingRowDTO {

	/** 順位（計算） */
	private int rank;

	/** サイト表示の順位（最後の試合の順位。取れなければ null） */
	private Integer siteRank;

	private String team;

	private int games;

	private int win;

	private int draw;

	private int lose;

	private int points;

	private int goalsFor;

	private int goalsAgainst;

	private int goalDiff;

	/** 直近 5 試合の結果 W / D / L（左が新しい） */
	private List<String> form = new ArrayList<>();

	/** この試合のホームなら H、アウェーなら A、それ以外は null */
	private String side;
}
