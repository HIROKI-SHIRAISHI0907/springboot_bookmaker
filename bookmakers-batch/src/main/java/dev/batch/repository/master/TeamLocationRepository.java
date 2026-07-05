package dev.batch.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.TeamLocationEntity;

/**
 * {@link TeamLocationEntity} のRepositoryです。
 */
@Mapper
public interface TeamLocationRepository {

	/**
	 * チーム本拠地情報を登録します。
	 *
	 * @param entity 登録対象
	 * @return 登録件数
	 */
	@Insert("""
			INSERT INTO team_location_master (
			    country,
			    country_translate,
			    team_name,
			    team_name_translate,
			    home_city,
			    home_city_translate,
			    stadium_name,
			    stadium_name_translate,
			    address,
			    latitude,
			    longitude,
			    place_id,
			    display_name_en,
			    address_en,
			    latitude_en,
			    longitude_en,
			    display_name_local,
			    address_local,
			    latitude_local,
			    longitude_local,
			    local_language_code,
			    geocode_source,
			    valid_from,
			    valid_to,
			    register_id,
			    register_time,
			    update_id,
			    update_time
			) VALUES (
			    #{country},
			    #{countryTranslate},
			    #{teamName},
			    #{teamNameTranslate},
			    #{homeCity},
			    #{homeCityTranslate},
			    #{stadiumName},
			    #{stadiumNameTranslate},
			    #{address},
			    #{latitude},
			    #{longitude},
			    #{placeId},
			    #{displayNameEn},
			    #{addressEn},
			    #{latitudeEn},
			    #{longitudeEn},
			    #{displayNameLocal},
			    #{addressLocal},
			    #{latitudeLocal},
			    #{longitudeLocal},
			    #{localLanguageCode},
			    #{geocodeSource},
			    #{validFrom},
			    #{validTo},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			""")
	int insert(TeamLocationEntity entity);

	/**
	 * 既存件数を取得します。
	 *
	 * <p>
	 * 自然キー想定:
	 * country + team_name + home_city + stadium_name
	 * </p>
	 *
	 * @param entity 検索条件
	 * @return 件数
	 */
	@Select("""
			SELECT COUNT(*)
			FROM team_location_master
			WHERE
				country = #{country}
				AND team_name = #{teamName}
				AND home_city IS NOT DISTINCT FROM #{homeCity}
				AND stadium_name = #{stadiumName}
			""")
	int count(TeamLocationEntity entity);

	/**
	 * データを取得します。
	 *
	 * <p>
	 * 自然キー想定:
	 * country + team_name + home_city + stadium_name
	 * </p>
	 *
	 * @param entity 検索条件
	 * @return 件数
	 */
	@Select("""
				SELECT
				    id                  AS id,
				    country             AS country,
				    country_translate   AS countryTranslate,
				    team_name           AS teamName,
				    team_name_translate AS teamNameTranslate,
				    home_city           AS homeCity,
				    home_city_translate AS homeCityTranslate,
				    stadium_name        AS stadiumName,
				    stadium_name_translate AS stadiumNameTranslate,
				    address             AS address,
				    latitude            AS latitude,
				    longitude           AS longitude,
				    place_id            AS placeId,
				    display_name_en     AS displayNameEn,
				    address_en          AS addressEn,
				    latitude_en         AS latitudeEn,
				    longitude_en        AS longitudeEn,
				    display_name_local  AS displayNameLocal,
				    address_local       AS addressLocal,
				    latitude_local      AS latitudeLocal,
				    longitude_local     AS longitudeLocal,
				    local_language_code AS localLanguageCode,
				    geocode_source      AS geocodeSource,
				    valid_from          AS validFrom,
				    valid_to            AS validTo
				FROM team_location_master
				WHERE
					geocode_source = 'B014_batch'
			""")
	List<TeamLocationEntity> select();

	/**
	 * IDをキーに更新します。
	 *
	 * @param entity 更新対象
	 * @return 更新件数
	 */
	@Update("""
			UPDATE team_location_master
			SET
			    country                = #{country},
			    country_translate      = #{countryTranslate},
			    team_name              = #{teamName},
			    team_name_translate    = #{teamNameTranslate},
			    home_city              = #{homeCity},
			    home_city_translate    = #{homeCityTranslate},
			    stadium_name           = #{stadiumName},
			    stadium_name_translate = #{stadiumNameTranslate},
			    address                = #{address},
			    latitude               = #{latitude},
			    longitude              = #{longitude},
			    place_id               = #{placeId},
			    display_name_en        = #{displayNameEn},
			    address_en             = #{addressEn},
			    latitude_en            = #{latitudeEn},
			    longitude_en           = #{longitudeEn},
			    display_name_local     = #{displayNameLocal},
			    address_local          = #{addressLocal},
			    latitude_local         = #{latitudeLocal},
			    longitude_local        = #{longitudeLocal},
			    local_language_code    = #{localLanguageCode},
			    geocode_source         = #{geocodeSource},
			    valid_from             = #{validFrom},
			    valid_to               = #{validTo},
			    update_id              = 'SYSTEM',
			    update_time            = CURRENT_TIMESTAMP
			WHERE
			    id = #{id}
			""")
	int updateById(TeamLocationEntity entity);
}
