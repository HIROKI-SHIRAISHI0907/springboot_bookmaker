package dev.batch.bm_b014;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.interf.TeamLocationEntityIF;
import dev.batch.repository.bm.BookDataRepository;
import dev.batch.repository.master.TeamLocationRepository;
import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.entity.TeamLocationEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.util.DateUtil;
import dev.common.util.ExecuteMainUtil;
import dev.common.util.ExecuteMainUtil.StadiumSplitResult;
import dev.common.util.FileDeleteUtil;

/**
 * TeamLocationStat登録ロジック
 * @author shiraishitoshio
 *
 */
@Service
public class TeamLocationStat implements TeamLocationEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamLocationStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamLocationStat.class.getName();

	/** データテーブル取得件数 */
	private static final int LIMIT = 200;

	@Autowired
	private BookDataRepository bookDataRepository; // bm
	@Autowired
	private TeamLocationRepository teamLocationRepository;

	/** Config */
	@Autowired
	private PathConfig config;

	/** S3Operator */
	@Autowired
	private S3Operator s3Operator;

	/** TeamLocationDBService部品 */
	@Autowired
	private TeamLocationDBService teamLocationDBService;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void teamLocationStat(List<TeamLocationEntity> map, boolean readyFlg) throws Exception {
		final String METHOD_NAME = "teamLocationStat";
		manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		List<String> insertPath = new ArrayList<>();
		try {
			// 位置情報が分かるデータを事前にマスタに登録しておく
			if (readyFlg)
				readyFlgTrue();

			// 取得できた情報に更新
			if (!readyFlg)
				readyFlgFalse(map);
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			throw new Exception(messageCd, e);
		}

		insertPath.add("b015_team_location.csv");
		insertPath.add("b015_geografic_input.json");

		String bucket = config.getS3Geografic();
		FileDeleteUtil.deleteS3Files(
				insertPath,
				bucket,
				s3Operator,
				manageLoggerComponent,
				PROJECT_NAME,
				CLASS_NAME,
				METHOD_NAME,
				"GEOGRAFIC_MASTER");

		manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	}

	/**
	 * 事前準備フラグがtrue: dataテーブルからスタジアム情報がわかるデータを取得
	 */
	private void readyFlgTrue() {
		final String METHOD_NAME = "readyFlgTrue";
		// dataテーブルの全件数を取得
		int total = bookDataRepository.countStadium();
		for (int offset = 0; offset < total; offset += LIMIT) {
			List<DataEntity> list = bookDataRepository.findStadium(LIMIT, offset);

			if (list == null || list.isEmpty()) {
				break;
			}

			manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG, "範囲" + offset + "~" + (offset + LIMIT) + ", "
							+ "データテーブル取得サイズ: " + list.size());

			int countAll = 0;
			for (DataEntity entity : list) {
				String dataCategory = entity.getDataCategory();
				String homeTeamName = entity.getHomeTeamName();
				List<String> countryLeague = ExecuteMainUtil.getCountryLeagueByRegex(dataCategory);
				if (countryLeague == null || countryLeague.size() < 2) {
					manageLoggerComponent.debugInfoLog(
							PROJECT_NAME,
							CLASS_NAME,
							METHOD_NAME,
							MessageCdConst.MCD00099I_LOG,
							"dataCategory解析スキップ: dataCategory=" + dataCategory + ", team=" + homeTeamName);
					continue;
				}

				String location = ExecuteMainUtil.normalizeText(entity.getLocation());

				StadiumSplitResult splitResult = ExecuteMainUtil.splitStadiumAndCity(entity.getStudium());
				String studium = splitResult.getStadiumName();

				// location が空なら、studium末尾の都市名を採用
				if (location == null || location.isBlank()) {
					location = splitResult.getCityName();
				}

				TeamLocationEntity insertEntity = new TeamLocationEntity();
				insertEntity.setCountry(countryLeague.get(0));
				insertEntity.setTeamName(homeTeamName);
				insertEntity.setHomeCity(location);
				insertEntity.setStadiumName(studium);
				int counts = teamLocationRepository.count(insertEntity);
				if (counts > 0)
					continue;

				insertEntity.setGeocodeSource("B014_batch");

				int rows = teamLocationRepository.insert(insertEntity);
				if (rows != 1) {
					throw new RuntimeException(
							"team_location_master insert affected rows=" + rows
									+ " country=" + countryLeague.get(0)
									+ " homeCity=" + location
									+ " stadium=" + studium);
				}

				String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, "登録件数: " + rows + "件, (国: " +
								countryLeague.get(0) + ", チーム: " + homeTeamName + ", 都市: " + location + ", スタジアム: "
								+ studium);
				countAll += rows;
			}

			String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, "全体登録件数: " + countAll + "件");

		}
	}

	/**
	 * 事前準備フラグがfalse:
	 * Google geografic APIから取得した位置情報を保存しているCSVを用いて情報更新する
	 */
	private void readyFlgFalse(List<TeamLocationEntity> list) {
		Map<String, TeamLocationEntity> afterMap = new HashMap<>();
		for (TeamLocationEntity aft : list) {
			afterMap.put(buildNaturalKey(aft), aft);
		}

		// 既存DBデータ取得
		List<TeamLocationEntity> updateBef = teamLocationDBService.selectInBatch();

		// 1. 既存データは update
		for (TeamLocationEntity bef : updateBef) {
			String key = buildNaturalKey(bef);
			TeamLocationEntity aft = afterMap.remove(key); // removeしておくと残りがinsert対象になる
			if (aft == null) {
				continue;
			}

			String fillChar = "id: " + bef.getId()
					+ ", 国: " + bef.getCountry()
					+ ", チーム: " + bef.getTeamName()
					+ ", 都市名: " + bef.getHomeCity()
					+ ", スタジアム: " + bef.getStadiumName();

			TeamLocationEntity updateEntity = buildUpdateEntity(bef.getId(), aft);
			teamLocationDBService.updateInBatch(updateEntity, fillChar);
		}

		// 2. 残ったデータは新規 insert
		for (TeamLocationEntity aft : afterMap.values()) {
			String fillChar = "新規登録"
					+ ", 国: " + aft.getCountry()
					+ ", チーム: " + aft.getTeamName()
					+ ", 都市名: " + aft.getHomeCity()
					+ ", スタジアム: " + aft.getStadiumName();

			TeamLocationEntity insertEntity = buildInsertEntity(aft);
			teamLocationDBService.insertInBatch(insertEntity, fillChar);
		}
	}

	private TeamLocationEntity buildUpdateEntity(Integer id, TeamLocationEntity src) {
		TeamLocationEntity entity = new TeamLocationEntity();

		entity.setId(id);

		entity.setCountry(src.getCountry());
		entity.setCountryTranslate(src.getCountryTranslate());

		entity.setTeamName(src.getTeamName());
		entity.setTeamNameTranslate(src.getTeamNameTranslate());

		entity.setHomeCity(src.getHomeCity());
		entity.setHomeCityTranslate(src.getHomeCityTranslate());

		entity.setStadiumName(src.getStadiumName());
		entity.setStadiumNameTranslate(src.getStadiumNameTranslate());

		entity.setAddress(src.getAddress());
		entity.setLatitude(src.getLatitude());
		entity.setLongitude(src.getLongitude());

		entity.setPlaceId(src.getPlaceId());

		entity.setDisplayNameEn(src.getDisplayNameEn());
		entity.setAddressEn(src.getAddressEn());
		entity.setLatitudeEn(src.getLatitudeEn());
		entity.setLongitudeEn(src.getLongitudeEn());

		entity.setDisplayNameLocal(src.getDisplayNameLocal());
		entity.setAddressLocal(src.getAddressLocal());
		entity.setLatitudeLocal(src.getLatitudeLocal());
		entity.setLongitudeLocal(src.getLongitudeLocal());

		entity.setLocalLanguageCode(src.getLocalLanguageCode());
		entity.setGeocodeSource(src.getGeocodeSource());

		entity.setValidFrom(src.getValidFrom());
		entity.setValidTo(src.getValidTo());

		return entity;
	}

	private TeamLocationEntity buildInsertEntity(TeamLocationEntity src) {
		TeamLocationEntity entity = new TeamLocationEntity();

		entity.setCountry(src.getCountry());
		entity.setCountryTranslate(src.getCountryTranslate());

		entity.setTeamName(src.getTeamName());
		entity.setTeamNameTranslate(src.getTeamNameTranslate());

		entity.setHomeCity(src.getHomeCity());
		entity.setHomeCityTranslate(src.getHomeCityTranslate());

		entity.setStadiumName(src.getStadiumName());
		entity.setStadiumNameTranslate(src.getStadiumNameTranslate());

		entity.setAddress(src.getAddress());
		entity.setLatitude(src.getLatitude());
		entity.setLongitude(src.getLongitude());

		entity.setPlaceId(src.getPlaceId());

		entity.setDisplayNameEn(src.getDisplayNameEn());
		entity.setAddressEn(src.getAddressEn());
		entity.setLatitudeEn(src.getLatitudeEn());
		entity.setLongitudeEn(src.getLongitudeEn());

		entity.setDisplayNameLocal(src.getDisplayNameLocal());
		entity.setAddressLocal(src.getAddressLocal());
		entity.setLatitudeLocal(src.getLatitudeLocal());
		entity.setLongitudeLocal(src.getLongitudeLocal());

		entity.setLocalLanguageCode(src.getLocalLanguageCode());
		entity.setGeocodeSource(src.getGeocodeSource());

		entity.setValidFrom(
				src.getValidFrom() == null ? DateUtil.convertLocalDateTime(DateUtil.getSysDate()) : src.getValidFrom());
		entity.setValidTo(
				src.getValidTo() == null
						? java.time.LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_000_000)
						: src.getValidTo());

		return entity;
	}

	private String buildNaturalKey(TeamLocationEntity e) {
		return nvl(e.getCountry()) + "|"
				+ nvl(e.getTeamName()) + "|"
				+ nvl(e.getHomeCity()) + "|"
				+ nvl(e.getStadiumName());
	}

	private String nvl(String s) {
		return s == null ? "" : s;
	}

}