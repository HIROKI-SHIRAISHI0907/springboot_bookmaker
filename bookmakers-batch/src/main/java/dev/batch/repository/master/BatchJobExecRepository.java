package dev.batch.repository.master;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.batch.bm_b099.BatchJobExecEntity;

@Mapper
public interface BatchJobExecRepository {

	@Insert({
		"	    INSERT INTO batch_job_exec (",
		"	        job_id,",
		"	        batch_cd,",
		"	        status,",
		"	        register_id,",
		"	        register_time,",
		"	        update_id,",
		"	        update_time",
		"	    ) VALUES (",
		"	        #{jobId},",
		"	        #{batchCd},",
		"	        #{status},",
		//"	        #{registerId}, CAST(#{registerTime} AS timestamptz), #{updateId}, CAST(#{updateTime}  AS timestamptz)",
		"	        #{registerId}, #{registerTime}, #{updateId}, #{updateTime}",
		"	    );"
	})
	int jobStartExec(BatchJobExecEntity entity);

	@Select("""
			    SELECT COUNT(1)
			 FROM batch_job_exec
			 WHERE
			     batch_cd = #{batchCd}
			     AND status IN (0, 1)
			     AND update_time >= (CURRENT_TIMESTAMP - INTERVAL '2 hours');
			""")
	int jobCountExec(@Param("batchCd") String batchCd);

	@Update("""
			    UPDATE
			    	batch_job_exec
			    SET
			    	status = #{status},
			    	update_id = 'BATCH',
			    	update_time = CURRENT_TIMESTAMP
			    WHERE
			        job_id = #{jobId}
			        AND status IN (0, 1);
			""")
	int jobUpdateExc(@Param("jobId") String jobId, @Param("status") int status);

	@Update("""
			    UPDATE batch_job_exec
			    SET
			        status = 99,
			        update_id = 'BATCH',
			        update_time = CURRENT_TIMESTAMP
			    WHERE
			        batch_cd = #{batchCd}
			        AND status IN (0,1)
			        AND update_time < (CURRENT_TIMESTAMP - INTERVAL '2 hours')
			""")
	int failStaleJobs(@Param("batchCd") String batchCd);

	@Update("""
			    UPDATE batch_job_exec
			    SET
			        update_id = 'BATCH',
			        update_time = CURRENT_TIMESTAMP
			    WHERE
			        job_id = #{jobId}
			        AND status IN (0, 1);
			""")
	int heartbeat(@Param("jobId") String jobId);

}