package dev.web.api.bm_u004;

import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Transactional
    public AuthResponse signUp(SignUpRequest req) {
        String email = norm(req.getEmail()).toLowerCase();
        String password = norm(req.getPassword());
        String name = emptyToNull(norm(req.getName()));
        String op = norm(req.getOperatorId());
        if (op.isEmpty()) op = "system";

        if (email.isEmpty() || password.isEmpty()) {
            return AuthResponse.of("400", "email/password は必須です。");
        }
        if (!EMAIL.matcher(email).matches()) {
            return AuthResponse.of("400", "email形式が不正です。");
        }
        if (password.length() < 8) {
            return AuthResponse.of("400", "password は8文字以上にしてください。");
        }

        if (userRepository.findByEmail(email).isPresent()) {
            return AuthResponse.of("409", "すでに登録済みです。");
        }

        String hash = encoder.encode(password);

        try {
            Long id = userRepository.insertUser(email, hash, name, op);
            return AuthResponse.ok("登録しました。", id);
        } catch (DuplicateKeyException e) {
            return AuthResponse.of("409", "すでに登録済みです。");
        }
    }

    public AuthResponse login(LoginRequest req) {
        String email = norm(req.getEmail()).toLowerCase();
        String password = norm(req.getPassword());

        if (email.isEmpty() || password.isEmpty()) {
            return AuthResponse.of("400", "email/password は必須です。");
        }

        var opt = userRepository.findByEmail(email);
        if (opt.isEmpty()) {
            return AuthResponse.of("401", "email または password が違います。");
        }

        var user = opt.get();
        if (!encoder.matches(password, user.passwordHash)) {
            return AuthResponse.of("401", "email または password が違います。");
        }

        return AuthResponse.ok("ログイン成功。", user.userId);
    }

    private static String norm(String v) {
        if (v == null) return "";
        String s = v.trim();
        if (s.equalsIgnoreCase("null")) return "";
        return s;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
