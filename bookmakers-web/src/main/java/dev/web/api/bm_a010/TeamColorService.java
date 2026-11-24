package dev.web.api.bm_a010;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.master.TeamColorMasterWebRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * TeamColorService
 * @author shiraishitoshio
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamColorService {

	private final TeamColorMasterWebRepository repo;

	/**
	 * 全件。先にチームマスタと同期する（2026-11-24。チームマスタで有効なチームは必ず有効な行がある・
	 * 外れたチームは無効にして履歴に残す・昇格降格したチームは前のリーグの色を引き継ぐ）
	 */
	public List<TeamColorDTO> findAll() {
		syncSafely();
		return repo.findAll();
	}

	public List<TeamColorDTO> search(TeamColorSearchCondition cond) {
		syncSafely();
		return repo.search(cond);
	}

	/** 同期に失敗しても一覧は出す（ログのみ） */
	private void syncSafely() {
		try {
			log.info("[TeamColor] チームマスタと同期しました: {}", repo.syncWithTeamMaster());
		} catch (Exception e) {
			log.warn("[TeamColor] チームマスタとの同期に失敗しました（一覧はそのまま出します） e={}", e.toString());
		}
	}

	/** 更新 */
	@Transactional
	public TeamColorResponse update(TeamColorRequest dto) {
		TeamColorResponse res = new TeamColorResponse();

		try {
			// 2026-11-24: 有効・無効はチームマスタに合わせて自動で決めるため、画面からは変えない（del_flg は null）
			int updated = repo.update(dto.getCountry(),
					dto.getLeague(), dto.getTeam(), dto.getTeamColorMainHex(),
					dto.getTeamColorSubHex(), null);
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
