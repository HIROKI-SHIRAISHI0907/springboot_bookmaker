package dev.application.domain.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.TeamMemberMasterEntity;

@Mapper
public interface TeamMemberMasterRepository {

	@Insert({
	    "INSERT INTO team_member_master (",
	    "country, league, team, score, loan_belong, jersey, member, face_pic_path, belong_list, height, weight, position, birth, age, market_value, injury,",
	    "versus_team_score_data, retire_flg, deadline, deadline_contract_date, latest_info_date, upd_stamp,",
	    "register_id, register_time, update_id, update_time",
	    ") VALUES (",
	    "#{country}, #{league}, #{team}, #{score}, #{loanBelong}, #{jersey}, #{member}, #{facePicPath}, #{belongList}, #{height}, #{weight}, #{position}, #{birth}, #{age}, #{marketValue}, #{injury},",
	    "#{versusTeamScoreData}, #{retireFlg}, #{deadline}, #{deadlineContractDate}, #{latestInfoDate}, #{updStamp},",
	    "#{registerId}, CAST(#{registerTime} AS timestamptz), #{updateId}, CAST(#{updateTime}  AS timestamptz));"
	})
	int insert(TeamMemberMasterEntity entity);

	@Select({
	    "SELECT ",
	    "id, country, league, team, score, loan_belong, jersey, member, face_pic_path, belong_list, height, weight, position, birth, age, market_value, injury,",
	    "versus_team_score_data, retire_flg, deadline, deadline_contract_date, latest_info_date, upd_stamp ",
	    "FROM team_member_master;"
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
	    "upd_stamp = #{updStamp} ",
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
