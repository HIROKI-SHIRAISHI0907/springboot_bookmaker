package dev.web.api.bm_a003;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.master.CountryLeagueMasterWebRepository;
import lombok.RequiredArgsConstructor;

/**
 * CountryLeagueUpdateService
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class CountryLeagueService {

	private final CountryLeagueMasterWebRepository repo;

	@Transactional(readOnly = true)
    public List<CountryLeagueDTO> findAll() {
        return repo.findAll();
    }

	@Transactional(readOnly = true)
    public List<CountryLeagueDTO> search(CountryLeagueSearchCondition cond) {
        return repo.search(cond);
    }

	@Transactional
	public CountryLeagueResponse patchLink(CountryLeagueRequest req) {
		CountryLeagueResponse res = new CountryLeagueResponse();

		// -------------------------
		// 必須チェック
		// -------------------------
		if (isBlank(req.getCountry())
				|| isBlank(req.getLeague())
				|| isBlank(req.getTeam())
				|| isBlank(req.getLink())) {

			res.setResponseCode("400");   // BAD_REQUEST
			res.setMessage("必須項目が未入力です。");
			return res;
		}

		// -------------------------
		// link重複チェック
		// -------------------------
		if (repo.existsLinkOtherThanTeam(
				req.getCountry(),
				req.getLeague(),
				req.getTeam(),
				req.getLink())) {

			res.setResponseCode("409");   // LINK_ALREADY_USED (Conflict)
			res.setMessage("すでに使用されているリンクです。");
			return res;
		}

		try {
			// -------------------------
			// 更新
			// -------------------------
			int updated = repo.updateLink(
					req.getCountry(),
					req.getLeague(),
					req.getTeam(),
					req.getLink());
			if (updated == 1) {
				// SUCCESS
				res.setResponseCode("200");
				res.setMessage("更新成功しました。");
			} else {
				// NOT_FOUND
				res.setResponseCode("404");
				res.setMessage("更新対象が存在しません。");
			}
			return res;
		} catch (DataIntegrityViolationException e) {
		    res.setResponseCode("409");
		    res.setMessage("すでに使用されているリンクです。");
		    return res;
		} catch (Exception e) {
			// ERROR
			res.setResponseCode("500");
			res.setMessage("システムエラーが発生しました。");
			return res;
		}
	}

	/** 更新 */
	public CountryLeagueResponse update(CountryLeagueRequest dto) {
		CountryLeagueResponse res = new CountryLeagueResponse();

		try {
			int updated = repo.updateById(dto.getId(), dto.getCountry(),
					dto.getLeague(), dto.getTeam(), dto.getLink());
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

	private boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}
}
