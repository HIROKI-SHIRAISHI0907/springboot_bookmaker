package dev.web.api.bm_a021;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.config.PathConfig;
import dev.common.constant.MasterNameConstant;
import dev.common.entity.AllLeagueMasterEntity;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.entity.InitialReadingMasterCsvEntity;
import dev.common.s3.S3Operator;
import dev.web.api.bm_a002.CountryLeagueSeasonDTO;
import dev.web.api.bm_a002.CountryLeagueSeasonSearchCondition;
import dev.web.api.bm_a003.CountryLeagueDTO;
import dev.web.api.bm_a003.CountryLeagueSearchCondition;
import dev.web.repository.master.AllLeagueMasterWebRepository;
import dev.web.repository.master.CountryLeagueMasterWebRepository;
import dev.web.repository.master.CountryLeagueSeasonMasterWebRepository;
import dev.web.repository.master.InitialReadingMasterCsvRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * マスタ登録CSV初回読み込み確認サービス
 *
 * <p>2026-10-27: シーズンマスタの更新・削除が成功したら、有効なシーズンの
 * 「国-リーグ → 終了日時」を b025_fin_season_all_data.json として
 * next season バケット（aws-s3-next-season-info-csv）へ出力する。</p>
 */
@Slf4j
@Service
public class InitialReadingMasterCsvService {

	/** シーズン終了日時一覧の出力ファイル名 */
	private static final String FIN_SEASON_ALL_DATA_KEY = "b025_fin_season_all_data.json";

	@Autowired
	private InitialReadingMasterCsvRepository initialReadingMasterCsvRepository;

	@Autowired
	private CountryLeagueSeasonMasterWebRepository countryLeagueSeasonMasterWebRepository;

	@Autowired
	private CountryLeagueMasterWebRepository countryLeagueMasterWebRepository;

	@Autowired
	private AllLeagueMasterWebRepository allLeagueMasterWebRepository;

	@Autowired
	private S3Operator s3Operator;

	@Autowired
	private PathConfig pathConfig;

	@Autowired
	private ObjectMapper objectMapper;

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

		// シーズンマスタの確定に成功したら、シーズン終了日時一覧を S3 へ出力
		if (updateCount > 0
				&& MasterNameConstant.COUNTRY_LEAGUE_SEASON_MASTER.equals(request.getMasterName())) {
			uploadFinSeasonAllData();
		}

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

				Integer id = target.getId();
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

		if (MasterNameConstant.ALL_LEAGUE_SCRAPE_MASTER.equals(request.getMasterName())) {
			for (AllLeagueMasterEntity target : request.getAllLeagueMasterEntities()) {
				if (target == null) {
					continue;
				}

				Integer id = target.getId();
				String country = target.getCountry();
				String league = target.getLeague();

				if (id == null || !hasText(country) || !hasText(league)) {
					continue;
				}

				int result = this.allLeagueMasterWebRepository.updateRow(
						id,
						target.getCountry(),
						target.getLeague());

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

		// 削除して、その国・リーグに確認する行（有効な行）が残っていなければ「新規データ確認」を確認済み（initial_flg = '1'）にする。
		// しないと、確認画面に出す行が無いのに未確認のまま残り、管理画面の入口チェックが解消できない。
		// 一部だけ削除した場合は、残りの行を確認画面で確認する（OK で確認済みになる）。
		Map<String, InitialReadingMasterCsvUpdateStatusTargetRequest> doneTargets = new LinkedHashMap<>();
		for (InitialReadingMasterCsvUpdateStatusTargetRequest t : updatedTargets) {
			doneTargets.putIfAbsent(t.getCountry() + "___" + t.getLeague(), t);
		}
		for (InitialReadingMasterCsvUpdateStatusTargetRequest t : doneTargets.values()) {
			if (!hasActiveRows(request.getMasterName(), t.getCountry(), t.getLeague())) {
				this.initialReadingMasterCsvRepository.updateInitialFlg(
						request.getMasterName(), t.getCountry(), t.getLeague());
			}
		}

		// シーズンマスタの行を削除したら、シーズン終了日時一覧を S3 へ出力し直す
		if (updateCount > 0
				&& MasterNameConstant.COUNTRY_LEAGUE_SEASON_MASTER.equals(request.getMasterName())) {
			uploadFinSeasonAllData();
		}

		return response;
	}

	/**
	 * 有効なシーズンの「国-リーグ → 終了日時」を b025_fin_season_all_data.json として S3 へ出力する。
	 * 例: {"日本-J1 リーグ": "2027-06-06 23:59:59+09", "ドイツ-ブンデスリーガ": "2026-05-16 23:59:59+09 (season=READFIN)"}
	 *
	 * 既存ファイルとマージする（B013 が付けた (season=READFIN) と、終了処理済みのリーグを消さないため）。
	 * <ul>
	 *   <li>DB の終了日時と既存の日付が同じ → 既存の値（マーカー付き）をそのまま残す。</li>
	 *   <li>DB の終了日時が変わった（次シーズンを登録した） → DB の値で上書き（マーカーは外れる）。</li>
	 *   <li>DB の終了日が空（B013 がクリアした等） → 既存の値を残す（45日後の次シーズン取得の判定に使う）。</li>
	 *   <li>DB に無いリーグ（削除・del_flg=1） → 出力しない。</li>
	 * </ul>
	 * 既存ファイルが読めない場合は、マーカーを消さないよう出力しない。
	 * S3 出力に失敗しても画面の更新処理は成功のままにする（ログのみ）。
	 */
	private void uploadFinSeasonAllData() {
		final String bucket = this.pathConfig.getS3BucketsOutputsNextSeason();
		try {
			Map<String, String> existing = new LinkedHashMap<>();
			if (this.s3Operator.existsOnS3(bucket, FIN_SEASON_ALL_DATA_KEY)) {
				String content = this.s3Operator.downloadTextUtf8(bucket, FIN_SEASON_ALL_DATA_KEY);
				if (content != null && !content.isBlank()) {
					Map<String, String> read = this.objectMapper.readValue(
							content, new TypeReference<LinkedHashMap<String, String>>() {
							});
					if (read != null) {
						existing.putAll(read);
					}
				}
			}

			Map<String, String> dbMap = this.countryLeagueSeasonMasterWebRepository.findEndSeasonDateMap();
			Map<String, String> out = new LinkedHashMap<>();
			for (Map.Entry<String, String> e : dbMap.entrySet()) {
				String key = e.getKey();
				String dbValue = e.getValue();
				String oldValue = existing.get(key);

				if (dbValue == null) {
					if (hasText(oldValue)) {
						out.put(key, oldValue);
					}
					continue;
				}
				if (hasText(oldValue) && dbValue.equals(stripMarkers(oldValue))) {
					out.put(key, oldValue);
				} else {
					out.put(key, dbValue);
				}
			}

			String json = this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);
			this.s3Operator.putJson(bucket, FIN_SEASON_ALL_DATA_KEY, json);
			log.info("[A021] シーズン終了日時一覧をアップロードしました: s3://{}/{} 件数={}",
					bucket, FIN_SEASON_ALL_DATA_KEY, out.size());
		} catch (Exception e) {
			log.error("[A021] シーズン終了日時一覧のアップロードに失敗しました: s3://{}/{}"
					+ "（403 の場合は web のタスクロールに s3:GetObject / s3:PutObject があるか確認）",
					bucket, FIN_SEASON_ALL_DATA_KEY, e);
		}
	}

	/** 値から "(season=READFIN)" などのマーカーを除いて日付部分だけにする */
	private static String stripMarkers(String value) {
		return value == null ? "" : value.replaceAll("\\([A-Za-z]+=READFIN\\)", "").trim();
	}

	/** その国・リーグに、確認画面に出す有効な行（del_flg = '0'）が残っているか */
	private boolean hasActiveRows(String masterName, String country, String league) {
		if (MasterNameConstant.COUNTRY_LEAGUE_SEASON_MASTER.equals(masterName)) {
			CountryLeagueSeasonSearchCondition c = new CountryLeagueSeasonSearchCondition();
			c.setCountry(country);
			c.setLeague(league);
			c.setDelFlg("0");
			return !this.countryLeagueSeasonMasterWebRepository.search(c).isEmpty();
		}
		if (MasterNameConstant.COUNTRY_LEAGUE_MASTER.equals(masterName)) {
			CountryLeagueSearchCondition c = new CountryLeagueSearchCondition();
			c.setCountry(country);
			c.setLeague(league);
			c.setDelFlg("0");
			return !this.countryLeagueMasterWebRepository.search(c).isEmpty();
		}
		return true;
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
					masterEntity.setId(Integer.parseInt(dto.getId())); // ← これが重要
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

}
