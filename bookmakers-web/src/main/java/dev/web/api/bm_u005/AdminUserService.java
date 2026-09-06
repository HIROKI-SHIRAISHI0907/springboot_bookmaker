package dev.web.api.bm_u005;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.common.util.DateOffsetDecisionUtil;
import dev.web.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(DateOffsetDecisionUtil.getZoneId());

    public AdminUserListResponse getUsers() {
        List<AdminUserItemResponse> users = userRepository.findAllUsers().stream()
                .map(u -> AdminUserItemResponse.builder()
                        .userId(u.userId)
                        .email(u.email)
                        .name(u.name)
                        .authFlg(u.authFlg)
                        .authLabel(toAuthLabel(u.authFlg))
                        .registerTime(u.registerTime == null ? null : FMT.format(u.registerTime.toInstant()))
                        .updateTime(u.updateTime == null ? null : FMT.format(u.updateTime.toInstant()))
                        .build())
                .collect(Collectors.toList());

        return AdminUserListResponse.builder()
                .responseCode("200")
                .message("OK")
                .users(users)
                .build();
    }

    @Transactional
    public AdminUserActionResponse updateAuthFlg(UpdateUserAuthFlgRequest req) {
        if (req.getUserId() == null) {
            return AdminUserActionResponse.builder()
                    .responseCode("400")
                    .message("userId は必須です。")
                    .build();
        }

        if (req.getAuthFlg() == null || !(req.getAuthFlg() == 1 || req.getAuthFlg() == 2
        		|| req.getAuthFlg() == 3)) {
            return AdminUserActionResponse.builder()
                    .responseCode("400")
                    .message("authFlg は 1 〜 3 を指定してください。")
                    .build();
        }

        String op = null;
        switch (req.getAuthFlg()) {
		case 1: {
			op = "admin";
			break;
		}
		case 2: {
			op = "admin_sub";
			break;
		}
		default:
			throw new IllegalArgumentException("Unexpected value: " + req.getAuthFlg());
		}
        int result = userRepository.updateAuthFlg(req.getUserId(), req.getAuthFlg(), op);

        if (result != 1) {
            return AdminUserActionResponse.builder()
                    .responseCode("404")
                    .message("対象ユーザーが見つかりません。")
                    .build();
        }

        return AdminUserActionResponse.builder()
                .responseCode("200")
                .message("権限を更新しました。")
                .build();
    }

    private String toAuthLabel(Integer authFlg) {
        if (authFlg != null && authFlg == 1) {
            return "管理者";
        }
        if (authFlg != null && authFlg == 2) {
            return "担当者";
        }
        return "一般";
    }
}
