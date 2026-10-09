package dev.web.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.auth0.jwt.interfaces.DecodedJWT;

import dev.web.jwt.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Authorization: Bearer &lt;JWT&gt; を検証し、正しければ SecurityContext にログインユーザーを入れる。
 * <ul>
 *   <li>検証はログイン時に発行している {@link JwtService#verifyToken(String)} と同じ（署名・発行者・有効期限）</li>
 *   <li>sub（メールアドレス）を名前、roles を権限にする</li>
 *   <li>トークンが無い・不正な場合は何もしない（未ログイン扱い。ここでは 401 にしない）</li>
 * </ul>
 * @author shiraishitoshio
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER = "Bearer ";

	private final JwtService jwtService;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith(BEARER)
				&& SecurityContextHolder.getContext().getAuthentication() == null) {
			authenticate(header.substring(BEARER.length()).trim());
		}
		chain.doFilter(request, response);
	}

	private void authenticate(String token) {
		if (token.isEmpty()) {
			return;
		}
		try {
			DecodedJWT jwt = this.jwtService.verifyToken(token);
			String sub = jwt.getSubject();
			if (sub == null || sub.isBlank()) {
				return;
			}
			List<SimpleGrantedAuthority> authorities = new ArrayList<>();
			List<String> roles = jwt.getClaim("roles").asList(String.class);
			if (roles != null) {
				for (String r : roles) {
					authorities.add(new SimpleGrantedAuthority(r));
				}
			}
			SecurityContextHolder.getContext().setAuthentication(
					new UsernamePasswordAuthenticationToken(sub.trim().toLowerCase(), null, authorities));
		} catch (Exception e) {
			// 期限切れ・署名不一致など → 未ログインのまま
			log.debug("[JwtAuthenticationFilter] JWT を検証できません: {}", e.getMessage());
		}
	}
}