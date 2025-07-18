package dev.application.domain.repository;

import org.apache.ibatis.annotations.Mapper;
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
	int update(String id, String target, String search, String tableName);
}
