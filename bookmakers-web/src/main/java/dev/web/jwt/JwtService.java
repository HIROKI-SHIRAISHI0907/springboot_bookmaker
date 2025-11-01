package dev.web.jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
/**
 * JWTサービスクラス
 * HEADER.PAYLOAD.SIGNATUREの形式
 *
 * HEADER
 * {
 * "alg": "HS256",
 * "typ": "JWT"
 * }
 *
 * PAYLOAD
 * {
 * "iss": "bookmakers-web", 発行者
 * "iat": 1761991494, 発行日時 (Issued At)
 * "exp": 1761995094, 有効期限
 * "sub": "testuser", 権限リスト
 * "roles": ["ROLE_USER", "ROLE_ADMIN"]
 * }
 *
 * SIGNATURE
 * secret = "test-secret-key" とペイロードからHMAC-SHA256で生成された署名。
 * @author shiraishitoshio
 *
 */
@Service
public class JwtService {

	/** 秘密鍵 */
	@Value("${security.jwt.secret}")
	String secret;

	/** 発行者 */
    @Value("${security.jwt.issuer}")
    String issuer;

    /** 有効期限 */
    @Value("${security.jwt.expires-in}")
    long expiresInSeconds;

    /**
     * JWT作成
     * @param username
     * @param roles
     * @return
     */
    public String generateToken(String username, Collection<String> roles) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        Instant now = Instant.now();

        return JWT.create()
                .withIssuer(issuer)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(expiresInSeconds)))
                .withSubject(username)
                .withClaim("roles", roles.stream().collect(Collectors.toList()))
                .sign(algorithm);
    }

    /**
     * 検証メソッド
     * @param token
     * @return
     */
    public DecodedJWT verifyToken(String token) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        return JWT.require(algorithm)
                .withIssuer(issuer)
                .build()
                .verify(token);
    }
}
