package dev.application.domain.repository;

import org.apache.ibatis.annotations.Insert;

import dev.application.analyze.bm_m098.UpdFileDataInfoEntity;

public interface UpdFileDataInfoRepository {

	@Insert("""
	        INSERT INTO file_chk_tmp (
	            id,
	            country,
	            league,
	            file_name,
	            bef_seq_list,
	            af_seq_list,
	            bef_file_hash,
	            af_file_hash,
	            remarks,
	            register_id,
	            register_time,
	            update_id,
	            update_time
	        ) VALUES (
	            #{id},
	            #{country},
	            #{league},
	            #{fileName},
	            #{befSeqList},
	            #{afSeqList},
	            #{befFileHash},
	            #{afFileHash},
	            #{registerId},
	            #{registerTime},
	            #{updateId},
	            #{updateTime}
	        )
	    """)
	    void insert(UpdFileDataInfoEntity entity);

}
