package dev.batch.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MatchKeySaveRepository {

	@Select("""
			  SELECT match_key
			  FROM match_key_save
			  WHERE match_key IS NOT NULL
			""")
	List<String> findMatchKeys();

	@Update("""
			    TRUNCATE TABLE match_key_save RESTART IDENTITY CASCADE;
			""")
	int truncate();

}