package dev.batch.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import dev.common.entity.StatSizeFinalizeEntity;

/**
 * stat_size_finalize_master 操作用リポジトリ.
 *
 * @author shiraishitoshio
 */
@Mapper
public interface StatSizeFinalizeMasterRepository {

	@Select("""
			    SELECT
			    	id, option_num, options, valid_flg
			    FROM
			    	stat_size_finalize_master
			    WHERE
			        valid_flg = #{validFlg}
			    ORDER BY option_num, id;
			""")
	List<StatSizeFinalizeEntity> findFlgData(
			@Param("validFlg") String flg);

}
