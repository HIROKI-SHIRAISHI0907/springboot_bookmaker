package dev.web.api.bm_a006;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.api.bm_a003.CountryLeagueDTO;
import dev.web.api.dashboard.auth.DashboardAuthResolver;
import dev.web.repository.master.AdminFavoriteRepository;
import lombok.RequiredArgsConstructor;

/**
 * ForceAdminAPI用サービス（お気に入り国リーグ情報全体反映設定）
 *
 * <p>2026-10-30: 非表示の設定を admin_favorites_master に保存するよう変更。
 * 以前は country_league_master / country_league_season_master の del_flg を書き換えていたため、
 * チームを1つ非表示にするとシーズンマスタのリーグごと非表示になり、バッチや他の画面にも影響していた。
 * マスタの del_flg はここでは触らない。</p>
 *
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class ForceAdminAPIService {

	/** level: チーム（favorites と同じ） */
	private static final String LEVEL_TEAM = "3";
	/** level: リーグ */
	private static final String LEVEL_LEAGUE = "2";
	/** level: 国 */
	private static final String LEVEL_COUNTRY = "1";

	private final AdminFavoriteRepository adminFavoriteRepository;

	private final DashboardAuthResolver authResolver;

	/**
	 * 画面の一覧（country_league_master の全チーム。delFlg は「非表示か」）
	 * GET /api/admin/force/teams
	 */
	@Transactional(readOnly = true)
	public List<CountryLeagueDTO> findTeams() {
		return adminFavoriteRepository.findTeamsWithHidden();
	}

	/**
	 * 強制制御更新（delFlg: "1" = 非表示 / "0" = 表示）
	 * チームが空ならリーグ単位、リーグも空なら国単位で登録する。
	 */
	@Transactional
	public ForceAdminResponse upsert(String country, String league, String team, String delFlg) {
		ForceAdminResponse res = new ForceAdminResponse();

		if (isBlank(country) || !("0".equals(delFlg) || "1".equals(delFlg))) {
			res.setResponseCode("9");
			res.setMessage("国・表示区分を指定してください");
			return res;
		}

		String level = !isBlank(team) ? LEVEL_TEAM : !isBlank(league) ? LEVEL_LEAGUE : LEVEL_COUNTRY;
		String operator = authResolver.currentUserId();

		int n = adminFavoriteRepository.upsert(level, country, league, team, delFlg,
				isBlank(operator) ? "ADMIN" : operator);
		if (n == 0) {
			res.setResponseCode("9");
			res.setMessage("更新エラー");
			return res;
		}

		res.setResponseCode("0");
		res.setMessage("OK");
		return res;
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
