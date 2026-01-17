package dev.web.repository.master;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_w013.TeamMemberDTO;
import dev.web.api.bm_w013.TeamMemberSearchCondition;

/**
 * TeamMemberMasterRepositoryクラス
 * @author shiraishitoshio
 *
 */
@Repository
public class TeamMemberMasterWebRepository {

    private final NamedParameterJdbcTemplate bmJdbcTemplate;

    public TeamMemberMasterWebRepository(
            @Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate
    ) {
        this.bmJdbcTemplate = bmJdbcTemplate;
    }

 // --------------------------------------------------------
    // 一覧: GET /api/team-member-master（全件取得）
    // --------------------------------------------------------
    public List<TeamMemberDTO> findAll() {
        String sql = """
            SELECT
              id,
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
              del_flg
            FROM team_member_master
            ORDER BY id
        """;

        return bmJdbcTemplate.query(sql, (rs, n) -> {
        	TeamMemberDTO dto = new TeamMemberDTO();
            dto.setId(rs.getString("id"));
            dto.setCountry(rs.getString("country"));
            dto.setLeague(rs.getString("league"));
            dto.setTeam(rs.getString("team"));
            dto.setScore(rs.getString("score"));
            dto.setLoanBelong(rs.getString("loan_belong"));
            dto.setJersey(rs.getString("jersey"));
            dto.setMember(rs.getString("member"));
            dto.setFacePicPath(rs.getString("face_pic_path"));
            dto.setBelongList(rs.getString("belong_list"));
            dto.setHeight(rs.getString("height"));
            dto.setWeight(rs.getString("weight"));
            dto.setPosition(rs.getString("position"));
            dto.setBirth(rs.getString("birth"));
            dto.setAge(rs.getString("age"));
            dto.setMarketValue(rs.getString("market_value"));
            dto.setInjury(rs.getString("injury"));
            dto.setVersusTeamScoreData(rs.getString("versus_team_score_data"));
            dto.setRetireFlg(rs.getString("retire_flg"));
            dto.setDeadline(rs.getString("deadline"));
            dto.setDeadlineContractDate(rs.getString("deadline_contract_date"));
            dto.setLatestInfoDate(rs.getString("latest_info_date"));
            dto.setUpdStamp(rs.getString("upd_stamp"));
            dto.setDelFlg(rs.getString("del_flg"));
            return dto;
        });
    }

    // --------------------------------------------------------
    // 条件検索
    // --------------------------------------------------------
    public List<TeamMemberDTO> search(TeamMemberSearchCondition cond) {

        StringBuilder sql = new StringBuilder("""
            SELECT
              id,
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
              del_flg
            FROM team_member_master
            WHERE 1 = 1
        """);

        MapSqlParameterSource params = new MapSqlParameterSource();

        // ---- IF条件 ----
        if (hasText(cond.getCountry())) {
            sql.append(" AND country = :country ");
            params.addValue("country", cond.getCountry());
        }
        if (hasText(cond.getLeague())) {
            sql.append(" AND league = :league ");
            params.addValue("league", cond.getLeague());
        }
        if (hasText(cond.getTeam())) {
            sql.append(" AND team = :team ");
            params.addValue("team", cond.getTeam());
        }
        if (hasText(cond.getMember())) {
            sql.append(" AND member LIKE :member ");
            params.addValue("member", "%" + cond.getMember() + "%");
        }
        if (hasText(cond.getPosition())) {
            sql.append(" AND position = :position ");
            params.addValue("position", cond.getPosition());
        }
        if (hasText(cond.getDelFlg())) {
            sql.append(" AND del_flg = :delFlg ");
            params.addValue("delFlg", cond.getDelFlg());
        }
        sql.append(" ORDER BY id ");

        return bmJdbcTemplate.query(sql.toString(), params, (rs, n) -> {
            TeamMemberDTO dto = new TeamMemberDTO();
            dto.setId(rs.getString("id"));
            dto.setCountry(rs.getString("country"));
            dto.setLeague(rs.getString("league"));
            dto.setTeam(rs.getString("team"));
            dto.setScore(rs.getString("score"));
            dto.setLoanBelong(rs.getString("loan_belong"));
            dto.setJersey(rs.getString("jersey"));
            dto.setMember(rs.getString("member"));
            dto.setFacePicPath(rs.getString("face_pic_path"));
            dto.setBelongList(rs.getString("belong_list"));
            dto.setHeight(rs.getString("height"));
            dto.setWeight(rs.getString("weight"));
            dto.setPosition(rs.getString("position"));
            dto.setBirth(rs.getString("birth"));
            dto.setAge(rs.getString("age"));
            dto.setMarketValue(rs.getString("market_value"));
            dto.setInjury(rs.getString("injury"));
            dto.setVersusTeamScoreData(rs.getString("versus_team_score_data"));
            dto.setRetireFlg(rs.getString("retire_flg"));
            dto.setDeadline(rs.getString("deadline"));
            dto.setDeadlineContractDate(rs.getString("deadline_contract_date"));
            dto.setLatestInfoDate(rs.getString("latest_info_date"));
            dto.setUpdStamp(rs.getString("upd_stamp"));
            dto.setDelFlg(rs.getString("del_flg"));
            return dto;
        });
    }

    // --------------------------------------------------------
    // 存在チェック（キーで1件取れるか）
    // --------------------------------------------------------
    public Integer findIdByUniqueKey(String team, String jersey, String member, String facePicPath) {
        String sql = """
            SELECT id
            FROM team_member_master
            WHERE team = :team
              AND jersey = :jersey
              AND member = :member
              AND face_pic_path = :facePicPath
            LIMIT 1
        """;

        List<Integer> list = bmJdbcTemplate.query(
            sql,
            new MapSqlParameterSource()
                .addValue("team", team)
                .addValue("jersey", jersey)
                .addValue("member", member)
                .addValue("facePicPath", facePicPath),
            (rs, n) -> rs.getInt("id")
        );

        return list.isEmpty() ? null : list.get(0);
    }

    // --------------------------------------------------------
    // 「未登録項目のみ更新」PATCH
    //  - 既に値が入っている列は上書きしない
    // --------------------------------------------------------
    public int patchIfBlank(
            Integer id,
            String height,
            String weight,
            String position,
            String birth,
            String age,
            String marketValue,
            String injury,
            String deadContractLine
    ) {
        String sql = """
            UPDATE team_member_master
            SET
              height = CASE WHEN (height IS NULL OR height = '') THEN :height ELSE height END,
              weight = CASE WHEN (weight IS NULL OR weight = '') THEN :weight ELSE weight END,
              position = CASE WHEN (position IS NULL OR position = '') THEN :position ELSE position END,
              birth = CASE WHEN (birth IS NULL OR birth = '') THEN :birth ELSE birth END,
              age = CASE WHEN (age IS NULL OR age = '') THEN :age ELSE age END,
              market_value = CASE WHEN (market_value IS NULL OR market_value = '') THEN :marketValue ELSE market_value END,
              injury = CASE WHEN (injury IS NULL OR injury = '') THEN :injury ELSE injury END,
              deadContractLine = CASE WHEN (deadContractLine IS NULL OR deadContractLine = '') THEN :deadContractLine ELSE deadContractLine END,
              del_flg = '0'
            WHERE id = :id
        """;

        return bmJdbcTemplate.update(
            sql,
            new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("height", height)
                .addValue("weight", weight)
                .addValue("position", position)
                .addValue("birth", birth)
                .addValue("age", age)
                .addValue("marketValue", marketValue)
                .addValue("injury", injury)
                .addValue("deadContractLine", deadContractLine)
        );
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
