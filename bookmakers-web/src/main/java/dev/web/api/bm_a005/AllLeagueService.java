package dev.web.api.bm_a005;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.api.dashboard.support.LeagueVisibility;
import dev.web.repository.master.AllLeagueMasterWebRepository;
import lombok.RequiredArgsConstructor;

/**
 * AllLeagueService用サービス
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class AllLeagueService {

	private final AllLeagueMasterWebRepository repo;

	/** 対象外にしたリーグをすぐユーザー画面から外すため（2026-11-23 追加） */
	private final LeagueVisibility leagueVisibility;

	/**
	 * 全選択
	 */
	@Transactional(readOnly = true)
	public List<AllLeagueDTO> findAll() {
		List<AllLeagueDTO> result = repo.findAll();
		return result;
	}

	/**
	 * 更新
	 */
	@Transactional
	public AllLeagueResponse upsert(String country, String league, String logicFlg, String dispFlg) {
		AllLeagueResponse res = new AllLeagueResponse();
		int result = repo.update(country, league, logicFlg, dispFlg);
		leagueVisibility.invalidate();
		if (result == 0) {
			res.setResponseCode("9");
			res.setMessage("更新エラー");
			return res;
		}
		res.setResponseCode("0");
		res.setMessage("OK");
		return res;
	}

}
