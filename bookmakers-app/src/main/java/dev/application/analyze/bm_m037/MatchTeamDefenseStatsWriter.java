package dev.application.analyze.bm_m037;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.MatchTeamDefenseStatsRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M037 MatchTeamDefenseStats 登録Writer
 */
@Component
public class MatchTeamDefenseStatsWriter {

	private static final String PROJECT_NAME = MatchTeamDefenseStatsWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	private static final String CLASS_NAME = MatchTeamDefenseStatsWriter.class.getName();

	private static final String BM_NUMBER = "BM_M037";

	private static final String MATCH_TEAM_DEFENSE_STATS = "matchTeamDefenseStats";

	@Autowired
	private MatchTeamDefenseStatsRepository matchTeamDefenseStatsRepository;

	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void insert(MatchTeamDefenseStatsEntity entity) {
		final String METHOD_NAME = "write";

		String fillChar = setLoggerFillChar(
				entity.getMatchId(),
				entity.getCountry(),
				entity.getLeagueName(),
				entity.getTeamName(),
				entity.getOpponentTeamName(),
				MATCH_TEAM_DEFENSE_STATS);

		int result = this.matchTeamDefenseStatsRepository.insert(entity);

		if (result != 1) {
			String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
			this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					messageCd, 1, result,
					String.format("matchId=%s, teamId=%s", entity.getMatchId(), entity.getTeamId()));
		}

		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 登録件数: " + result + "件 (" + fillChar + ")");
	}

	private String setLoggerFillChar(
			String matchId,
			String country,
			String leagueName,
			String teamName,
			String opponentTeamName,
			String bikou) {

		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("matchId: ").append(matchId).append(", ");
		stringBuilder.append("国: ").append(country).append(", ");
		stringBuilder.append("リーグ: ").append(leagueName).append(", ");
		stringBuilder.append("チーム: ").append(teamName).append(", ");
		stringBuilder.append("対戦相手: ").append(opponentTeamName).append(", ");
		stringBuilder.append("備考: ").append(bikou);
		return stringBuilder.toString();
	}
}
