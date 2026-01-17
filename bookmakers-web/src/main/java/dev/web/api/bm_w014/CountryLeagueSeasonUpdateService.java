package dev.web.api.bm_w014;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.master.CountryLeagueSeasonMasterWebRepository;
import lombok.RequiredArgsConstructor;

/**
 * CountryLeagueSeasonUpdateService
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class CountryLeagueSeasonUpdateService {

    private final CountryLeagueSeasonMasterWebRepository repo;

    @Transactional
    public CountryLeagueSeasonUpdateResponse patchLink(CountryLeagueSeasonUpdateRequest req) {
        CountryLeagueSeasonUpdateResponse res = new CountryLeagueSeasonUpdateResponse();

        // 必須チェック
        if (isBlank(req.getCountry())
                || isBlank(req.getLeague())
                || isBlank(req.getSeasonYear())
                || isBlank(req.getPath())) {

            res.setResponseCode("400");
            res.setMessage("必須項目が未入力です。");
            return res;
        }

        // link重複チェック（自分自身は除外）
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
            // DBユニーク制約がある場合の最終防衛線
            res.setResponseCode("409");
            res.setMessage("すでに使用されているリンクです。");
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
