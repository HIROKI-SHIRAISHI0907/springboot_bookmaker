package dev.web.api.bm_a002;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.api.bm_a021.InitialReadingMasterCsvService;
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

	/** 日付を直したあと b025_fin_season_all_data.json を DB に合わせるため（2026-11-24 追加） */
	private final InitialReadingMasterCsvService initialReadingMasterCsvService;

	private static final java.util.regex.Pattern YMD = java.util.regex.Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

	/**
	 * 開始日・終了日を直す（確認済みの行も直せる。2026-11-24 追加）。
	 * 日付は yyyy-MM-dd（日本時間）。開始は 00:00:00、終了は 23:59:59 で保存する。
	 * 直した日付は手修正の目印が立ち、同じシーズン年の間は B003 が上書きしない。
	 */
	public CountryLeagueSeasonResponse updateDates(CountryLeagueSeasonDTO dto) {
		CountryLeagueSeasonResponse res = new CountryLeagueSeasonResponse();
		String start = dto == null || dto.getStartSeasonDate() == null ? "" : dto.getStartSeasonDate().trim();
		String end = dto == null || dto.getEndSeasonDate() == null ? "" : dto.getEndSeasonDate().trim();
		Integer id;
		try {
			id = Integer.valueOf(dto == null || dto.getId() == null ? "" : dto.getId().trim());
		} catch (NumberFormatException e) {
			res.setResponseCode("400");
			res.setMessage("id が不正です。");
			return res;
		}
		if (!YMD.matcher(start).matches() || !YMD.matcher(end).matches()) {
			res.setResponseCode("400");
			res.setMessage("開始日・終了日を yyyy-MM-dd で入力してください。");
			return res;
		}
		try {
			if (java.time.LocalDate.parse(start).isAfter(java.time.LocalDate.parse(end))) {
				res.setResponseCode("400");
				res.setMessage("開始日は終了日以前にしてください。");
				return res;
			}
		} catch (java.time.format.DateTimeParseException e) {
			res.setResponseCode("400");
			res.setMessage("存在しない日付です。");
			return res;
		}
		try {
			int updated = repo.updateDates(id, start + " 00:00:00+09", end + " 23:59:59+09");
			if (updated != 1) {
				res.setResponseCode("404");
				res.setMessage("更新対象が存在しません（非表示の行は直せません）。");
				return res;
			}
			initialReadingMasterCsvService.uploadFinSeasonAllData();
			res.setResponseCode("200");
			res.setMessage("開始日・終了日を更新しました。");
			return res;
		} catch (Exception e) {
			res.setResponseCode("500");
			res.setMessage("システムエラーが発生しました。");
			return res;
		}
	}

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
