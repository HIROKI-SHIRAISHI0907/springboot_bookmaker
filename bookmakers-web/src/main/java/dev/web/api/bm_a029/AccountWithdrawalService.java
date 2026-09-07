package dev.web.api.bm_a029;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.api.bm_a028.AdminApproveService;
import dev.web.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * 担当者の自己退会処理。
 *
 * 【要確認】担当者の退会（users."authFlg" の更新）を行う処理が、既存の
 * {@code AdminUserService} 等に既に実装されている場合は、そちらとロジックが
 * 重複します。重複する場合はこのクラスは使わず、既存の退会処理の中から
 * {@link AdminApproveService#handleUserWithdrawal(Long)} を同一トランザクションで
 * 呼び出すよう差し替えてください（承認フロー側の後始末はそちらに一本化できます）。
 *
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class AccountWithdrawalService {

    private final UserRepository userRepository;
    private final AdminApproveService approveService;

    /**
     * 担当者本人が自分自身を退会させる。
     *
     * <ol>
     *   <li>{@code users."authFlg"} を {@link UserRepository#AUTH_FLG_WITHDRAWN} に更新する。</li>
     *   <li>{@link AdminApproveService#handleUserWithdrawal(Long)} を呼び出し、
     *       その担当者が起票した依頼・宛先の指令のステータスを後始末する。</li>
     * </ol>
     *
     * 1.と2.は同一トランザクション内で実行するため、片方だけ反映される状態にはならない。
     *
     * <p>「管理者が最後の1人でなくならないか」等の人数制約チェックは行っていない
     * （観点1のヒアリングで示された退会の遷移例では、担当者0人になる遷移が
     * 明示されていなかったため）。人数の下限を設ける必要がある場合は、
     * {@code UserRepository#findAllUsersForUpdate} と同様の行ロックを使った
     * チェックをこのメソッドに追加してください。
     *
     * @param userId     退会するユーザーのuser_id（JWTから解決した本人のuserIdのみを渡すこと。
     *                   他人のuserIdを渡せないよう、呼び出し元〈Controller〉で保証する）
     * @param operatorId users.update_id に記録する操作者ID（本人操作のためuserIdを文字列化して使用）
     * @return 対象ユーザーが見つからず users 行が更新されなかった場合は {@code false}
     */
    @Transactional
    public boolean withdrawSelf(Long userId, String operatorId) {
        int updated = userRepository.updateAuthFlg(userId, UserRepository.AUTH_FLG_WITHDRAWN, operatorId);
        if (updated == 0) {
            return false;
        }
        approveService.handleUserWithdrawal(userId);
        return true;
    }
}
