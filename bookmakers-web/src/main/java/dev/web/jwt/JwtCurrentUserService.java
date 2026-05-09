package dev.web.jwt;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.auth0.jwt.interfaces.DecodedJWT;

import dev.web.repository.user.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtCurrentUserService {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public CurrentUser resolve(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is missing.");
        }

        if (!authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Authorization header.");
        }

        String token = authorizationHeader.substring(7).trim();
        if (token.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT token is missing.");
        }

        final DecodedJWT jwt;
        try {
            jwt = jwtService.verifyToken(token);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT token is invalid.");
        }

        String email = jwt.getSubject();
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject is missing.");
        }

        UserRepository.UserRow user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));

        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(user.userId);
        currentUser.setEmail(user.email);
        currentUser.setAuthFlg(user.authFlg);

        return currentUser;
    }

    @Data
    public static class CurrentUser {
        private Long userId;
        private String email;
        private Integer authFlg;
    }
}
