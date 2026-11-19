package dev.application.analyze.bm_m000;

import java.util.List;

import org.mapstruct.BeanMapping;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import dev.common.entity.BookDataEntity;
import dev.common.entity.DataEntity;

@Mapper(componentModel = "spring") // Spring を使わないなら省略可
public interface BookToDataMapper {

    @BeanMapping(ignoreByDefault = false) // 同名は自動マップ
    @Mappings({
        // フィールド名が異なるものだけ指定
        @Mapping(target = "file",           source = "filePath"),            // file <- filePath
        @Mapping(target = "dataCategory",   source = "gameTeamCategory"),    // dataCategory <- gameTeamCategory
        @Mapping(target = "times",          source = "time"),                // times <- time
        @Mapping(target = "homeDonation",   source = "homeBallPossesion"),   // ポゼッション（表記揺れ）
        @Mapping(target = "awayDonation",   source = "awayBallPossesion"),
        @Mapping(target = "homeBlockShoot", source = "homeShootBlocked"),    // Blocked ← ShootBlocked
        @Mapping(target = "awayBlockShoot", source = "awayShootBlocked"),
        @Mapping(target = "homeCorner",     source = "homeCornerKick"),      // Corner ← CornerKick
        @Mapping(target = "awayCorner",     source = "awayCornerKick"),
        @Mapping(target = "homeOffside",    source = "homeOffSide"),         // Offside ← OffSide
        @Mapping(target = "awayOffside",    source = "awayOffSide"),
        @Mapping(target = "temparature",    source = "temperature")          // 綴り差異 (temparature <- temperature)
        // 他は同名なので自動でコピーされます
    })
    DataEntity toData(BookDataEntity src);

    @IterableMapping(elementTargetType = DataEntity.class)
    List<DataEntity> toDataList(List<BookDataEntity> srcList);
}
