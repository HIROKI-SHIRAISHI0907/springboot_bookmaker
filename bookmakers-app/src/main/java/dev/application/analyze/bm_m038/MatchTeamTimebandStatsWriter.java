package dev.application.analyze.bm_m038;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.MatchTeamTimebandStatsRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M038 MatchTeamTimebandStats 登録Writer
 */
@Component
public class MatchTeamTimebandStatsWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MatchTeamTimebandStatsWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = MatchTeamTimebandStatsWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M038";

	/** 備考 */
	private static final String MATCH_TEAM_TIMEBAND_STATS = "matchTeamTimebandStats";

	@Autowired
	private MatchTeamTimebandStatsRepository matchTeamTimebandStatsRepository;

	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * MatchTeamTimebandStats を登録します。
	 *
	 * @param entity 登録対象Entity
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void insert(MatchTeamTimebandStatsEntity entity) {
		final String METHOD_NAME = "insert";

		String fillChar = setLoggerFillChar(
				entity.getMatchId(),
				entity.getCountry(),
				entity.getLeagueName(),
				entity.getTeamName(),
				entity.getOpponentTeamName(),
				entity.getTimeBand() == null ? null : entity.getTimeBand().name(),
				MATCH_TEAM_TIMEBAND_STATS);

		int result = this.matchTeamTimebandStatsRepository.insert(entity);

		if (result != 1) {
			String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
			this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					messageCd, 1, result,
					String.format(
							"matchId=%s, teamId=%s, timeBand=%s",
							entity.getMatchId(),
							entity.getTeamId(),
							entity.getTimeBand()));
		}

		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 登録件数: " + result + "件 (" + fillChar + ")");
	}

	/**
	 * ログ出力用の補足文字列を生成します。
	 *
	 * @param matchId 試合ID
	 * @param country 国
	 * @param leagueName リーグ名
	 * @param teamName チーム名
	 * @param opponentTeamName 対戦相手チーム名
	 * @param timeBand 時間帯
	 * @param bikou 備考
	 * @return ログ用文字列
	 */
	private String setLoggerFillChar(
			String matchId,
			String country,
			String leagueName,
			String teamName,
			String opponentTeamName,
			String timeBand,
			String bikou) {

		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("matchId: ").append(matchId).append(", ");
		stringBuilder.append("国: ").append(country).append(", ");
		stringBuilder.append("リーグ: ").append(leagueName).append(", ");
		stringBuilder.append("チーム: ").append(teamName).append(", ");
		stringBuilder.append("対戦相手: ").append(opponentTeamName).append(", ");
		stringBuilder.append("時間帯: ").append(timeBand).append(", ");
		stringBuilder.append("備考: ").append(bikou);
		return stringBuilder.toString();
	}
}
