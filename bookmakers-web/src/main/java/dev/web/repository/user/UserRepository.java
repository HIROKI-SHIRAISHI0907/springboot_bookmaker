package dev.web.repository.user;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
 * 2 = 担当者（管理者サブ）ユーザー
 * 3 = 一般ユーザー
 *
 * @author shiraishitoshio
 */
@Repository
@RequiredArgsConstructor
public class UserRepository {
    private final @Qualifier("webUserJdbcTemplate")
    NamedParameterJdbcTemplate jdbc;

    /**
     * 退会済みを表す "authFlg" の値。
     *
     * 【要確認】退会処理そのもの（担当者の "authFlg" をこの値に更新する処理）は
     * 本リポジトリの外（例: AdminUserService 等、今回のzipには含まれていないクラス）で
     * 行われる想定です。実際の退会処理が別の値を使っている場合は、この定数を
     * 実装に合わせて修正してください。
     */
    public static final int AUTH_FLG_WITHDRAWN = 9;

    /** 退会済みユーザーの表示名・メールアドレスの代わりに表示するマスク文字列。 */
    private static final String WITHDRAWN_DISPLAY_NAME = "退会済み";
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
     * 承認フロー（依頼/指令）でJWTのsubject(email)からuserIdを解決するために使用する。
     * @param email
     * @return userIdが見つかった場合はOptionalに包んで返す。存在しない場合は空のOptional。
     */
    public Optional<Long> findUserIdByEmail(String email) {
        String sql = """
            SELECT
                user_id
            FROM users
            WHERE email = :email
        """;
        var params = new MapSqlParameterSource()
            .addValue("email", email);
        var list = jdbc.query(sql, params, (rs, rowNum) -> rs.getLong("user_id"));
        return list.stream().findFirst();
    }
    /**
     * 指定したauthFlgのuser_id一覧を取得する。
     * 承認フローで「指令」を発行する際、その時点の担当者(authFlg=2)全員へ一斉送信するために使用する。
     * @param authFlg
     * @return
     */
    public List<Long> findUserIdsByAuthFlg(Integer authFlg) {
        String sql = """
            SELECT
                user_id
            FROM users
            WHERE "authFlg" = :authFlg
            ORDER BY user_id
        """;
        var params = new MapSqlParameterSource()
            .addValue("authFlg", authFlg);
        return jdbc.query(sql, params, (rs, rowNum) -> rs.getLong("user_id"));
    }
    /**
     * user_idの集合から、画面表示用の名称（nameが未設定ならemail）をまとめて取得する。
     * 承認フロー一覧で、起票者・宛先の担当者の表示名を出すために使用する。
     *
     * <p>対象ユーザーが退会済み（"authFlg" = {@link #AUTH_FLG_WITHDRAWN}）の場合は、
     * name/emailの代わりに「{@value #WITHDRAWN_DISPLAY_NAME}」を返す
     * （退会した担当者の名前・メールアドレスを非表示にする要件のため）。
     *
     * @param userIds
     * @return user_id をキーとした表示名のMap。存在しないuser_idはキーに含まれない。
     */
    public Map<Long, String> findUserNamesByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        String sql = """
            SELECT
                user_id,
                CASE
                    WHEN "authFlg" = :withdrawnFlg THEN :withdrawnDisplayName
                    ELSE COALESCE(name, email)
                END AS display_name
            FROM users
            WHERE user_id IN (:userIds)
        """;
        var params = new MapSqlParameterSource()
            .addValue("userIds", userIds)
            .addValue("withdrawnFlg", AUTH_FLG_WITHDRAWN)
            .addValue("withdrawnDisplayName", WITHDRAWN_DISPLAY_NAME);
        List<Object[]> rows = jdbc.query(sql, params, (rs, rowNum) ->
                new Object[] { rs.getLong("user_id"), rs.getString("display_name") });
        return rows.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (String) row[1]));
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
     * 新規のパスワードに更新する
     * @param email
     * @param passwordHash
     * @param operatorId
     * @return
     */
    public int updatePasswordByEmail(String email, String passwordHash, String operatorId) {
        String sql = """
            UPDATE users
            SET
                "passwordHash" = :passwordHash,
                update_id = :op,
                update_time = CURRENT_TIMESTAMP
            WHERE email = :email
        """;
        var params = new MapSqlParameterSource()
            .addValue("passwordHash", passwordHash)
            .addValue("email", email)
            .addValue("op", operatorId);
        return jdbc.update(sql, params);
    }
    /**
     * 存在するEmailか
     * @param email
     * @return
     */
    public int findEmail(String email) {
        String sql = """
            SELECT
        		COUNT(*)
        	FROM
        		users
            WHERE email = :email
        """;
        var params = new MapSqlParameterSource()
            .addValue("email", email);
        return jdbc.queryForObject(sql, params, Integer.class);
    }
    /**
     * 権限変更の判定(管理者は最大1人・管理者/担当者が0人にならないことのチェック)を
     * 安全に行うため、usersテーブル全行をSELECT FOR UPDATEでロックしたうえで取得する。
     *
     * <p>呼び出し側({@code AdminUserService#updateAuthFlg})は必ず{@code @Transactional}な
     * メソッドの中からこれを呼び、取得したロックを保持したまま人数チェックと
     * {@link #updateAuthFlg(Long, Integer, String)}呼び出しまでを行うこと。
     * トランザクションがコミット/ロールバックされるまでロックは解放されないため、
     * ほぼ同時に来た複数の権限変更リクエストは、このSELECTの時点で直列化される
     * (先に来た方の更新が確定するまで、後続はここで待たされる)。
     *
     * <p>なお、管理者0人の状態から2人が同時に「自分を管理者にする」操作を行うケースを
     * 正しく防ぐには、既存の管理者/担当者の行だけでなく、対象ユーザー(まだ一般ユーザーで
     * 行がauthFlg=1/2ではない場合もある)を含めて競合しうる全行をロックする必要があるため、
     * あえて対象を絞らずテーブル全体をロック対象としている。
     * ユーザー数が非常に多くなる場合は、このテーブル全体ロックがボトルネックになりうる点に注意。
     *
     * @return usersテーブルの全行(ロック済み)
     */
    public List<UserAdminRow> findAllUsersForUpdate() {
        String sql = """
            SELECT
                user_id,
                email,
                name,
                "authFlg" AS auth_flg,
                register_time,
                update_time
            FROM users
            ORDER BY user_id
            FOR UPDATE
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
