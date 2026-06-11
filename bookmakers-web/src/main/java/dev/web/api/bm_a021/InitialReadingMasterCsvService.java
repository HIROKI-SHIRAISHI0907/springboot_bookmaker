package dev.web.api.bm_a021;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.common.constant.MasterNameConstant;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.entity.InitialReadingMasterCsvEntity;
import dev.web.api.bm_a002.CountryLeagueSeasonDTO;
import dev.web.api.bm_a002.CountryLeagueSeasonSearchCondition;
import dev.web.api.bm_a003.CountryLeagueDTO;
import dev.web.api.bm_a003.CountryLeagueSearchCondition;
import dev.web.repository.master.CountryLeagueMasterWebRepository;
import dev.web.repository.master.CountryLeagueSeasonMasterWebRepository;
import dev.web.repository.master.InitialReadingMasterCsvRepository;

/**
 * マスタ登録CSV初回読み込み確認サービス
 */
@Service
@Transactional(readOnly = true)
public class InitialReadingMasterCsvService {

	@Autowired
	private InitialReadingMasterCsvRepository initialReadingMasterCsvRepository;

	@Autowired
	private CountryLeagueSeasonMasterWebRepository countryLeagueSeasonMasterWebRepository;

	@Autowired
	private CountryLeagueMasterWebRepository countryLeagueMasterWebRepository;

	/**
	 * 指定マスタの初回読み込み状態を返却
	 *
	 * 判定ルール:
	 * - initial_reading_csv_master に initial_flg='0' のデータが存在する場合:
	 *     → 未初回読み込み
	 * - 存在しない場合:
	 *     → 初回読み込み済み
	 */
	public InitialReadingMasterCsvResponse getStatus(String masterName,
			String country, String league) {

		List<InitialReadingMasterCsvEntity> list = this.initialReadingMasterCsvRepository.findData(masterName);

		InitialReadingMasterCsvResponse response = new InitialReadingMasterCsvResponse();
		// 設定
		setViewData(response, masterName, list);
		return response;
	}

	/**
	 * テーブル名によっての設定
	 * @param response
	 * @param masterName
	 * @param list
	 */
	private void setViewData(InitialReadingMasterCsvResponse response,
			String masterName, List<InitialReadingMasterCsvEntity> list) {
		for (InitialReadingMasterCsvEntity entity : list) {
			switch (masterName) {
			case MasterNameConstant.COUNTRY_LEAGUE_SEASON_MASTER: {
				// countryLeagueSeasonMasterWebRepositoryの取得
				CountryLeagueSeasonSearchCondition searchCondition = new CountryLeagueSeasonSearchCondition();
				searchCondition.setCountry(entity.getCountry());
				searchCondition.setLeague(entity.getLeague());
				List<CountryLeagueSeasonDTO> seasonDTOs = this.countryLeagueSeasonMasterWebRepository
						.search(searchCondition);
				List<CountryLeagueSeasonMasterEntity> lists = new ArrayList<CountryLeagueSeasonMasterEntity>();
				for (CountryLeagueSeasonDTO dto : seasonDTOs) {
					CountryLeagueSeasonMasterEntity seasonMasterEntity = new CountryLeagueSeasonMasterEntity();
					seasonMasterEntity.setCountry(dto.getCountry());
					seasonMasterEntity.setLeague(dto.getLeague());
					seasonMasterEntity.setStartSeasonDate(dto.getStartSeasonDate());
					seasonMasterEntity.setEndSeasonDate(dto.getEndSeasonDate());
					seasonMasterEntity.setSeasonYear(dto.getSeasonYear());
					seasonMasterEntity.setRound(dto.getRound());
					seasonMasterEntity.setIcon(dto.getIcon());
					seasonMasterEntity.setPath(dto.getPath());
					lists.add(seasonMasterEntity);
				}
				response.setCountryLeagueSeasonMasterEntityList(lists);
				response.setMasterName(MasterNameConstant.COUNTRY_LEAGUE_SEASON_MASTER);
				break;
			}
			case MasterNameConstant.COUNTRY_LEAGUE_MASTER: {
				CountryLeagueSearchCondition searchCondition = new CountryLeagueSearchCondition();
				searchCondition.setCountry(entity.getCountry());
				searchCondition.setLeague(entity.getLeague());
				List<CountryLeagueDTO> leagueDTOs = this.countryLeagueMasterWebRepository.search(searchCondition);
				List<CountryLeagueMasterEntity> lists = new ArrayList<CountryLeagueMasterEntity>();
				for (CountryLeagueDTO dto : leagueDTOs) {
					CountryLeagueMasterEntity masterEntity = new CountryLeagueMasterEntity();
					masterEntity.setCountry(dto.getCountry());
					masterEntity.setLeague(dto.getLeague());
					masterEntity.setTeam(dto.getTeam());
					masterEntity.setLink(dto.getLink());
					lists.add(masterEntity);
				}
				response.setCountryLeagueMasterEntityList(lists);
				response.setMasterName(MasterNameConstant.COUNTRY_LEAGUE_MASTER);
				break;
			}
			default:
				throw new IllegalArgumentException("Unexpected value: " + masterName);
			}
		}
	}
}
