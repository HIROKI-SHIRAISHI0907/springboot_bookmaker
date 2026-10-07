package dev.web.api.dashboard.auth;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * トップ画面のログイン判定（JWT）。
 * <p>
 * JWT のフィルターが Authorization ヘッダーを検証して SecurityContext に入れている前提。
 * /v1/api/dashboard/** は未ログインでも呼べる（permitAll）が、トークンが付いていればフィルターは検証すること。
 * </p>
 * @author shiraishitoshio
 *
 */
@Component
public class DashboardAuthResolver {

	/**
	 * ログイン中のユーザー（未ログインは null）。
	 * ユーザーIDの取り方が違う場合（principal に独自クラスを入れている等）はここだけ直す。
	 */
	public String currentUserId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
			return null;
		}
		String name = auth.getName();
		return (name == null || name.isBlank()) ? null : name;
	}

	/** ログイン中か */
	public boolean isLoggedIn() {
		return currentUserId() != null;
	}
}
