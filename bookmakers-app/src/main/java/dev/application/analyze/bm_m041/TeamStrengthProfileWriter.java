package dev.application.analyze.bm_m041;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.TeamStrengthProfileRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import lombok.RequiredArgsConstructor;

/**
 * TeamStrengthProfileEntity を永続化する Writer です。
 *
 * <p>チーム単位の強度・安定性評価プロファイルを1件登録します。</p>
 */
@Component
@RequiredArgsConstructor
public class TeamStrengthProfileWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamStrengthProfileWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamStrengthProfileWriter.class.getName();

    /** BM番号です。 */
    private static final String BM_NUMBER = "BM_M041";

    /** 処理名称です。 */
    private static final String TEAM_STRENGTH_PROFILE = "teamStrengthProfile";

    /** Repository です。 */
    private final TeamStrengthProfileRepository teamStrengthProfileRepository;

    /** 例外ラッパーです。 */
    private final RootCauseWrapper rootCauseWrapper;

    /** ログ管理コンポーネントです。 */
    private final ManageLoggerComponent manageLoggerComponent;

    /**
     * チーム強度プロファイルを1件登録します。
     *
     * @param entity 登録対象Entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void insert(TeamStrengthProfileEntity entity) {
    	final String METHOD_NAME = "insert";
        String fillChar = setLoggerFillChar(entity);

        int result = teamStrengthProfileRepository.insert(entity);

        if (result != 1) {
        	this.rootCauseWrapper.throwUnexpectedRowCount(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00007E_INSERT_FAILED,
                    1, result,
                    "result=" + result + " " + fillChar);
        }

        manageLoggerComponent.debugInfoLog(
                PROJECT_NAME,
                CLASS_NAME,
                METHOD_NAME,
                MessageCdConst.MCD00005I_INSERT_SUCCESS,
                new String[] {
                        BM_NUMBER,
                        TEAM_STRENGTH_PROFILE,
                        String.valueOf(result),
                        fillChar
                });
    }

    /**
     * ログ出力用の補足文字列を組み立てます。
     *
     * @param entity 対象Entity
     * @return ログ補足文字列
     */
    private String setLoggerFillChar(TeamStrengthProfileEntity entity) {

        StringBuilder sb = new StringBuilder();

        sb.append("[country=").append(entity.getCountry()).append("]");
        sb.append("[league=").append(entity.getLeagueName()).append("]");
        sb.append("[teamId=").append(entity.getTeamId()).append("]");
        sb.append("[teamName=").append(entity.getTeamName()).append("]");
        sb.append("[snapshotDate=").append(entity.getSnapshotDate()).append("]");
        sb.append("[last5Points=").append(entity.getLast5Points()).append("]");
        sb.append("[last5GoalDiff=").append(entity.getLast5GoalDiff()).append("]");
        sb.append("[homeStrengthIndex=").append(entity.getHomeStrengthIndex()).append("]");
        sb.append("[awayStrengthIndex=").append(entity.getAwayStrengthIndex()).append("]");
        sb.append("[eloLikeRating=").append(entity.getEloLikeRating()).append("]");
        sb.append("[formIndex=").append(entity.getFormIndex()).append("]");
        sb.append("[note=").append(entity.getNote()).append("]");

        return sb.toString();
    }
}
