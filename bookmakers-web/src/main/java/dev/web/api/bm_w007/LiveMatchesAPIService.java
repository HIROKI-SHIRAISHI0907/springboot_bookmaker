package dev.web.api.bm_w007;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dev.web.repository.bm.LeaguesRepository;
import dev.web.repository.bm.LeaguesRepository.TeamRow;
import dev.web.repository.bm.LiveMatchesRepository;
import lombok.RequiredArgsConstructor;

/**
 * LiveMatchesAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class LiveMatchesAPIService {

	private final LeaguesRepository leagueRepo;

    private final LiveMatchesRepository liveMatchesRepository;

    /**
     * 現在開催中試合一覧を取得。
     */
    public List<LiveMatchResponse> getLiveMatches(String teamEnglish, String teamHash) {
    	TeamRow teamInfo = leagueRepo.findTeamDetailByTeamAndHash(teamEnglish, teamHash);
    	if (teamInfo == null) return null;

        String c = trimToNull(teamInfo.getCountry());
        String l = trimToNull(teamInfo.getLeague());

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
