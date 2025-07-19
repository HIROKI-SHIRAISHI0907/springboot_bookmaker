package dev.application.analyze.bm_m019_bm_m020;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import dev.common.entity.BookDataEntity;

@Mapper
public interface BookDataToMatchClassificationResultMapper {

	@Mapping(source = "gameTeamCategory", target = "dataCategory")
    @Mapping(source = "time", target = "times")
    @Mapping(source = "homeBallPossesion", target = "homeDonation")
    @Mapping(source = "awayBallPossesion", target = "awayDonation")
    @Mapping(source = "homeShootBlocked", target = "homeBlockShoot")
    @Mapping(source = "awayShootBlocked", target = "awayBlockShoot")
    @Mapping(source = "homeCornerKick", target = "homeCorner")
    @Mapping(source = "awayCornerKick", target = "awayCorner")
    @Mapping(source = "homeOffSide", target = "homeOffside")
    @Mapping(source = "awayOffSide", target = "awayOffside")
	@Mapping(target = "classifyMode", expression = "java(classificationMode)")
	MatchClassificationResultEntity mapStruct(BookDataEntity book, String classificationMode);

}
