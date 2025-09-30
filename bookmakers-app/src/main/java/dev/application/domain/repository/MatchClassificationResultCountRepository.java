package dev.application.domain.repository;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.application.analyze.bm_m019_bm_m020.MatchClassificationResultCountEntity;

@Mapper
public interface MatchClassificationResultCountRepository {

	@Insert("""
			    INSERT INTO match_classification_result_count (
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
			        #{country},
			        #{league},
			        #{classifyMode},
			        #{count},
			        #{remarks},
			        #{registerId}, CAST(#{registerTime} AS timestamptz), #{updateId}, CAST(#{updateTime}  AS timestamptz)
			    );
			""")
	int insert(MatchClassificationResultCountEntity entity);

	@Select("""
			    SELECT
			        id,
			        count
			    FROM
			    	match_classification_result_count
			    WHERE
			        country = #{country} AND
			        league = #{league} AND
			        classify_mode = #{classifyMode};
			""")
	List<MatchClassificationResultCountEntity> findData(String country, String league, String classifyMode);

	@Update("""
			UPDATE match_classification_result_count
			SET    count = #{count, jdbcType=INTEGER}
			WHERE  id    = #{id, jdbcType=INTEGER}
			""")
	int update(@Param("id") Integer id,
			@Param("count") Integer count);

}
