package dev.mng.domain.repository;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import dev.mng.analyze.bm_c001.StatSizeFinalizeMasterCsvEntity;

@Mapper
public interface StatSizeFinalizeMasterRepository {

	@Insert({
			"INSERT INTO stat_size_finalize_master (",
			 "id, options, flg, register_id, register_time, update_id, update_time",
			 ") VALUES (",
			 "#{id}, #{options}, #{flg}, ",
			 "#{registerId}, #{registerTime}, #{updateId}, #{updateTime});"
	})
	int insert(StatSizeFinalizeMasterCsvEntity entity);

	@Select({
	    "SELECT ",
	    "id, options, flg, ",
	    "FROM stat_size_finalize_master ",
	    "WHERE flg = #{flg};"
	})
	List<StatSizeFinalizeMasterCsvEntity> findData(String flg);

}
