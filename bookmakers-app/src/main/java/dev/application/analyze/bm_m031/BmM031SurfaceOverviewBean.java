package dev.application.analyze.bm_m031;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.map.HashedMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.constant.ValidFlgConst;
import dev.application.domain.repository.master.CountryLeagueSeasonMasterRepository;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * surface_overviewのbeanロジック
 * @author shiraishitoshio
 *
 */
@Component
public class BmM031SurfaceOverviewBean {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = BmM031SurfaceOverviewBean.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = BmM031SurfaceOverviewBean.class.getSimpleName();

	/** イタリアセリエA */
	private static final String ITALY_SERIEA = "イタリア／セリエ A";

	/** イングランド／プレミアリーグ */
	private static final String ENGLAND_PREMIER = "イングランド／プレミアリーグ";

	/** CountryLeagueSeasonMasterRepositoryレポジトリクラス */
	@Autowired
	private CountryLeagueSeasonMasterRepository countryLeagueSeasonMasterRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/** リスト */
	private List<String> countryLeagueList;

	/** マップ */
	private Map<String, Integer> countryLeagueRoundMap;

	/** 初期化
	 * @throws Exception */
	public void init() throws Exception {
		final String METHOD_NAME = "init";

		// 初期化
		countryLeagueList = new ArrayList<>();
		countryLeagueRoundMap = new HashedMap<String, Integer>();

		// valid_flg = 0 の国・リーグを取得して "国: リーグ" 形式で格納
		String key = "";
		try {
			List<CountryLeagueSeasonMasterEntity> rows = this.countryLeagueSeasonMasterRepository
					.findRoundValidFlg(ValidFlgConst.VALID_FLG_0);
			if (rows != null) {
				for (CountryLeagueSeasonMasterEntity e : rows) {
					String country = e.getCountry();
					String league = e.getLeague();
					league = getRegexLeague(league);
					if (country != null && league != null) {
						key = country.trim() + ": " + league.trim();
						countryLeagueList.add(key);
						countryLeagueRoundMap.put(key, Integer.parseInt(e.getRound().trim()));
					}
				}
			}
		} catch (Exception e) {
			String messageCd = "initエラー";
			this.loggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, key);
			this.loggerComponent.createBusinessException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					null,
					null);
		}
	}

	/**
	 * リーグ名を正規化する
	 * @param league
	 * @return
	 */
	private String getRegexLeague(String league) {
		if (ITALY_SERIEA.equals(league)) {
			return "セリエA";
		} else if (ENGLAND_PREMIER.equals(league)) {
			return "プレミアリーグ";
		} else if (league.contains("-")) {
			String[] sp = league.split("-");
			return sp[0];
		}
		return league;
	}

	/** 他のクラスから参照したい場合のゲッター */
	public List<String> getCountryLeagueList() {
		return countryLeagueList;
	}

	/** 他のクラスから参照したい場合のゲッター */
	public Map<String, Integer> getCountryLeagueRoundMap() {
		return countryLeagueRoundMap;
	}

}
