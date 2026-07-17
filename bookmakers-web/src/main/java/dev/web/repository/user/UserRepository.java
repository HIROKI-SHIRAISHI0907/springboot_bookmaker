package dev.web.repository.user;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * UserRepository
 *
 * authFlg:
 * 1 = 管理者ユーザー
 * 2 = 一般ユーザー
 *
 * @author shiraishitoshio
 */
@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final @Qualifier("webUserJdbcTemplate")
    NamedParameterJdbcTemplate jdbc;

    /**
     * ログイン用
     * @param email
     * @return
     */
    public Optional<UserRow> findByEmail(String email) {
        String sql = """
            SELECT
                user_id,
                email,
                "passwordHash" AS password_hash,
                name,
                "authFlg" AS auth_flg
            FROM users
            WHERE email = :email
        """;

        var params = new MapSqlParameterSource()
            .addValue("email", email);

        var list = jdbc.query(sql, params, (rs, rowNum) -> {
            UserRow u = new UserRow();
            u.userId = rs.getLong("user_id");
            u.email = rs.getString("email");
            u.passwordHash = rs.getString("password_hash");
            u.name = rs.getString("name");
            u.authFlg = rs.getObject("auth_flg", Integer.class);
            return u;
        });

        return list.stream().findFirst();
    }

    /**
     * 新規登録
     * @param email
     * @param passwordHash
     * @param name
     * @param authFlg
     * @param operatorId
     * @return
     */
    public Long insertUser(String email, String passwordHash, String name, Integer authFlg, String operatorId) {
        String sql = """
            INSERT INTO users (
                email,
                "passwordHash",
                name,
                "authFlg",
                register_id,
                register_time,
                update_id,
                update_time
            )
            VALUES (
                :email,
                :passwordHash,
                :name,
                :authFlg,
                :op,
                CURRENT_TIMESTAMP,
                :op,
                CURRENT_TIMESTAMP
            )
            RETURNING user_id
        """;

        var params = new MapSqlParameterSource()
            .addValue("email", email)
            .addValue("passwordHash", passwordHash)
            .addValue("name", name)
            .addValue("authFlg", authFlg)
            .addValue("op", operatorId);

        return jdbc.queryForObject(sql, params, Long.class);
    }

    /**
     * ユーザー検索
     * @return
     */
    public java.util.List<UserAdminRow> findAllUsers() {
        String sql = """
            SELECT
                user_id,
                email,
                name,
                "authFlg" AS auth_flg,
                register_time,
                update_time
            FROM users
            ORDER BY
                CASE WHEN "authFlg" = 1 THEN 0 ELSE 1 END,
                COALESCE(name, email),
                user_id
        """;

        return jdbc.query(sql, new MapSqlParameterSource(), (rs, rowNum) -> {
            UserAdminRow u = new UserAdminRow();
            u.userId = rs.getLong("user_id");
            u.email = rs.getString("email");
            u.name = rs.getString("name");
            u.authFlg = rs.getObject("auth_flg", Integer.class);
            u.registerTime = rs.getTimestamp("register_time");
            u.updateTime = rs.getTimestamp("update_time");
            return u;
        });
    }

    /**
     * 権限変更
     * @param userId
     * @param authFlg
     * @param operatorId
     * @return
     */
    public int updateAuthFlg(Long userId, Integer authFlg, String operatorId) {
        String sql = """
            UPDATE users
            SET
                "authFlg" = :authFlg,
                update_id = :op,
                update_time = CURRENT_TIMESTAMP
            WHERE user_id = :userId
        """;

        var params = new MapSqlParameterSource()
            .addValue("authFlg", authFlg)
            .addValue("userId", userId)
            .addValue("op", operatorId);

        return jdbc.update(sql, params);
    }

    /**
     * UserAdminRow
     *
     * authFlg:
     * 1 = 管理者ユーザー
     * 2 = 一般ユーザー
     *
     * @author shiraishitoshio
     */
    public static class UserAdminRow {
        public Long userId;
        public String email;
        public String name;
        public Integer authFlg;
        public java.sql.Timestamp registerTime;
        public java.sql.Timestamp updateTime;
    }

    /**
     * UserRow
     *
     * authFlg:
     * 1 = 管理者ユーザー
     * 2 = 一般ユーザー
     *
     * @author shiraishitoshio
     */
    public static class UserRow {
        public Long userId;
        public String email;
        public String passwordHash;
        public String name;
        public Integer authFlg;
    }
}
