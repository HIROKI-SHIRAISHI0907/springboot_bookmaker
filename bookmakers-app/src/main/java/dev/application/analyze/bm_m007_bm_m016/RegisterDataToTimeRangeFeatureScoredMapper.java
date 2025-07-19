package dev.application.analyze.bm_m007_bm_m016;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface RegisterDataToTimeRangeFeatureScoredMapper {

	@Mapping(source = "table", target = "tableName")
	TimeRangeFeatureScoredEntity mapStruct(TeamRangeRegisterData registerData);

}
