package dev.web.api.bm_a025;

import java.util.List;

import lombok.Data;

/**
 * リアルタイムデータ（static_data）の dataCategory 確認用DTO
 * （dataCategory × home × away の1グループ）
 * @author shiraishitoshio
 *
 */
@Data
public class RealTimeDataDTO {

	/** データカテゴリ */
	private String dataCategory;

	/** ホームチーム名 */
	private String homeTeamName;

	/** アウェーチーム名 */
	private String awayTeamName;

	/** 3パターンのいずれかに一致した場合の dataCategory 値。一致しない場合は null */
	private String formattedDataCategory;

	/** home/away の組み合わせ単位での「形式」の揃い具合（"同一カテゴリ名" / "混在"） */
	private String categoryFormatIcon;

	/** グルーピング件数 */
	private Long cnt;

	/**
	 * home/away の組み合わせ単位での「国・リーグ」の判定（country_league_master と照合）
	 * <ul>
	 *   <li>一致: 候補が1つに決まり、今の dataCategory の国・リーグがそれと同じ</li>
	 *   <li>不一致: 候補が1つに決まるが、今の dataCategory の国・リーグが違う（例: エクアドルの試合がチリになっている）</li>
	 *   <li>混在: 同名チームなどで候補が複数あり、1つに決められない</li>
	 *   <li>不明: マスタにどちらのチームも無い</li>
	 * </ul>
	 */
	private String leagueCheck;

	/** 「国: リーグ」の候補（一致・不一致は1件、混在は2件以上） */
	private List<String> leagueCandidates;

}
