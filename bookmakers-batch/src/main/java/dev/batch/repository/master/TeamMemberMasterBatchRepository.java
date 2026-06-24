package dev.batch.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.TeamMemberMasterEntity;

@Mapper
public interface TeamMemberMasterBatchRepository {

	@Select("""
			SELECT
			    id,
			    file,
			    country,
			    league,
			    team,
			    score,
			    loan_belong         AS loanBelong,
			    jersey,
			    member,
			    face_pic_path       AS facePicPath,
			    belong_list         AS belongList,
			    height,
			    weight,
			    position,
			    birth,
			    age,
			    market_value        AS marketValue,
			    injury,
			    versus_team_score_data AS versusTeamScoreData,
			    retire_flg          AS retireFlg,
			    deadline,
			    deadline_contract_date AS deadlineContractDate,
			    latest_info_date    AS latestInfoDate,
			    upd_stamp           AS updStamp,
			    del_flg             AS delFlg,
			    missing_count       AS missingCount
			FROM team_member_master
			WHERE del_flg = '0' OR del_flg IS NULL
			""")
	List<TeamMemberMasterEntity> selectAll();

	@Insert("""
			INSERT INTO team_member_master (
			         country,
			         league,
			         team,
			         score,
			         loan_belong,
			         jersey,
			         member,
			         face_pic_path,
			         belong_list,
			         height,
			         weight,
			         position,
			         birth,
			         age,
			         market_value,
			         injury,
			         versus_team_score_data,
			         retire_flg,
			         deadline,
			         deadline_contract_date,
			         latest_info_date,
			         del_flg,
			         missing_count,
			    register_id,
			    register_time,
			    update_id,
			    update_time
			) VALUES (
			         #{country},
			         #{league},
			         #{team},
			         #{score},
			         #{loanBelong},
			         #{jersey},
			         #{member},
			         #{facePicPath},
			         #{belongList},
			         #{height},
			         #{weight},
			         #{position},
			         #{birth},
			         #{age},
			         #{marketValue},
			         #{injury},
			         #{versusTeamScoreData},
			         #{retireFlg},
			         #{deadline},
			         #{deadlineContractDate},
			         #{latestInfoDate},
			         #{delFlg},
			         #{missingCount},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insert(TeamMemberMasterEntity entity);

	@Update("""
			UPDATE team_member_master
			   SET
			       country                = #{country},
			       league                 = #{league},
			       team                   = #{team},
			       score                  = #{score},
			       loan_belong            = #{loanBelong},
			       jersey                 = #{jersey},
			       member                 = #{member},
			       face_pic_path          = #{facePicPath},
			       belong_list            = #{belongList},
			       height                 = #{height},
			       weight                 = #{weight},
			       position               = #{position},
			       birth                  = #{birth},
			       age                    = #{age},
			       market_value           = #{marketValue},
			       injury                 = #{injury},
			       versus_team_score_data = #{versusTeamScoreData},
			       retire_flg             = #{retireFlg},
			       deadline               = #{deadline},
			       deadline_contract_date = #{deadlineContractDate},
			       latest_info_date       = #{latestInfoDate},
			       upd_stamp              = #{updStamp},
			       del_flg                = #{delFlg},
			       missing_count          = #{missingCount}
			 WHERE id = #{id}
			""")
	int updateById(TeamMemberMasterEntity entity);
}
