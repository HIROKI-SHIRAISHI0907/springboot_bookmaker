package dev.batch.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.MatchKeySaveEntity;

@Mapper
public interface MatchKeySaveRepository {

	@Select("""
			    SELECT
			    	match_key
			    FROM
			    	match_key_save;
			""")
	List<MatchKeySaveEntity> findByMatchKey();

	@Update("""
			    TRUNCATE TABLE match_key_save RESTART IDENTITY CASCADE;
			""")
	int truncate();

}