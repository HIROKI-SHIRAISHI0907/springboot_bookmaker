package dev.application.analyze.bm_m019_bm_m020;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import dev.common.entity.BookDataEntity;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BookDataToMatchClassificationResultMapper {

	@Mapping(source = "book.gameTeamCategory", target = "dataCategory")
    @Mapping(source = "book.time", target = "times")
    @Mapping(source = "book.homeBallPossesion", target = "homeDonation")
    @Mapping(source = "book.awayBallPossesion", target = "awayDonation")
    @Mapping(source = "book.homeShootBlocked", target = "homeBlockShoot")
    @Mapping(source = "book.awayShootBlocked", target = "awayBlockShoot")
    @Mapping(source = "book.homeCornerKick", target = "homeCorner")
    @Mapping(source = "book.awayCornerKick", target = "awayCorner")
    @Mapping(source = "book.homeOffSide", target = "homeOffside")
    @Mapping(source = "book.awayOffSide", target = "awayOffside")
	@Mapping(target = "classifyMode", expression = "java(classificationMode)")
	MatchClassificationResultEntity mapStruct(BookDataEntity book, String classificationMode);

}
