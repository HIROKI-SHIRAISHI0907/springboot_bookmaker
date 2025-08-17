package dev.application.analyze.bm_m007_bm_m016;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import dev.common.entity.BookDataEntity;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BookDataToTimeRangeFeatureMapper {

	@Mapping(source = "book.recordTime", target = "recordTime", qualifiedByName = "stringToTimestamp")
    @Mapping(target = "dataCategory",    source = "book.gameTeamCategory")
    @Mapping(target = "times",           source = "book.time")
    @Mapping(target = "homeDonation",    source = "book.homeBallPossesion")
    @Mapping(target = "awayDonation",    source = "book.awayBallPossesion")
    @Mapping(target = "homeBlockShoot",  source = "book.homeShootBlocked")
    @Mapping(target = "awayBlockShoot",  source = "book.awayShootBlocked")
    @Mapping(target = "homeCorner",      source = "book.homeCornerKick")
    @Mapping(target = "awayCorner",      source = "book.awayCornerKick")
    @Mapping(target = "homeOffside",     source = "book.homeOffSide")
    @Mapping(target = "awayOffside",     source = "book.awayOffSide")
    @Mapping(target = "timeRangeFeatureId", source = "conditionId")
    TimeRangeFeatureEntity mapStruct(BookDataEntity book, String conditionId);

    @Named("stringToTimestamp")
    default Timestamp stringToTimestamp(String value) {
        if (value == null || value.isBlank()) return null;

        // 1) epochミリ秒らしき場合
        if (value.matches("^\\d{10,}$")) {
            try {
                long epoch = Long.parseLong(value);
                return new Timestamp(epoch);
            } catch (NumberFormatException ignore) {}
        }

        // 2) 代表的な日付文字列パターンを順に試す
        String[] patterns = new String[] {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd" // 日付のみ
        };
        for (String p : patterns) {
            try {
                DateTimeFormatter f = DateTimeFormatter.ofPattern(p);
                if (p.contains("HH")) {
                    LocalDateTime ldt = LocalDateTime.parse(value, f);
                    return Timestamp.valueOf(ldt);
                } else {
                    LocalDate ld = LocalDate.parse(value, f);
                    return Timestamp.valueOf(ld.atStartOfDay());
                }
            } catch (DateTimeParseException ignore) {}
        }

        // 3) ISO-8601（Z/オフセット付き）を最後に試す
        try {
            Instant inst = Instant.parse(value);
            return Timestamp.from(inst);
        } catch (DateTimeParseException ignore) {}

        // どうしてもパースできない場合は null（必要なら例外にしてもOK）
        return null;
    }
}
