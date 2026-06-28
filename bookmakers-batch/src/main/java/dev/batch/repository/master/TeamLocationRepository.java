package dev.batch.repository.master;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

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
			    team_id,
			    team_name,
			    country,
			    home_city,
			    stadium_name,
			    address,
			    latitude,
			    longitude,
			    geocode_source,
			    valid_from,
			    valid_to,
			    register_id,
			    register_time,
			    update_id,
			    update_time
			) VALUES (
			    #{teamId},
			    #{teamName},
			    #{country},
			    #{homeCity},
			    #{stadiumName},
			    #{address},
			    #{latitude},
			    #{longitude},
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
}
