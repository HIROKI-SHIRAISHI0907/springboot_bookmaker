package dev.web.repository.user;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * UserRepository
 * @author shiraishitoshio
 *
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
            SELECT user_id, email, "passwordHash" AS password_hash, name
            FROM users
            WHERE email = :email
        """;

        var list = jdbc.query(sql, Map.of("email", email), (rs, rowNum) -> {
            UserRow u = new UserRow();
            u.userId = rs.getLong("user_id");
            u.email = rs.getString("email");
            u.passwordHash = rs.getString("password_hash");
            u.name = rs.getString("name");
            return u;
        });

        return list.stream().findFirst();
    }

    /**
     * 新規登録
     * @param email
     * @param passwordHash
     * @param name
     * @param operatorId
     * @return
     */
    public Long insertUser(String email, String passwordHash, String name, String operatorId) {
        String sql = """
            INSERT INTO users (email, "passwordHash", name, register_id, register_time, update_id, update_time)
            VALUES (:email, :passwordHash, :name, :op, now(), :op, now())
            RETURNING user_id
        """;

        return jdbc.queryForObject(sql, Map.of(
            "email", email,
            "passwordHash", passwordHash,
            "name", name,
            "op", operatorId
        ), Long.class);
    }

    /**
     * UserRow
     * @author shiraishitoshio
     *
     */
    public static class UserRow {
        public Long userId;
        public String email;
        public String passwordHash;
        public String name;
    }
}
