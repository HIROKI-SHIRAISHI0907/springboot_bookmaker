package dev.web.api.bm_w006;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dev.web.repository.bm.LeaguesRepository;
import dev.web.repository.bm.LeaguesRepository.TeamRow;
import dev.web.repository.bm.PastRankingRepository;
import dev.web.repository.bm.StandingsRepository;
import dev.web.repository.master.CountryLeagueSeasonMasterWebRepository;
import lombok.AllArgsConstructor;

/**
 * StandingsAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
@AllArgsConstructor
public class StandingsAPIService {

	private final LeaguesRepository leagueRepo;

	private final CountryLeagueSeasonMasterWebRepository countryLeagueSeasonMasterWebRepository;

	private final StandingsRepository standingsRepository;

	private final PastRankingRepository pastRankingRepository;

	/**
	 * リーグ順位表を取得してレスポンスに詰める
	 *
	 * @param country 国名（デコード済み推奨）
	 * @param league  リーグ名（デコード済み推奨）
	 * @return LeagueStandingResponse（rows入り。season/updatedAtは現状null）
	 */
	public LeagueStandingResponse getStandings(String country, String league) {

		// Controller でチェックしていても、サービス側でも最低限ガード
		if (!StringUtils.hasText(country) || !StringUtils.hasText(league)) {
			LeagueStandingResponse empty = new LeagueStandingResponse(List.of());
			empty.setSeason(null);
			empty.setUpdatedAt(null);
			return empty;
		}

		List<StandingRowDTO> rows = standingsRepository.findStandings(country, league);

		LeagueStandingResponse body = new LeagueStandingResponse(rows);
		// season / updatedAt は現状設定なし（null）
		body.setSeason(null);
		body.setUpdatedAt(null);

		return body;
	}

	/**
	 * リーグ順位表を取得してレスポンスに詰める
	 *
	 * @param teamEnglish チーム英語
	 * @param teamHash  チームハッシュ
	 * @return LeagueStandingResponse（rows入り。season/updatedAtは現状null）
	 */
	public TeamsStandingsResponse getStandingsBorder(String teamEnglish, String teamHash) {

		TeamRow teamInfo = leagueRepo.findTeamDetailByTeamAndHash(teamEnglish, teamHash);
		if (teamInfo == null)
			return null;

		final String country = teamInfo.getCountry();
		final String league = teamInfo.getLeague();
		final String currentTeamName = normalizeTeamName(teamInfo.getTeam()); // teamInfo.getName() の場合は合わせて

		// 1) seasonYear はマスタの current を使う（ここはあなたの方針どおり）
		String seasonYear = countryLeagueSeasonMasterWebRepository.findCurrentSeasonYear(country, league);
		if (seasonYear == null || seasonYear.isBlank())
			return null;

		// 2) trend は pastRankingRepository から全節×全チーム取得（rank付き）
	    List<TeamStandingsRowDTO> trend = pastRankingRepository.findTrendAllTeams(country, league, seasonYear);
	    if (trend == null || trend.isEmpty()) return null;

	    // 3) latestMatch は trend から算出（DBに別クエリ不要）
	    Integer latestMatch = trend.stream()
	            .map(TeamStandingsRowDTO::getMatch)
	            .filter(java.util.Objects::nonNull)
	            .max(Integer::compareTo)
	            .orElse(null);
	    if (latestMatch == null) return null;

	    // 4) 最新節の順位表（表表示用）を trend から抽出して、太字用フラグ付与
	    List<TeamStandingsRowViewDTO> standings = trend.stream()
	    	    .filter(r -> latestMatch.equals(r.getMatch()))
	    	    .sorted(Comparator.comparing(TeamStandingsRowDTO::getRank,
	    	    	    Comparator.nullsLast(Integer::compareTo)))
	    	    .map((TeamStandingsRowDTO r) -> {
	    	        TeamStandingsRowViewDTO dto = new TeamStandingsRowViewDTO();
	    	        dto.setRank(r.getRank());
	    	        dto.setTeam(r.getTeam());
	    	        dto.setWin(r.getWin());
	    	        dto.setLose(r.getLose());
	    	        dto.setDraw(r.getDraw());
	    	        dto.setWinningPoints(r.getWinningPoints());
	    	        dto.setCurrentTeam(normalizeTeamName(r.getTeam()).equals(currentTeamName));
	    	        return dto;
	    	    })
	    	    .collect(Collectors.toList());

	    return new TeamsStandingsResponse(seasonYear, latestMatch, standings, trend);
	}

	private static String normalizeTeamName(String s) {
		if (s == null)
			return "";
		return s.replaceAll("\\s+", " ").trim();
	}

}
