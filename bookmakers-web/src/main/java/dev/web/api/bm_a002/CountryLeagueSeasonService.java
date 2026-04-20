package dev.web.api.bm_a002;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.master.CountryLeagueSeasonMasterWebRepository;
import dev.web.repository.master.PointSettingWebRepository;
import lombok.RequiredArgsConstructor;

/**
 * CountryLeagueSeasonUpdateService
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class CountryLeagueSeasonService {

	private final CountryLeagueSeasonMasterWebRepository repo;

	private final PointSettingWebRepository pointSettingRepository;

	@Transactional(readOnly = true)
	public List<CountryLeagueSeasonDTO> findAll() {
		return repo.findAll();
	}

	@Transactional(readOnly = true)
	public List<CountryLeagueSeasonDTO> search(CountryLeagueSeasonSearchCondition cond) {
		return repo.search(cond);
	}

	@Transactional
	public CountryLeagueSeasonResponse patchLink(CountryLeagueSeasonRequest req) {
		CountryLeagueSeasonResponse res = new CountryLeagueSeasonResponse();

		if (isBlank(req.getCountry())
				|| isBlank(req.getLeague())
				|| isBlank(req.getSeasonYear())
				|| isBlank(req.getPath())) {

			res.setResponseCode("400");
			res.setMessage("必須項目が未入力です。");
			return res;
		}

		if (repo.existsPathOtherThanKey(
				req.getCountry(),
				req.getLeague(),
				req.getSeasonYear(),
				req.getPath())) {
			res.setResponseCode("409");
			res.setMessage("すでに使用されているリンクです。");
			return res;
		}

		try {
			int updated = repo.updatePath(
					req.getCountry(),
					req.getLeague(),
					req.getSeasonYear(),
					req.getPath());

			if (updated == 1) {
				res.setResponseCode("200");
				res.setMessage("更新成功しました。");
			} else {
				res.setResponseCode("404");
				res.setMessage("更新対象が存在しません。");
			}
			return res;

		} catch (DataIntegrityViolationException e) {
			res.setResponseCode("409");
			res.setMessage("すでに使用されているリンクです。");
			return res;

		} catch (Exception e) {
			res.setResponseCode("500");
			res.setMessage("システムエラーが発生しました。");
			return res;
		}
	}

	/** 更新 */
	@Transactional
	public CountryLeagueSeasonResponse update(CountryLeagueSeasonDTO dto) {
		CountryLeagueSeasonResponse res = new CountryLeagueSeasonResponse();

		try {
			int updated = repo.updateById(
					dto.getId(),
					dto.getCountry(),
					dto.getLeague(),
					dto.getSeasonYear(),
					dto.getPath(),
					dto.getDelFlg());

			if (updated == 1) {
				// point_setting_master 側にも同じ del_flg を反映
				pointSettingRepository.updateDelFlgByCountryAndLeague(
						dto.getCountry(),
						dto.getLeague(),
						dto.getDelFlg());

				res.setResponseCode("200");
				res.setMessage("更新成功しました。");
				return res;
			}

			res.setResponseCode("404");
			res.setMessage("更新対象が存在しません。");
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
