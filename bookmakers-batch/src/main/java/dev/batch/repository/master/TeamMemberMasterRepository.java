package dev.batch.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.TeamMemberMasterEntity;

@Mapper
public interface TeamMemberMasterRepository {

	@Insert({
			"INSERT INTO team_member_master (",
			"country, league, team, score, loan_belong, jersey, member, face_pic_path, belong_list, height, weight, position, birth, age, market_value, injury,",
			"versus_team_score_data, retire_flg, deadline, deadline_contract_date, latest_info_date, upd_stamp, del_flg, ",
			"register_id, register_time, update_id, update_time",
			") VALUES (",
			"#{country}, #{league}, #{team}, #{score}, #{loanBelong}, #{jersey}, #{member}, #{facePicPath}, #{belongList}, #{height}, #{weight}, #{position}, #{birth}, #{age}, #{marketValue}, #{injury},",
			"#{versusTeamScoreData}, #{retireFlg}, #{deadline}, #{deadlineContractDate}, #{latestInfoDate}, #{updStamp}, '0', ",
			"#{registerId}, CAST(#{registerTime} AS timestamptz), #{updateId}, CAST(#{updateTime}  AS timestamptz));"
			//"#{registerId}, #{registerTime}, #{updateId}, #{updateTime});"
	})
	int insert(TeamMemberMasterEntity entity);

	@Select({
			"SELECT ",
			"id, country, league, team, score, loan_belong, jersey, member, face_pic_path, belong_list, height, weight, position, birth, age, market_value, injury,",
			"versus_team_score_data, retire_flg, deadline, deadline_contract_date, latest_info_date, upd_stamp, del_flg ",
			"FROM team_member_master ORDER BY id;"
	})
	@Results(id = "TeamMemberMasterMap", value = {
			@Result(column = "id", property = "id"),
			@Result(column = "country", property = "country"),
			@Result(column = "league", property = "league"),
			@Result(column = "team", property = "team"),
			@Result(column = "score", property = "score"),
			@Result(column = "loan_belong", property = "loanBelong"),
			@Result(column = "jersey", property = "jersey"),
			@Result(column = "member", property = "member"),
			@Result(column = "face_pic_path", property = "facePicPath"),
			@Result(column = "belong_list", property = "belongList"),
			@Result(column = "height", property = "height"),
			@Result(column = "weight", property = "weight"),
			@Result(column = "position", property = "position"),
			@Result(column = "birth", property = "birth"),
			@Result(column = "age", property = "age"),
			@Result(column = "market_value", property = "marketValue"),
			@Result(column = "injury", property = "injury"),
			@Result(column = "versus_team_score_data", property = "versusTeamScoreData"),
			@Result(column = "retire_flg", property = "retireFlg"),
			@Result(column = "deadline", property = "deadline"),
			@Result(column = "deadline_contract_date", property = "deadlineContractDate"),
			@Result(column = "latest_info_date", property = "latestInfoDate"),
			@Result(column = "upd_stamp", property = "updStamp"),
			@Result(column = "del_flg", property = "delFlg"),
	})
	List<TeamMemberMasterEntity> findData();

	@Update({
			"UPDATE team_member_master SET",
			"country = #{country},",
			"league = #{league},",
			"team = #{team},",
			"score = #{score},",
			"loan_belong = #{loanBelong},",
			"jersey = #{jersey},",
			"member = #{member},",
			"face_pic_path = #{facePicPath},",
			"belong_list = #{belongList},",
			"height = #{height},",
			"weight = #{weight},",
			"position = #{position},",
			"birth = #{birth},",
			"age = #{age},",
			"market_value = #{marketValue},",
			"injury = #{injury},",
			"versus_team_score_data = #{versusTeamScoreData},",
			"retire_flg = #{retireFlg},",
			"deadline = #{deadline},",
			"deadline_contract_date = #{deadlineContractDate},",
			"latest_info_date = #{latestInfoDate},",
			"upd_stamp = #{updStamp},",
			"del_flg = '0'",
			"WHERE id = CAST(#{id,jdbcType=VARCHAR} AS INTEGER);"
	})
	int update(TeamMemberMasterEntity entity);

	@Select("""
			    SELECT
			        COUNT(*)
			    FROM
			        team_member_master
			    WHERE
			        member = #{member};
			""")
	int findDataCount(TeamMemberMasterEntity entity);

}
