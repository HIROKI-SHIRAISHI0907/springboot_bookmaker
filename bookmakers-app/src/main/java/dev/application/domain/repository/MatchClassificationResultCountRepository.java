package dev.application.domain.repository;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.application.analyze.bm_m019_bm_m020.MatchClassificationResultCountEntity;

@Mapper
public interface MatchClassificationResultCountRepository {

	@Insert("""
			    INSERT INTO classify_result_data_detail (
			        id,
			        country,
			        league,
			        classify_mode,
			        count,
			        remarks,
			        register_id,
			       	register_time,
			       	update_id,
			       	update_time
			    ) VALUES (
			        #{id},
			        #{country},
			        #{league},
			        #{classifyMode},
			        #{count},
			        #{remarks},
			        #{registerId},
			       	#{registerTime},
			       	#{updateId},
			       	#{updateTime}
			    )
			""")
	int insert(MatchClassificationResultCountEntity entity);

	@Select("""
			    SELECT
			        id,
			        count
			    FROM
			    	classify_result_data_detail
			    WHERE
			        country = #{country} AND
			        league = #{league} AND
			        classify_mode = #{classifyMode};
			""")
	List<MatchClassificationResultCountEntity> findData(String country, String league, String classifyMode);

	@Update("""
			    UPDATE classify_result_data_detail
			    SET
			        count = #{count}
			    WHERE
			        id = #{id};
			""")
	int update(String id, String count);

}
