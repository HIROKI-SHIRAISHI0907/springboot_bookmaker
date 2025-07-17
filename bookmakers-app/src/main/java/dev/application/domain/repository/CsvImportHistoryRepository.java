package dev.application.domain.repository;

import org.apache.ibatis.annotations.Insert;

import dev.application.analyze.bm_m098.UpdFileDataInfoEntity;

public interface CsvImportHistoryRepository {

	@Insert("""
	        INSERT INTO file_chk (
	            id,
	            file_name,
	            file_hash,
	            register_id,
	            register_time,
	            update_id,
	            update_time
	        ) VALUES (
	            #{id},
	            #{fileName},
	            #{fileHash},
	            #{registerId},
	            #{registerTime},
	            #{updateId},
	            #{updateTime}
	        )
	    """)
	    void insert(UpdFileDataInfoEntity entity);

}
