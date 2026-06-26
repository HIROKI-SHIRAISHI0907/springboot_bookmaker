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

		InitialReadingMasterCsvResponse response = new InitialReadingMasterCsvResponse();

		if (!hasText(masterName)) {
			response.setMasterName(masterName);
			response.setCountryLeagueSeasonMasterEntityList(new ArrayList<>());
			response.setCountryLeagueMasterEntityList(new ArrayList<>());
			return response;
		}

		List<InitialReadingMasterCsvEntity> list = this.initialReadingMasterCsvRepository.findData(masterName);
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

		if (request == null || !hasText(request.getMasterName())) {
			response.setMessage("masterName が不正です。");
			response.setUpdateCount(0);
			response.setUpdatedTargets(new ArrayList<>());
			return response;
		}

		if (request.getTargets() == null || request.getTargets().isEmpty()) {
			response.setMessage("更新対象がありません。");
			response.setUpdateCount(0);
			response.setUpdatedTargets(new ArrayList<>());
			return response;
		}

		// country + league で重複除去
		Map<String, InitialReadingMasterCsvUpdateStatusTargetRequest> uniqueTargetMap = new LinkedHashMap<>();
		for (InitialReadingMasterCsvUpdateStatusTargetRequest target : request.getTargets()) {
			if (target == null) {
				continue;
			}

			String country = target.getCountry();
			String league = target.getLeague();

			if (!hasText(country) || !hasText(league)) {
				continue;
			}

			String key = country + "___" + league;
			uniqueTargetMap.put(key, target);
		}

		int updateCount = 0;
		List<InitialReadingMasterCsvUpdateStatusTargetRequest> updatedTargets = new ArrayList<>();

		for (InitialReadingMasterCsvUpdateStatusTargetRequest target : uniqueTargetMap.values()) {
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
	 * モーダルで確認した対象の レコード を一括更新
	 */
	@Transactional
	public InitialReadingMasterCsvUpdateResponse updateRow(
			InitialReadingMasterCsvUpdateTargetRequest request) {

		InitialReadingMasterCsvUpdateResponse response = new InitialReadingMasterCsvUpdateResponse();
		List<InitialReadingMasterCsvUpdateStatusTargetRequest> updatedTargets = new ArrayList<>();

		if (request == null || !hasText(request.getMasterName())) {
			response.setMessage("マスタ名がありません。");
			response.setUpdateCount(0);
			response.setUpdatedTargets(updatedTargets);
			return response;
		}

		if ((request.getMasterEntities() == null || request.getMasterEntities().isEmpty())
				&& (request.getSeasonMasterEntities() == null || request.getSeasonMasterEntities().isEmpty())) {
			response.setMessage("更新対象がありません。");
			response.setUpdateCount(0);
			response.setUpdatedTargets(updatedTargets);
			return response;
		}

		int updateCount = 0;

		if (MasterNameConstant.COUNTRY_LEAGUE_SEASON_MASTER.equals(request.getMasterName())) {
			for (CountryLeagueSeasonMasterEntity target : request.getSeasonMasterEntities()) {
				if (target == null) {
					continue;
				}

				Integer id = target.getId();
				String country = target.getCountry();
				String league = target.getLeague();

				if (id == null || !hasText(country) || !hasText(league)) {
					continue;
				}

				int result = this.countryLeagueSeasonMasterWebRepository.updateRow(
						id,
						target.getCountry(),
						target.getLeague(),
						target.getSeasonYear(),
						target.getStartSeasonDate(),
						target.getEndSeasonDate(),
						target.getRound());

				updateCount += result;

				if (result > 0) {
					InitialReadingMasterCsvUpdateStatusTargetRequest updated = new InitialReadingMasterCsvUpdateStatusTargetRequest();
					updated.setCountry(target.getCountry());
					updated.setLeague(target.getLeague());
					updatedTargets.add(updated);
				}
			}
		}

		if (MasterNameConstant.COUNTRY_LEAGUE_MASTER.equals(request.getMasterName())) {
			for (CountryLeagueMasterEntity target : request.getMasterEntities()) {
				if (target == null) {
					continue;
				}

				Integer id = parseIntegerOrNull(target.getId());
				String country = target.getCountry();
				String league = target.getLeague();

				if (id == null || !hasText(country) || !hasText(league)) {
					continue;
				}

				int result = this.countryLeagueMasterWebRepository.updateRow(
						id,
						target.getCountry(),
						target.getLeague(),
						target.getTeam());

				updateCount += result;

				if (result > 0) {
					InitialReadingMasterCsvUpdateStatusTargetRequest updated = new InitialReadingMasterCsvUpdateStatusTargetRequest();
					updated.setCountry(target.getCountry());
					updated.setLeague(target.getLeague());
					updatedTargets.add(updated);
				}
			}
		}

		response.setUpdateCount(updateCount);
		response.setUpdatedTargets(updatedTargets);
		response.setMessage(updateCount == 0 ? "処理失敗しました。" : "処理成功しました。");

		return response;
	}

	/**
	 * モーダルで確認した対象の レコード を一括削除
	 */
	@Transactional
	public InitialReadingMasterCsvUpdateResponse deleteRow(
			InitialReadingMasterCsvDeleteTargetRequest request) {

		InitialReadingMasterCsvUpdateResponse response = new InitialReadingMasterCsvUpdateResponse();
		List<InitialReadingMasterCsvUpdateStatusTargetRequest> updatedTargets = new ArrayList<>();

		if (request == null || !hasText(request.getMasterName())) {
			response.setMessage("マスタ名がありません。");
			response.setUpdateCount(0);
			response.setUpdatedTargets(updatedTargets);
			return response;
		}

		if ((request.getMasterEntities() == null || request.getMasterEntities().isEmpty())
				&& (request.getSeasonMasterEntities() == null || request.getSeasonMasterEntities().isEmpty())) {
			response.setMessage("更新対象がありません。");
			response.setUpdateCount(0);
			response.setUpdatedTargets(updatedTargets);
			return response;
		}

		int updateCount = 0;

		if (MasterNameConstant.COUNTRY_LEAGUE_SEASON_MASTER.equals(request.getMasterName())) {
			for (CountryLeagueSeasonMasterEntity target : request.getSeasonMasterEntities()) {
				if (target == null) {
					continue;
				}

				Integer id = target.getId();
				String country = target.getCountry();
				String league = target.getLeague();

				if (id == null || !hasText(country) || !hasText(league)) {
					continue;
				}

				int result = this.countryLeagueSeasonMasterWebRepository.deleteRow(id);
				updateCount += result;

				if (result > 0) {
					InitialReadingMasterCsvUpdateStatusTargetRequest deleted = new InitialReadingMasterCsvUpdateStatusTargetRequest();
					deleted.setCountry(target.getCountry());
					deleted.setLeague(target.getLeague());
					updatedTargets.add(deleted);
				}
			}
		}

		if (MasterNameConstant.COUNTRY_LEAGUE_MASTER.equals(request.getMasterName())) {
			for (CountryLeagueMasterEntity target : request.getMasterEntities()) {
				if (target == null) {
					continue;
				}

				String country = target.getCountry();
				String league = target.getLeague();
				String team = target.getTeam();

				if (!hasText(country) || !hasText(league) || !hasText(team)) {
					continue;
				}

				int result = this.countryLeagueMasterWebRepository.deleteRow(
						target.getCountry(),
						target.getLeague(),
						target.getTeam());

				updateCount += result;

				if (result > 0) {
					InitialReadingMasterCsvUpdateStatusTargetRequest deleted = new InitialReadingMasterCsvUpdateStatusTargetRequest();
					deleted.setCountry(target.getCountry());
					deleted.setLeague(target.getLeague());
					updatedTargets.add(deleted);
				}
			}
		}

		response.setUpdateCount(updateCount);
		response.setUpdatedTargets(updatedTargets);
		response.setMessage(updateCount == 0 ? "処理失敗しました。" : "処理成功しました。");

		return response;
	}

	/**
	 * テーブル名によってレスポンス用データを設定
	 */
	private void setViewData(
			InitialReadingMasterCsvResponse response,
			String masterName,
			List<InitialReadingMasterCsvEntity> list) {

		List<CountryLeagueSeasonMasterEntity> seasonLists = new ArrayList<>();
		List<CountryLeagueMasterEntity> masterLists = new ArrayList<>();

		if (list == null) {
			list = new ArrayList<>();
		}

		for (InitialReadingMasterCsvEntity entity : list) {
			if (entity == null) {
				continue;
			}

			switch (masterName) {
			case MasterNameConstant.COUNTRY_LEAGUE_SEASON_MASTER: {
				CountryLeagueSeasonSearchCondition searchCondition = new CountryLeagueSeasonSearchCondition();
				searchCondition.setCountry(entity.getCountry());
				searchCondition.setLeague(entity.getLeague());
				searchCondition.setDelFlg("0");

				List<CountryLeagueSeasonDTO> seasonDTOs =
						this.countryLeagueSeasonMasterWebRepository.search(searchCondition);

				for (CountryLeagueSeasonDTO dto : seasonDTOs) {
					CountryLeagueSeasonMasterEntity seasonMasterEntity = new CountryLeagueSeasonMasterEntity();
					seasonMasterEntity.setId(Integer.parseInt(dto.getId()));
					seasonMasterEntity.setCountry(dto.getCountry());
					seasonMasterEntity.setLeague(dto.getLeague());
					seasonMasterEntity.setStartSeasonDate(dto.getStartSeasonDate());
					seasonMasterEntity.setEndSeasonDate(dto.getEndSeasonDate());
					seasonMasterEntity.setSeasonYear(dto.getSeasonYear());
					seasonMasterEntity.setRound(dto.getRound());
					seasonMasterEntity.setIcon(dto.getIcon());
					seasonMasterEntity.setPath(dto.getPath());
					seasonLists.add(seasonMasterEntity);
				}
				break;
			}
			case MasterNameConstant.COUNTRY_LEAGUE_MASTER: {
				CountryLeagueSearchCondition searchCondition = new CountryLeagueSearchCondition();
				searchCondition.setCountry(entity.getCountry());
				searchCondition.setLeague(entity.getLeague());
				searchCondition.setDelFlg("0");

				List<CountryLeagueDTO> leagueDTOs =
						this.countryLeagueMasterWebRepository.search(searchCondition);

				for (CountryLeagueDTO dto : leagueDTOs) {
					CountryLeagueMasterEntity masterEntity = new CountryLeagueMasterEntity();
					masterEntity.setId(dto.getId()); // ← これが重要
					masterEntity.setCountry(dto.getCountry());
					masterEntity.setLeague(dto.getLeague());
					masterEntity.setTeam(dto.getTeam());
					masterEntity.setLink(dto.getLink());
					masterLists.add(masterEntity);
				}
				break;
			}
			default:
				throw new IllegalArgumentException("Unexpected value: " + masterName);
			}
		}

		response.setMasterName(masterName);
		response.setCountryLeagueSeasonMasterEntityList(seasonLists);
		response.setCountryLeagueMasterEntityList(masterLists);
	}

	/**
	 * 文字列に値があるか
	 */
	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	/**
	 * Integer 変換。null / 空文字 / 数値変換失敗時は null
	 */
	private Integer parseIntegerOrNull(String value) {
		if (!hasText(value)) {
			return null;
		}
		try {
			return Integer.valueOf(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
