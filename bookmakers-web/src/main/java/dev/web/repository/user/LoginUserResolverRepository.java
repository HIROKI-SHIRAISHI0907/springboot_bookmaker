package dev.web.repository.user;

import org.springframework.stereotype.Component;

import dev.web.api.dashboard.auth.DashboardAuthResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ログイン中ユーザーの数値ID（favorites.user_id）を求める。
 * <p>
 * JWT の sub はメールアドレスなので、既存の {@link UserRepository#findByEmail(String)} で userId を引く。
 * 数値が入っている場合はそのまま使う。
 * </p>
 * @author shiraishitoshio
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginUserResolverRepository {

	private final DashboardAuthResolver authResolver;

	private final UserRepository userRepository;

	/** ログイン中ユーザーの ID（未ログイン・見つからない場合は null） */
	public Long currentUserId() {
		String name = this.authResolver.currentUserId();
		Long id = toUserId(name);
		if (id == null) {
			log.debug("[LoginUserResolver] ユーザーIDを特定できません name={}", name);
		}
		return id;
	}

	/** JWT の名前（数値 or メール）→ ユーザーID */
	public Long toUserId(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		String s = name.trim();
		if (s.chars().allMatch(Character::isDigit)) {
			try {
				return Long.valueOf(s);
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return this.userRepository.findByEmail(s.toLowerCase())
				.map(u -> u.userId)
				.orElse(null);
	}
}