package dev.application.domain.repository;

import org.apache.ibatis.annotations.Insert;

import dev.application.analyze.bm_m097.UpdCsvInfoEntity;

public interface UpdCsvInfoRepository {

	@Insert("""
	        INSERT INTO upd_csv_info (
	            id,
	            country,
	            league,
	            tableId,
	            remarks,
	            register_id,
	            register_time,
	            update_id,
	            update_time
	        ) VALUES (
	            #{id},
	            #{country},
	            #{league},
	            #{tableId},
	            #{remarks},
	            #{registerId},
	            #{registerTime},
	            #{updateId},
	            #{updateTime}
	        )
	    """)
	    void insert(UpdCsvInfoEntity entity);

}
