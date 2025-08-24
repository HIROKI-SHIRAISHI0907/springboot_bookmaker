package dev.application.analyze.bm_m007_bm_m016;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring") //これをつけないとSpring配下にならない
public interface RegisterDataToTimeRangeFeatureAllLeagueMapper {

	@Mapping(source = "table", target = "tableName")
	TimeRangeFeatureAllLeagueEntity mapStruct(TeamRangeRegisterData registerData);

}
