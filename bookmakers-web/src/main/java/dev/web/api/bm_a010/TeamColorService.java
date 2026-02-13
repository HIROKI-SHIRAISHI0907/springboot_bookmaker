package dev.web.api.bm_a010;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.master.TeamColorMasterWebRepository;
import lombok.RequiredArgsConstructor;

/**
 * TeamColorService
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class TeamColorService {

	private final TeamColorMasterWebRepository repo;

	@Transactional(readOnly = true)
	public List<TeamColorDTO> findAll() {
		return repo.findAll();
	}

	@Transactional(readOnly = true)
	public List<TeamColorDTO> search(TeamColorSearchCondition cond) {
		return repo.search(cond);
	}

	/** 更新 */
	@Transactional
	public TeamColorResponse update(TeamColorRequest dto) {
		TeamColorResponse res = new TeamColorResponse();

		try {
			int updated = repo.update(dto.getCountry(),
					dto.getLeague(), dto.getTeam(), dto.getTeamColorMainHex(),
					dto.getTeamColorSubHex());
			if (updated == 1) {
				res.setResponseCode("200");
				res.setMessage("更新成功しました。");
				return res;
			}
		} catch (Exception e) {
			res.setResponseCode("500");
			res.setMessage("システムエラーが発生しました。");
			return res;
		}
		return res;
	}

}
