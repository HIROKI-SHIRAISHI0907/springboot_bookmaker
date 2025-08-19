package dev.application.domain.repository;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import dev.application.analyze.bm_m025.CalcCorrelationRankingEntity;

@Mapper
public interface CalcCorrelationRankingRepository {

    @Insert({
        "INSERT INTO `correlation_ranking_data` (",
        " `id`,`country`,`league`,`home`,`away`,`score`,`chkBody`,",
        " `rank_1th`,`rank_2th`,`rank_3th`,`rank_4th`,`rank_5th`,`rank_6th`,`rank_7th`,`rank_8th`,`rank_9th`,`rank_10th`,",
        " `rank_11th`,`rank_12th`,`rank_13th`,`rank_14th`,`rank_15th`,`rank_16th`,`rank_17th`,`rank_18th`,`rank_19th`,`rank_20th`,",
        " `rank_21th`,`rank_22th`,`rank_23th`,`rank_24th`,`rank_25th`,`rank_26th`,`rank_27th`,`rank_28th`,`rank_29th`,`rank_30th`,",
        " `rank_31th`,`rank_32th`,`rank_33th`,`rank_34th`,`rank_35th`,`rank_36th`,`rank_37th`,`rank_38th`,`rank_39th`,`rank_40th`,",
        " `rank_41th`,`rank_42th`,`rank_43th`,`rank_44th`,`rank_45th`,`rank_46th`,`rank_47th`,`rank_48th`,`rank_49th`,`rank_50th`,",
        " `rank_51th`,`rank_52th`,`rank_53th`,`rank_54th`,`rank_55th`,`rank_56th`,`rank_57th`,`rank_58th`,`rank_59th`,`rank_60th`,",
        " `rank_61th`,`rank_62th`,`rank_63th`,`rank_64th`,`rank_65th`,`rank_66th`,`rank_67th`,`rank_68th`,",
        " `register_id`,`register_time`,`update_id`,`update_time`",
        ") VALUES (",
        " #{id},#{country},#{league},#{home},#{away},#{score},#{chkBody},",
        " #{rank1st},#{rank2nd},#{rank3rd},#{rank4th},#{rank5th},#{rank6th},#{rank7th},#{rank8th},#{rank9th},#{rank10th},",
        " #{rank11th},#{rank12th},#{rank13th},#{rank14th},#{rank15th},#{rank16th},#{rank17th},#{rank18th},#{rank19th},#{rank20th},",
        " #{rank21st},#{rank22nd},#{rank23rd},#{rank24th},#{rank25th},#{rank26th},#{rank27th},#{rank28th},#{rank29th},#{rank30th},",
        " #{rank31st},#{rank32nd},#{rank33rd},#{rank34th},#{rank35th},#{rank36th},#{rank37th},#{rank38th},#{rank39th},#{rank40th},",
        " #{rank41st},#{rank42nd},#{rank43rd},#{rank44th},#{rank45th},#{rank46th},#{rank47th},#{rank48th},#{rank49th},#{rank50th},",
        " #{rank51st},#{rank52nd},#{rank53rd},#{rank54th},#{rank55th},#{rank56th},#{rank57th},#{rank58th},#{rank59th},#{rank60th},",
        " #{rank61st},#{rank62nd},#{rank63rd},#{rank64th},#{rank65th},#{rank66th},#{rank67th},#{rank68th},",
        " #{registerId},#{registerTime},#{updateId},#{updateTime}",
        ")"
    })
    int insert(CalcCorrelationRankingEntity entity);

    @Select({
        "SELECT",
        " `id`,`country`,`league`,`home`,`away`,`score`,`chkBody`,",
        " `rank_1th`,`rank_2th`,`rank_3th`,`rank_4th`,`rank_5th`,`rank_6th`,`rank_7th`,`rank_8th`,`rank_9th`,`rank_10th`,",
        " `rank_11th`,`rank_12th`,`rank_13th`,`rank_14th`,`rank_15th`,`rank_16th`,`rank_17th`,`rank_18th`,`rank_19th`,`rank_20th`,",
        " `rank_21th`,`rank_22th`,`rank_23th`,`rank_24th`,`rank_25th`,`rank_26th`,`rank_27th`,`rank_28th`,`rank_29th`,`rank_30th`,",
        " `rank_31th`,`rank_32th`,`rank_33th`,`rank_34th`,`rank_35th`,`rank_36th`,`rank_37th`,`rank_38th`,`rank_39th`,`rank_40th`,",
        " `rank_41th`,`rank_42th`,`rank_43th`,`rank_44th`,`rank_45th`,`rank_46th`,`rank_47th`,`rank_48th`,`rank_49th`,`rank_50th`,",
        " `rank_51th`,`rank_52th`,`rank_53th`,`rank_54th`,`rank_55th`,`rank_56th`,`rank_57th`,`rank_58th`,`rank_59th`,`rank_60th`,",
        " `rank_61th`,`rank_62th`,`rank_63th`,`rank_64th`,`rank_65th`,`rank_66th`,`rank_67th`,`rank_68th`,",
        " `register_id`,`register_time`,`update_id`,`update_time`",
        " FROM `correlation_ranking_data`",
        " WHERE `country` = #{country} AND `league` = #{league} AND `home` = #{home} AND `away` = #{away} AND `score` = #{score} AND `chkBody` = #{chkBody}"
    })
    List<CalcCorrelationRankingEntity> selectByData(String country, String league, String home
    		, String away, String score, String chkBody);
}
