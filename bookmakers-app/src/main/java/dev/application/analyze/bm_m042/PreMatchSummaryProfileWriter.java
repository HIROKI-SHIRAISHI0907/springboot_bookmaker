package dev.application.analyze.bm_m042;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.PreMatchSummaryProfileRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import lombok.RequiredArgsConstructor;

/**
 * PreMatchSummaryProfileEntity を永続化する Writer です。
 *
 * <p>チーム単位の試合前要約統計プロファイルを1件登録します。</p>
 */
@Component
@RequiredArgsConstructor
public class PreMatchSummaryProfileWriter {

    /** プロジェクト名です。 */
    private static final String PROJECT_NAME = "bookmaker";

    /** クラス名です。 */
    private static final String CLASS_NAME = "PreMatchSummaryProfileWriter";

    /** BM番号です。 */
    private static final String BM_NUMBER = "BM_M042";

    /** 処理名称です。 */
    private static final String PRE_MATCH_SUMMARY_PROFILE = "preMatchSummaryProfile";

    /** Repository です。 */
    private final PreMatchSummaryProfileRepository preMatchSummaryProfileRepository;

    /** 例外ラッパーです。 */
    private final RootCauseWrapper rootCauseWrapper;

    /** ログ管理コンポーネントです。 */
    private final ManageLoggerComponent manageLoggerComponent;

    /**
     * 試合前要約プロファイルを1件登録します。
     *
     * @param entity 登録対象Entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void insert(PreMatchSummaryProfileEntity entity) {
    	final String METHOD_NAME = "insert";
        String fillChar = setLoggerFillChar(entity);

        int result = preMatchSummaryProfileRepository.insert(entity);

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
                        PRE_MATCH_SUMMARY_PROFILE,
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
    private String setLoggerFillChar(PreMatchSummaryProfileEntity entity) {

        StringBuilder sb = new StringBuilder();

        sb.append("[country=").append(entity.getCountry()).append("]");
        sb.append("[league=").append(entity.getLeagueName()).append("]");
        sb.append("[teamId=").append(entity.getTeamId()).append("]");
        sb.append("[teamName=").append(entity.getTeamName()).append("]");
        sb.append("[snapshotDate=").append(entity.getSnapshotDate()).append("]");
        sb.append("[last5ResultString=").append(entity.getLast5ResultString()).append("]");
        sb.append("[last5AvgGoals=").append(entity.getLast5AvgGoals()).append("]");
        sb.append("[last5AvgGoalsConceded=").append(entity.getLast5AvgGoalsConceded()).append("]");
        sb.append("[firstGoalRate=").append(entity.getFirstGoalRate()).append("]");
        sb.append("[winAfterScoringFirstRate=").append(entity.getWinAfterScoringFirstRate()).append("]");
        sb.append("[comebackRate=").append(entity.getComebackRate()).append("]");
        sb.append("[lateScoringRate=").append(entity.getLateScoringRate()).append("]");
        sb.append("[lateConcedingRate=").append(entity.getLateConcedingRate()).append("]");
        sb.append("[setPieceGoalInvolvementRate=").append(entity.getSetPieceGoalInvolvementRate()).append("]");
        sb.append("[cleanSheetRate=").append(entity.getCleanSheetRate()).append("]");
        sb.append("[bothTeamsToScoreRate=").append(entity.getBothTeamsToScoreRate()).append("]");
        sb.append("[note=").append(entity.getNote()).append("]");

        return sb.toString();
    }
}
