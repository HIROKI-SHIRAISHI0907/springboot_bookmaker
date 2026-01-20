package dev.web.api.bm_w018;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.master.TeamMemberMasterWebRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeamMemberUpdateService {

    private final TeamMemberMasterWebRepository repo;

    @Transactional
    public TeamMemberUpdateResponse patchTeamMember(TeamMemberUpdateRequest req) {
    	TeamMemberUpdateResponse res = new TeamMemberUpdateResponse();

        // 必須（キー）
        if (isBlank(req.getTeam()) || isBlank(req.getJersey())
                || isBlank(req.getMember()) || isBlank(req.getFacePicPath())) {
            res.setResponseCode("400");
            res.setMessage("必須項目（team/jersey/member/facePicPath）が未入力です。");
            return res;
        }

        // 対象取得
        Integer id = repo.findIdByUniqueKey(req.getTeam(), req.getJersey(), req.getMember(), req.getFacePicPath());
        if (id == null) {
            res.setResponseCode("404");
            res.setMessage("更新対象が存在しません。");
            return res;
        }

        try {
            int updated = repo.patchIfBlank(
                id,
                req.getHeight(),
                req.getWeight(),
                req.getPosition(),
                req.getBirth(),
                req.getAge(),
                req.getMarketValue(),
                req.getInjury(),
                req.getDeadlineContractDate()
            );

            if (updated == 1) {
                res.setResponseCode("200");
                res.setMessage("更新成功しました。");
            } else {
                // idで更新なので基本ここには来ない想定だが保険
                res.setResponseCode("404");
                res.setMessage("更新対象が存在しません。");
            }
            return res;

        } catch (DataIntegrityViolationException e) {
            res.setResponseCode("409");
            res.setMessage("更新内容が制約により拒否されました。");
            return res;
        } catch (Exception e) {
            res.setResponseCode("500");
            res.setMessage("システムエラーが発生しました。");
            return res;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
