package dev.application.analyze.bm_m005;

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

@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface BookDataToNoGoalMatchMapper {

    @Mapping(source = "seq", target = "seq")
    @Mapping(source = "recordTime", target = "recordTime", qualifiedByName = "stringToTimestamp")
    // ほかの @Mapping はそのまま
    @Mapping(source = "gameTeamCategory", target = "dataCategory")
    @Mapping(source = "time",               target = "times")
    @Mapping(source = "homeBallPossesion", target = "homeDonation")
    @Mapping(source = "awayBallPossesion", target = "awayDonation")
    @Mapping(source = "homeShootBlocked",   target = "homeBlockShoot")
    @Mapping(source = "awayShootBlocked",   target = "awayBlockShoot")
    @Mapping(source = "homeCornerKick",     target = "homeCorner")
    @Mapping(source = "awayCornerKick",     target = "awayCorner")
    @Mapping(source = "homeOffSide",        target = "homeOffside")
    @Mapping(source = "awayOffSide",        target = "awayOffside")
    NoGoalMatchStatisticsEntity mapStruct(BookDataEntity book);

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
