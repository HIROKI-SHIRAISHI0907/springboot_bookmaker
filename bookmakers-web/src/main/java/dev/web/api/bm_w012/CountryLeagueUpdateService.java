package dev.web.api.bm_w012;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.CountryLeagueMasterRepository;
import lombok.RequiredArgsConstructor;

/**
 * CountryLeagueUpdateService
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class CountryLeagueUpdateService {

	private final CountryLeagueMasterRepository repo;

	@Transactional
	public CountryLeagueUpdateResponse patchLink(CountryLeagueUpdateRequest req) {
		CountryLeagueUpdateResponse res = new CountryLeagueUpdateResponse();

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

	private boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}
}
