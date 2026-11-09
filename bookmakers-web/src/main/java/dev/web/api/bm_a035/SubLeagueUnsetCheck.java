package dev.web.api.bm_a035;

import org.springframework.stereotype.Component;

import dev.web.repository.master.SubLeagueSeasonRepository;
import lombok.RequiredArgsConstructor;

/**
 * サブリーグ保有の未設定（2026-11-09 追加）
 * <p>country_league_season_master の 国・リーグ のうち、サブリーグ保有国リーグ登録設定で
 * 「あり」「なし」を決めていないもの。system_view_master の check_code = SUB_LEAGUE_UNSET
 * （path = sub-league-season, block_flg = '1'）で、その画面以外（setup_flg = '1' 以外）を開けなくする。
 * 「設定しない」を押すと未設定が全部「サブリーグなし」になって解消する。</p>
 * @author shiraishitoshio
 */
@Component
@RequiredArgsConstructor
public class SubLeagueUnsetCheck implements SetupCheck {

	/** チェックのコード */
	public static final String CODE = "SUB_LEAGUE_UNSET";

	private final SubLeagueSeasonRepository repository;

	@Override
	public String code() {
		return CODE;
	}

	@Override
	public String name() {
		return "サブリーグ保有（あり・なし）の未設定";
	}

	@Override
	public SetupCheckResult check() {
		return SetupCheckResult.of(repository.findUnsetLabels());
	}
}
