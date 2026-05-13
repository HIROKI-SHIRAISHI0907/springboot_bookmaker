package dev.application.analyze.bm_m003;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.TeamMonthlyScoreSummaryRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

@Service
public class TeamMonthlyScoreSummaryWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamMonthlyScoreSummaryWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamMonthlyScoreSummaryWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M003";

	private static final int MONTH_COUNT = 12;

	@Autowired
	private TeamMonthlyScoreSummaryRepository teamMonthlyScoreSummaryRepository;

	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	@Transactional
	public void addMonthlyGoal(String country, String league, String team, String ha,
			String year, int monthIndex, int goalCount) {
		final String METHOD_NAME = "addMonthlyGoal";

		validate(monthIndex, goalCount);

		TeamMonthlyScoreSummaryEntity condition = new TeamMonthlyScoreSummaryEntity();
		condition.setCountry(country);
		condition.setLeague(league);
		condition.setTeamName(team);
		condition.setHa(ha);
		condition.setYear(year);

		List<TeamMonthlyScoreSummaryEntity> entities =
				this.teamMonthlyScoreSummaryRepository.findByCount(condition);

		boolean updFlg = !entities.isEmpty();
		String seq = null;
		String[] seasonCountList = createDefaultSeasonCountList();

		if (updFlg) {
			TeamMonthlyScoreSummaryEntity current = entities.get(0);
			seq = current.getSeq();
			seasonCountList = toSeasonCountList(current);
		}

		int existing = parseCount(seasonCountList[monthIndex]);
		seasonCountList[monthIndex] = String.valueOf(existing + goalCount);

		TeamMonthlyScoreSummaryEntity saveEntity = new TeamMonthlyScoreSummaryEntity();
		saveEntity.setSeq(seq);
		saveEntity.setCountry(country);
		saveEntity.setLeague(league);
		saveEntity.setTeamName(team);
		saveEntity.setHa(ha);
		saveEntity.setYear(year);
		saveEntity.setJanuaryScoreSumCount(seasonCountList[0]);
		saveEntity.setFebruaryScoreSumCount(seasonCountList[1]);
		saveEntity.setMarchScoreSumCount(seasonCountList[2]);
		saveEntity.setAprilScoreSumCount(seasonCountList[3]);
		saveEntity.setMayScoreSumCount(seasonCountList[4]);
		saveEntity.setJuneScoreSumCount(seasonCountList[5]);
		saveEntity.setJulyScoreSumCount(seasonCountList[6]);
		saveEntity.setAugustScoreSumCount(seasonCountList[7]);
		saveEntity.setSeptemberScoreSumCount(seasonCountList[8]);
		saveEntity.setOctoberScoreSumCount(seasonCountList[9]);
		saveEntity.setNovemberScoreSumCount(seasonCountList[10]);
		saveEntity.setDecemberScoreSumCount(seasonCountList[11]);

		if (updFlg) {
			int result = this.teamMonthlyScoreSummaryRepository.update(saveEntity);
			if (result != 1) {
				String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						String.format("id=%s", seq));
			}

			String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, BM_NUMBER + " 更新件数: 1件");
		} else {
			int result = this.teamMonthlyScoreSummaryRepository.insertTeamMonthlyScore(saveEntity);
			if (result != 1) {
				String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						null);
			}

			String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, BM_NUMBER + " 登録件数: 1件");
		}
	}

	private void validate(int monthIndex, int goalCount) {
		if (monthIndex < 0 || monthIndex >= MONTH_COUNT) {
			throw new IllegalArgumentException("monthIndex is invalid.");
		}
		if (goalCount <= 0) {
			throw new IllegalArgumentException("goalCount is invalid.");
		}
	}

	private String[] createDefaultSeasonCountList() {
		return new String[] {
				"0", "0", "0", "0", "0", "0",
				"0", "0", "0", "0", "0", "0"
		};
	}

	private String[] toSeasonCountList(TeamMonthlyScoreSummaryEntity entity) {
		return new String[] {
				nullToZero(entity.getJanuaryScoreSumCount()),
				nullToZero(entity.getFebruaryScoreSumCount()),
				nullToZero(entity.getMarchScoreSumCount()),
				nullToZero(entity.getAprilScoreSumCount()),
				nullToZero(entity.getMayScoreSumCount()),
				nullToZero(entity.getJuneScoreSumCount()),
				nullToZero(entity.getJulyScoreSumCount()),
				nullToZero(entity.getAugustScoreSumCount()),
				nullToZero(entity.getSeptemberScoreSumCount()),
				nullToZero(entity.getOctoberScoreSumCount()),
				nullToZero(entity.getNovemberScoreSumCount()),
				nullToZero(entity.getDecemberScoreSumCount())
		};
	}

	private int parseCount(String value) {
		try {
			return Integer.parseInt(nullToZero(value));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private String nullToZero(String value) {
		return value == null ? "0" : value;
	}
}
