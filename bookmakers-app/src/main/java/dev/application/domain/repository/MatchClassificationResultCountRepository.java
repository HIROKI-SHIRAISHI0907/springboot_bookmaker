package dev.application.domain.repository;

import org.apache.ibatis.annotations.Insert;

import dev.application.analyze.bm_m019_bm_m020.MatchClassificationResultCountEntity;

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
	            #{classify_mode},
	            #{count},
	            #{remarks},
	            #{registerId},
            	#{registerTime},
            	#{updateId},
            	#{updateTime}
	        )
	    """)
	    void insert(MatchClassificationResultCountEntity entity);

}
