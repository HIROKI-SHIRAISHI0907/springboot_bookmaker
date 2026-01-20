package dev.web.api.bm_w007;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dev.web.repository.bm.LiveMatchesRepository;

/**
 * LiveMatchesAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
public class LiveMatchesAPIService {

    private final LiveMatchesRepository liveMatchesRepository;

    public LiveMatchesAPIService(LiveMatchesRepository liveMatchesRepository) {
        this.liveMatchesRepository = liveMatchesRepository;
    }

    /**
     * 現在開催中試合一覧を取得。
     * country/league が両方揃っている場合のみ絞り込み、片方欠けている場合は全カテゴリ扱い。
     */
    public List<LiveMatchResponse> getLiveMatches(String country, String league) {

        String c = trimToNull(country);
        String l = trimToNull(league);

        // どちらか欠けていたら全カテゴリ
        if (!StringUtils.hasText(c) || !StringUtils.hasText(l)) {
            c = null;
            l = null;
        }

        return liveMatchesRepository.findLiveMatches(c, l);
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
