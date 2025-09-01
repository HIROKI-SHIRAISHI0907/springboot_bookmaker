package dev.application.domain.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TruncateRepository {

	@Update({
        "Truncate table ${table};"
    })
	void truncate(String table);

}
