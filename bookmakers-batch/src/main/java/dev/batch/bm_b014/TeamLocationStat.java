package dev.batch.bm_b014;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

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
 *
 * <h2>readyFlg = true（事前準備）</h2>
 * <ol>
 *   <li>static_data のスタジアム情報（カテゴリ・ホームチーム・都市・スタジアム）を team_location_master に登録する（既にあれば何もしない）。</li>
 *   <li>緯度経度がまだ無い行（B014 が登録して B015 の結果が未反映の行）をすべて b015_geografic_input.json にして S3 に置く。</li>
 * </ol>
 *
 * <h2>readyFlg = false（B015 の結果取り込み）</h2>
 * <p>Google Places API の結果 CSV で team_location_master を更新・登録し、S3 の入力 JSON・結果 CSV を消す。</p>
 *
 * <h2>修正履歴</h2>
 * <ul>
 *   <li>国の取り出しを「: より前」にした。旧実装は「国: リーグ - ラウンドN」形式でないと読めない
 *       ExecuteMainUtil.getCountryLeagueByRegex を使っていたため、「スペイン: ラ・リーガ」などラウンドの無いカテゴリのチームが
 *       すべてスキップされていた。</li>
 *   <li>入力 JSON を「今回登録した行」ではなく「緯度経度がまだ無い行（DB）」から作る。旧実装は JSON のアップロードに失敗したり、
 *       B015 が処理する前に次の B014 が JSON を上書きしたりすると、その回に登録したスタジアムが二度と JSON に載らなかった。</li>
 *   <li>JSON のアップロードをページごと（200 件ごと）から最後の1回にした。</li>
 *   <li>同じチーム・スタジアムが何度出てきても（ラウンドごとにカテゴリが違うため）1回だけ判定する。</li>
 * </ul>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li>1トランザクション（全件）なので、JSON のアップロードに失敗すると今回の登録もロールバックされる（次回また登録し直す）。</li>
 *   <li>カテゴリに国が無い行（「プリメーラ A - クラウスラ」など）はスキップ（同じチームの別の行に国があればそちらで登録される）。</li>
 * </ul>
 *
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

	/** Pythonバッチ(B015)への入力JSONファイル名 */
	private static final String GEOGRAFIC_INPUT_KEY = "b015_geografic_input.json";

	/** B014 が登録した行の geocode_source */
	private static final String GEOCODE_SOURCE_B014 = "B014_batch";

	/** JSON変換用 */
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

		if (readyFlg)
			return;

		insertPath.add("b015_team_location.csv");
		insertPath.add(GEOGRAFIC_INPUT_KEY);

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
	 * 事前準備フラグがtrue: dataテーブルからスタジアム情報がわかるデータを登録し、
	 * 緯度経度がまだ無い行を B015 の入力 JSON にして S3 に置く。
	 */
	private void readyFlgTrue() {
		final String METHOD_NAME = "readyFlgTrue";
		// dataテーブルの全件数を取得
		int total = bookDataRepository.countStadium();

		// 同じチーム・スタジアムはカテゴリ（ラウンド）違いで何度も出てくるので1回だけ判定する
		Set<String> seen = new HashSet<>();
		int insertedAll = 0;
		int skippedNoCountry = 0;
		for (int offset = 0; offset < total; offset += LIMIT) {
			List<DataEntity> list = bookDataRepository.findStadium(LIMIT, offset);
			if (list == null || list.isEmpty()) {
				break;
			}

			for (DataEntity entity : list) {
				String homeTeamName = trimToNull(entity.getHomeTeamName());
				String country = parseCountry(entity.getDataCategory());
				if (country == null || homeTeamName == null) {
					skippedNoCountry++;
					continue;
				}

				String location = ExecuteMainUtil.normalizeText(entity.getLocation());
				StadiumSplitResult splitResult = ExecuteMainUtil.splitStadiumAndCity(entity.getStudium());
				String studium = splitResult.getStadiumName();
				// location が空なら、studium末尾の都市名を採用
				if (location == null || location.isBlank()) {
					location = splitResult.getCityName();
				}

				if (!seen.add(country + "|" + homeTeamName + "|" + nvl(location) + "|" + nvl(studium))) {
					continue;
				}

				TeamLocationEntity insertEntity = new TeamLocationEntity();
				insertEntity.setCountry(country);
				insertEntity.setTeamName(homeTeamName);
				insertEntity.setHomeCity(location);
				insertEntity.setStadiumName(studium);
				if (teamLocationRepository.count(insertEntity) > 0)
					continue;

				insertEntity.setGeocodeSource(GEOCODE_SOURCE_B014);
				int rows = teamLocationRepository.insert(insertEntity);
				if (rows != 1) {
					throw new RuntimeException(
							"team_location_master insert affected rows=" + rows
									+ " country=" + country
									+ " homeCity=" + location
									+ " stadium=" + studium);
				}
				insertedAll += rows;
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00005I_INSERT_SUCCESS,
						"登録: 国: " + country + ", チーム: " + homeTeamName + ", 都市: " + location + ", スタジアム: " + studium);
			}
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00005I_INSERT_SUCCESS,
				"全体登録件数: " + insertedAll + "件（対象行 " + total + "、カテゴリに国が無くスキップ " + skippedNoCountry + "）");

		// 今回の登録分だけでなく、緯度経度がまだ無い行すべてを入力にする（前回の JSON が処理されなかった分も拾う）
		uploadGeograficInputJson(teamLocationRepository.selectPendingGeocode());
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

	/**
	 * 緯度経度がまだ無いスタジアムを JSON にして b015_geografic_input.json として S3 へアップロードする。
	 * (このファイルをPythonバッチ(B015)が読み込み、Google Places APIで緯度経度を取得してb015_team_location.csvを生成する)
	 * 対象が無ければアップロードしない。
	 */
	private void uploadGeograficInputJson(List<TeamLocationEntity> pending) {
		final String METHOD_NAME = "uploadGeograficInputJson";

		if (pending == null || pending.isEmpty()) {
			manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"緯度経度が未取得のスタジアムなし。" + GEOGRAFIC_INPUT_KEY + " は作成しません。");
			return;
		}

		List<Map<String, String>> items = new ArrayList<>();
		for (TeamLocationEntity e : pending) {
			Map<String, String> geoItem = new LinkedHashMap<>();
			geoItem.put("country", nvl(e.getCountry()));
			geoItem.put("teamName", nvl(e.getTeamName()));
			geoItem.put("homeCity", nvl(e.getHomeCity()));
			geoItem.put("stadium", nvl(e.getStadiumName()));
			items.add(geoItem);
		}

		String bucket = config.getS3Geografic();
		try {
			String json = OBJECT_MAPPER.writeValueAsString(items);
			s3Operator.putJson(bucket, GEOGRAFIC_INPUT_KEY, json);
			manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					GEOGRAFIC_INPUT_KEY + " をアップロードしました。件数=" + items.size());
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
					GEOGRAFIC_INPUT_KEY + " のアップロードに失敗しました（bucket=" + bucket
							+ "。403 の場合はタスクロールに s3:PutObject の権限があるか確認）");
			throw new RuntimeException(messageCd, e);
		}
	}

	/**
	 * カテゴリから国を取り出す（「国: リーグ …」の「:」より前）。
	 * 「スペイン: ラ・リーガ」「日本: J1 - ラウンド 5」どちらも可。「:」が無ければ null。
	 */
	static String parseCountry(String dataCategory) {
		if (dataCategory == null) {
			return null;
		}
		String s = Normalizer.normalize(dataCategory, Normalizer.Form.NFKC).trim();
		int idx = s.indexOf(':');
		if (idx <= 0) {
			return null;
		}
		String country = s.substring(0, idx).trim();
		return country.isEmpty() ? null : country;
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

	private static String trimToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	private static String nvl(String s) {
		return s == null ? "" : s;
	}

}