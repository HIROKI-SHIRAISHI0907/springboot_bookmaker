package dev.mng.domain.repository;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.mng.analyze.bm_c001.StatSizeFinalizeMasterCsvEntity;

@Mapper
public interface StatSizeFinalizeMasterRepository {

	@Insert({
			"INSERT INTO stat_size_finalize_master (",
			"option_num, options, flg, register_id, register_time, update_id, update_time",
			") VALUES (",
			"#{optionNum}, #{options}, #{flg}, ",
			"#{registerId}, CAST(#{registerTime} AS timestamptz), #{updateId}, CAST(#{updateTime}  AS timestamptz));"
	})
	int insert(StatSizeFinalizeMasterCsvEntity entity);

	@Select("""
			  SELECT id, option_num, options, flg
			  FROM stat_size_finalize_master
			  WHERE flg = #{flg}
			  ORDER BY option_num, id;
			""")
	List<StatSizeFinalizeMasterCsvEntity> findFlgData(String flg);

	@Select("""
			  SELECT id
			  FROM stat_size_finalize_master
			  WHERE option_num = #{optionNum} AND options = #{options};
			""")
	List<StatSizeFinalizeMasterCsvEntity> findData(String optionNum, String options);

	@Update({
			  "UPDATE stat_size_finalize_master ",
			  "SET option_num = #{optionNum}, options = #{options}, flg = #{flg} ",
			  "WHERE id = #{id};"
	})
	int update(StatSizeFinalizeMasterCsvEntity entity);

}
