package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TruncateRepository {

	@Update("TRUNCATE TABLE ${table} RESTART IDENTITY CASCADE;")
	void truncate(String table);

}
