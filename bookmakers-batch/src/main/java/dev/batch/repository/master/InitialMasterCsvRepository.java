package dev.batch.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.InitialReadingMasterCsvEntity;

@Mapper
public interface InitialMasterCsvRepository {

	@Insert("""
			    INSERT INTO initial_reading_csv_master (
			        master_name,
				    country,
				    league,
				    initial_flg,
			        register_id,
			        register_time,
			        update_id,
			        update_time
			    ) VALUES (
			        #{masterName},
			        #{country},
			        #{league},
			        #{initialFlg},
			        'SYSTEM',
			        CURRENT_TIMESTAMP,
			        'SYSTEM',
			        CURRENT_TIMESTAMP
			    );
			""")
	int insert(InitialReadingMasterCsvEntity entity);

	@Delete("""
			    DELETE FROM
			    	initial_reading_csv_master
			    WHERE
			        country = #{country} AND
			        league = #{league} AND
			        master_name = #{masterName};
			""")
	int delete(@Param("masterName") String master_name,
			@Param("country") String country,
			@Param("league") String league);

	@Select("""
			    SELECT
			     	master_name AS masterName,
				    country,
				    league,
				    initial_flg AS initialFlg
			    FROM
			     	initial_reading_csv_master
			    WHERE
			        master_name = #{masterName}
			""")
	List<InitialReadingMasterCsvEntity> select(@Param("masterName") String master_name);

	/**
	 * 対象件数取得
	 */
	@Select("""
				SELECT COUNT(*)
				FROM initial_reading_csv_master
				WHERE master_name = #{masterName}
				  AND country = #{country}
				  AND league = #{league}
			""")
	int findCount(
			@Param("masterName") String masterName,
			@Param("country") String country,
			@Param("league") String league);

	/**
	 * 初回確認フラグ更新
	 *
	 * 想定用途:
	 * - 既存データが更新された時に initial_flg を 0 に戻す
	 */
	@Update("""
		UPDATE initial_reading_csv_master
		SET
			initial_flg = #{initialFlg},
			update_id = 'SYSTEM',
			update_time = CURRENT_TIMESTAMP
		WHERE master_name = #{masterName}
		  AND country = #{country}
		  AND league = #{league}
	""")
	int updateInitialFlg(
			@Param("masterName") String masterName,
			@Param("country") String country,
			@Param("league") String league,
			@Param("initialFlg") String initialFlg);

}