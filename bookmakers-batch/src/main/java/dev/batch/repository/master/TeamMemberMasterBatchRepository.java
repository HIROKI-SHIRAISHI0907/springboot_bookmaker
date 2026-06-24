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
			    country,
			    league,
			    team,
			    score,
			    loan_belong            AS loanBelong,
			    jersey,
			    member,
			    face_pic_path          AS facePicPath,
			    belong_list            AS belongList,
			    height,
			    weight,
			    position,
			    birth,
			    age,
			    market_value           AS marketValue,
			    injury,
			    versus_team_score_data AS versusTeamScoreData,
			    retire_flg             AS retireFlg,
			    deadline,
			    deadline_contract_date AS deadlineContractDate,
			    latest_info_date       AS latestInfoDate,
			    upd_stamp              AS updStamp,
			    del_flg                AS delFlg,
			    missing_count          AS missingCount
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
			    upd_stamp,
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
			    COALESCE(NULLIF(#{retireFlg}, ''), '0'),
			    COALESCE(NULLIF(#{deadline}, ''), '0'),
			    #{deadlineContractDate},
			    #{latestInfoDate},
			    COALESCE(NULLIF(#{updStamp}, ''), '0'),
			    COALESCE(NULLIF(#{delFlg}, ''), '0'),
			    COALESCE(NULLIF(#{missingCount}, ''), '0'),
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(TeamMemberMasterEntity entity);

	@Update("""
			UPDATE team_member_master
			   SET country                  = #{country},
			       league                   = #{league},
			       team                     = #{team},
			       score                    = #{score},
			       loan_belong              = #{loanBelong},
			       jersey                   = #{jersey},
			       member                   = #{member},
			       face_pic_path            = #{facePicPath},
			       belong_list              = #{belongList},
			       height                   = #{height},
			       weight                   = #{weight},
			       position                 = #{position},
			       birth                    = #{birth},
			       age                      = #{age},
			       market_value             = #{marketValue},
			       injury                   = #{injury},
			       versus_team_score_data   = #{versusTeamScoreData},
			       retire_flg               = COALESCE(NULLIF(#{retireFlg}, ''), '0'),
			       deadline                 = COALESCE(NULLIF(#{deadline}, ''), '0'),
			       deadline_contract_date   = #{deadlineContractDate},
			       latest_info_date         = #{latestInfoDate},
			       upd_stamp                = COALESCE(NULLIF(#{updStamp}, ''), '0'),
			       del_flg                  = COALESCE(NULLIF(#{delFlg}, ''), '0'),
			       missing_count            = COALESCE(NULLIF(#{missingCount}, ''), '0'),
			       update_id                = 'SYSTEM',
			       update_time              = CURRENT_TIMESTAMP
			 WHERE id = #{id}
			""")
	int updateById(TeamMemberMasterEntity entity);
}
