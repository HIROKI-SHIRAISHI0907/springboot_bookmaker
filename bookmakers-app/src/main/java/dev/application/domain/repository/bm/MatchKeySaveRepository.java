package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MatchKeySaveRepository {

	@Select("""
			  SELECT COUNT(*)
			  FROM match_key_save
			  WHERE match_key = #{matchKey}
			""")
	int findMatchKeys(@Param("matchKey") String matchKey);

}