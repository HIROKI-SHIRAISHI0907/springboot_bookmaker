package dev.application.analyze.common.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.interf.SeasonResolverIF;
import dev.application.domain.repository.master.CountryLeagueSeasonMasterRepository;

/**
 * country_league_season_master から「現在のシーズン」を取得する {@link SeasonResolverIF} の実装。
 *
 * <h2>何を導出するクラスか</h2>
 * <p>
 * 国・リーグに対して、seq 採番に使うシーズン（season_year）を返す。
 * </p>
 * <ol>
 *   <li>今日がシーズン期間内（start_season_date〜end_season_date）のシーズン
 *       （{@link CountryLeagueSeasonMasterRepository#findSeasonYearInPeriod}）</li>
 *   <li>見つからなければ、今日までに開始した直近のシーズン
 *       （{@link CountryLeagueSeasonMasterRepository#findLatestStartedSeasonYear}）。
 *       シーズン終了後〜次シーズン開始前のオフ期間の救済。</li>
 *   <li>それもなければ、開始日・終了日が未登録の行も含めて season_year が最も新しいもの
 *       （{@link CountryLeagueSeasonMasterRepository#findLatestSeasonYear}）。</li>
 *   <li>どれもなければ例外（空のシーズンで採番しない）</li>
 * </ol>
 * <p>
 * season_year の値は "2026/2027"（秋春制）や "2026"（春秋制）。seq は "2026/2027-000001" のようになる。
 * </p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>「今日」基準である</b>: 試合日ではなく処理日でシーズンを決める。シーズン切替直後に
 *       前シーズンの試合を処理すると、新シーズンの番号が振られる
 *       （seq は行の識別子として使うだけなら問題ないが、seq でシーズンを判断する用途には使わないこと）。</li>
 *   <li><b>valid_flg では絞らない</b>: 実データでは主要リーグが valid_flg='1'、日付未登録のリーグが '0' のため、
 *       valid_flg の意味に関わらずシーズンを取れるよう del_flg='0'（未削除）だけで絞る。</li>
 *   <li><b>日付未登録のリーグ</b>: 3番目の救済で season_year の文字列順に最新を取る。
 *       形式が混在（"2026" と "2025/2026"）していても通常は正しく並ぶが、厳密な日付比較ではない。</li>
 *   <li><b>season_year の形式</b>: マスタの値をそのまま使う（前後の空白のみ除去）。
 *       seq_counter.season（VARCHAR(20)）に収まる長さであること。</li>
 *   <li><b>キャッシュなし</b>: 呼び出しごとに DB を引く。呼び出し側（Writer）で実行単位のキャッシュを持つこと。</li>
 * </ul>
 */
@Component
public class CountryLeagueSeasonResolver implements SeasonResolverIF {

	@Autowired
	private CountryLeagueSeasonMasterRepository countryLeagueSeasonMasterRepository;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String resolveSeason(String country, String league) {
		if (isBlank(country) || isBlank(league)) {
			throw new IllegalStateException("country/league が空です: country=" + country + ", league=" + league);
		}

		String season = this.countryLeagueSeasonMasterRepository.findSeasonYearInPeriod(country, league);
		if (isBlank(season)) {
			season = this.countryLeagueSeasonMasterRepository.findLatestStartedSeasonYear(country, league);
		}
		if (isBlank(season)) {
			season = this.countryLeagueSeasonMasterRepository.findLatestSeasonYear(country, league);
		}
		if (isBlank(season)) {
			throw new IllegalStateException(
					"country_league_season_master にシーズンがありません: country=" + country + ", league=" + league);
		}
		return season.trim();
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
