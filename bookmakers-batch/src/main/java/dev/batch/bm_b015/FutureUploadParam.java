package dev.batch.bm_b015;

import lombok.Data;

/**
 * future_master の登録・更新の値
 * <p>更新では null の項目は変更しない（CSV で空欄の列）。日時は「yyyy-MM-ddTHH:mm:ss+09:00」。
 *    数値の列は既存の登録（FutureMasterRepository）と同じく文字のまま渡す。</p>
 * @author shiraishitoshio
 */
@Data
public class FutureUploadParam {
	/** future_master.seq */
	private Long seq;
	private String gameTeamCategory;
	private String futureTime;
	private String homeRank;
	private String awayRank;
	private String homeTeamName;
	private String awayTeamName;
	private String homeMaxGettingScorer;
	private String awayMaxGettingScorer;
	private String homeTeamHomeScore;
	private String homeTeamHomeLost;
	private String awayTeamHomeScore;
	private String awayTeamHomeLost;
	private String homeTeamAwayScore;
	private String homeTeamAwayLost;
	private String awayTeamAwayScore;
	private String awayTeamAwayLost;
	private String gameLink;
	private String dataTime;
	/** register_id / update_id */
	private String user;
}
