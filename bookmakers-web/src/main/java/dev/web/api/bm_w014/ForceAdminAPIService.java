package dev.web.api.bm_w014;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.master.CountryLeagueMasterWebRepository;
import dev.web.repository.master.CountryLeagueSeasonMasterWebRepository;
import lombok.RequiredArgsConstructor;

/**
 * ForceAdminAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class ForceAdminAPIService {

	private final CountryLeagueSeasonMasterWebRepository repoSeason;

    private final CountryLeagueMasterWebRepository repoMaster;

    /**
	 * 強制制御更新
	 */
	@Transactional
    public ForceAdminResponse upsert(String country, String league, String team, String delFlg) {
		ForceAdminResponse res = new ForceAdminResponse();
		int result1 = repoSeason.updateDelFlgOne(country, league, delFlg);
        if (result1 == 0) {
        	res.setResponseCode("9");
    	    res.setMessage("更新エラー");
    	    return res;
        }

        int result2 = repoMaster.updateDelFlgOne(country, league, team, delFlg);
        if (result2 == 0) {
        	res.setResponseCode("9");
    	    res.setMessage("更新エラー");
    	    return res;
        }
        res.setResponseCode("0");
	    res.setMessage("OK");
        return res;
    }
}
