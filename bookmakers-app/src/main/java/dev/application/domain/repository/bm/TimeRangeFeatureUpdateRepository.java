package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TimeRangeFeatureUpdateRepository {

	@Update("""
			    UPDATE #{tableName}
			    SET
			        target = #{target},
			        search = #{search};
			    WHERE
			    	id = #{id};
			""")
	int update(@Param("id") String id, @Param("target") String target, @Param("search") String search, @Param("tableName") String tableName);
}
