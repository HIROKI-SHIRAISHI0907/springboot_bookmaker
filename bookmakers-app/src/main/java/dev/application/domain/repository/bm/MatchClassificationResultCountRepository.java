package dev.application.domain.repository.bm;

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
			    SET    count = CAST(#{count} AS varchar)
			    WHERE  id = CAST(#{id} AS integer)
			""")
	int update(@Param("id") Integer id,
			@Param("count") Integer count);

	// 返り値は int（1 が返る）
	@Select("""
			WITH upd AS (
			  UPDATE match_classification_result_count
			  SET
			    /* varchar の count を数値化して +1、最後に varchar へ戻す */
			    count       = (COALESCE(NULLIF(count, ''), '0')::integer + 1)::varchar,
			    remarks     = #{remarks},
			    update_id   = #{updateId},
			    update_time = CAST(#{updateTime} AS timestamptz)
			  WHERE country = #{country}
			    AND league = #{league}
			    AND classify_mode = #{classifyMode}
			  RETURNING 1
			),
			ins AS (
			  INSERT INTO match_classification_result_count (
			    country, league, classify_mode, count,
			    remarks, register_id, register_time, update_id, update_time
			  )
			  SELECT
			    #{country}, #{league}, #{classifyMode}, '1',
			    #{remarks},
			    #{registerId}, CAST(#{registerTime} AS timestamptz),
			    #{updateId},  CAST(#{updateTime}  AS timestamptz)
			  WHERE NOT EXISTS (SELECT 1 FROM upd)
			  RETURNING 1
			)
			/* UPDATE or INSERT のいずれかが実行されていれば 1 を返す */
			SELECT CASE
			         WHEN EXISTS (SELECT 1 FROM upd) THEN 1
			         WHEN EXISTS (SELECT 1 FROM ins) THEN 1
			         ELSE 0
			       END AS affected;
			""")
	int upsertIncrementCount(MatchClassificationResultCountEntity entity);

}
