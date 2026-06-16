package dev.web.api.bm_a021;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
public class InitialReadingMasterCsvService {

	@Autowired
	private InitialReadingMasterCsvRepository initialReadingMasterCsvRepository;

	@Autowired
	private CountryLeagueSeasonMasterWebRepository countryLeagueSeasonMasterWebRepository;

	@Autowired
	private CountryLeagueMasterWebRepository countryLeagueMasterWebRepository;

	/**
	 * 指定マスタの初回読み込み状態を返却
	 */
	@Transactional(readOnly = true)
	public InitialReadingMasterCsvResponse getStatus(String masterName) {

		List<InitialReadingMasterCsvEntity> list =
				this.initialReadingMasterCsvRepository.findData(masterName);

		InitialReadingMasterCsvResponse response = new InitialReadingMasterCsvResponse();
		setViewData(response, masterName, list);
		return response;
	}

	/**
	 * モーダルで確認した対象の initial_flg を一括更新
	 * initial_flg = '0' → '1'
	 */
	@Transactional
	public InitialReadingMasterCsvUpdateResponse updateStatus(
			InitialReadingMasterCsvUpdateRequest request) {

		InitialReadingMasterCsvUpdateResponse response = new InitialReadingMasterCsvUpdateResponse();

		if (request == null || request.getMasterName() == null || request.getMasterName().isBlank()) {
			response.setMessage("masterName が不正です。");
			response.setUpdateCount(0);
			return response;
		}

		if (request.getTargets() == null || request.getTargets().isEmpty()) {
			response.setMessage("更新対象がありません。");
			response.setUpdateCount(0);
			return response;
		}

		// country + league で重複除去
		Map<String, InitialReadingMasterCsvUpdateTargetRequest> uniqueTargetMap = new LinkedHashMap<>();
		for (InitialReadingMasterCsvUpdateTargetRequest target : request.getTargets()) {
			if (target == null) {
				continue;
			}

			String country = target.getCountry();
			String league = target.getLeague();

			if (country == null || country.isBlank() || league == null || league.isBlank()) {
				continue;
			}

			String key = country + "___" + league;
			uniqueTargetMap.put(key, target);
		}

		int updateCount = 0;
		List<InitialReadingMasterCsvUpdateTargetRequest> updatedTargets = new ArrayList<>();

		for (InitialReadingMasterCsvUpdateTargetRequest target : uniqueTargetMap.values()) {
			int result = this.initialReadingMasterCsvRepository.updateInitialFlg(
					request.getMasterName(),
					target.getCountry(),
					target.getLeague());

			if (result > 0) {
				updateCount += result;
				updatedTargets.add(target);
			}
		}

		response.setUpdateCount(updateCount);
		response.setUpdatedTargets(updatedTargets);
		response.setMessage(updateCount == 0 ? "処理失敗しました。" : "処理成功しました。");

		return response;
	}

	/**
	 * テーブル名によっての設定
	 */
	private void setViewData(
			InitialReadingMasterCsvResponse response,
			String masterName,
			List<InitialReadingMasterCsvEntity> list) {

		for (InitialReadingMasterCsvEntity entity : list) {
			switch (masterName) {
			case MasterNameConstant.COUNTRY_LEAGUE_SEASON_MASTER: {
				CountryLeagueSeasonSearchCondition searchCondition = new CountryLeagueSeasonSearchCondition();
				searchCondition.setCountry(entity.getCountry());
				searchCondition.setLeague(entity.getLeague());
				searchCondition.setDelFlg("0");

				List<CountryLeagueSeasonDTO> seasonDTOs =
						this.countryLeagueSeasonMasterWebRepository.search(searchCondition);

				List<CountryLeagueSeasonMasterEntity> lists =
						new ArrayList<CountryLeagueSeasonMasterEntity>();

				for (CountryLeagueSeasonDTO dto : seasonDTOs) {
					CountryLeagueSeasonMasterEntity seasonMasterEntity =
							new CountryLeagueSeasonMasterEntity();
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
				searchCondition.setDelFlg("0");

				List<CountryLeagueDTO> leagueDTOs =
						this.countryLeagueMasterWebRepository.search(searchCondition);

				List<CountryLeagueMasterEntity> lists =
						new ArrayList<CountryLeagueMasterEntity>();

				for (CountryLeagueDTO dto : leagueDTOs) {
					CountryLeagueMasterEntity masterEntity =
							new CountryLeagueMasterEntity();
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
