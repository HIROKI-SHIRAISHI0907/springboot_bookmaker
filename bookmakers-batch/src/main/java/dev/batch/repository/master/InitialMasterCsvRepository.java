package dev.batch.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

}