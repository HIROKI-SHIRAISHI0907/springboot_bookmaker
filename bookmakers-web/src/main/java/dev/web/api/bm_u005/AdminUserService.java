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

        int newAuthFlg = req.getAuthFlg();

        // usersテーブル全行をSELECT FOR UPDATEでロックしてから判定する。
        // このメソッドは@Transactionalなので、ロックはこの後の人数チェック〜
        // updateAuthFlg()の実行〜メソッド終了(コミット)まで保持され続ける。
        // これにより、ほぼ同時に来た複数の権限変更リクエストは後続がここで待たされ、
        // 「両方が『まだ大丈夫』と判定して同時に更新してしまう」競合を防ぐ。
        var allUsers = userRepository.findAllUsersForUpdate();

        Integer currentAuthFlg = allUsers.stream()
                .filter(u -> u.userId != null && u.userId.equals(req.getUserId()))
                .map(u -> u.authFlg)
                .findFirst()
                .orElse(null);
        if (currentAuthFlg == null) {
            return AdminUserActionResponse.builder()
                    .responseCode("404")
                    .message("対象ユーザーが見つかりません。")
                    .build();
        }

        // ルール1: 管理者は常に最大1人まで。既に(自分以外の)管理者がいる状態での
        // 新規admin化は不可(担当者や一般ユーザーからの昇格、他の担当者との入れ替え含む)。
        if (newAuthFlg == 1) {
            boolean otherAdminExists = allUsers.stream()
                    .anyMatch(u -> u.userId != null && !u.userId.equals(req.getUserId())
                            && u.authFlg != null && u.authFlg == 1);
            if (otherAdminExists) {
                return AdminUserActionResponse.builder()
                        .responseCode("409")
                        .message("管理者は既に1人存在するため、これ以上増やせません。")
                        .build();
            }
        }

        // ルール2: 管理者・担当者は、一度その役割になった人が最後の1人の場合、
        // 別の役割に変える(降格・入れ替え問わず)ことはできない
        // (デフォルトで管理者・担当者が0人の状態自体は問題ない。ここで防ぎたいのは、
        //  最後の1人の管理者/担当者が別の役割に移ることで、その役割が0人になってしまうケース)。
        if (currentAuthFlg != newAuthFlg && (currentAuthFlg == 1 || currentAuthFlg == 2)) {
            int fromRole = currentAuthFlg;
            boolean otherSameRoleExists = allUsers.stream()
                    .anyMatch(u -> u.userId != null && !u.userId.equals(req.getUserId())
                            && u.authFlg != null && u.authFlg == fromRole);
            if (!otherSameRoleExists) {
                String roleName = fromRole == 1 ? "管理者" : "担当者";
                return AdminUserActionResponse.builder()
                        .responseCode("409")
                        .message(roleName + "が1人もいなくなるため、この操作はできません。")
                        .build();
            }
        }

        String op = null;
        switch (newAuthFlg) {
		case 1: {
			op = "admin";
			break;
		}
		case 2: {
			op = "admin_sub";
			break;
		}
		case 3: {
			op = "user";
			break;
		}
		default:
			throw new IllegalArgumentException("Unexpected value: " + newAuthFlg);
		}
        int result = userRepository.updateAuthFlg(req.getUserId(), newAuthFlg, op);
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