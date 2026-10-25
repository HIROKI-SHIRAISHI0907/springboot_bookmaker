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

	/** グループの最初の記録日時（static_data.record_time。日本時間 "yyyy-MM-dd HH:mm:ss"）。手動取得（add_manual_flg = '1'）の行は除く */
	private String minRecordTime;

	/** グループの最後の記録日時（同上） */
	private String maxRecordTime;

	/** 手動取得（add_manual_flg = '1'）の行の最初の記録日時。取り直した日なので試合の日とは限らない（試合の日より後） */
	private String minManualRecordTime;

	/** 手動取得の行の最後の記録日時（同上） */
	private String maxManualRecordTime;

	/**
	 * country_league_season_master のシーズン期間に入っているか（期間内 / 期間外 / 期間不明）。
	 * 開始の1日前〜終了の7日後までを期間内とする（SeasonPeriodChecker）。
	 */
	private String seasonCheck;

	/** シーズン期間の判定の説明（例: 期間内（2026-02-14〜2026-12-06）、期間不明（シーズン未登録）） */
	private String seasonLabel;

}
