package dev.application.analyze.bm_m039;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.MatchTeamMomentumStatsRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import lombok.RequiredArgsConstructor;

/**
 * MatchTeamMomentumStatsEntity を永続化する Writer です。
 *
 * <p>1件単位でモメンタム統計を登録します。</p>
 */
@Component
@RequiredArgsConstructor
public class MatchTeamMomentumStatsWriter {

    /** プロジェクト名です。 */
    private static final String PROJECT_NAME = "bookmaker";

    /** クラス名です。 */
    private static final String CLASS_NAME = "MatchTeamMomentumStatsWriter";

    /** BM番号です。 */
    private static final String BM_NUMBER = "BM_M039";

    /** 処理名称です。 */
    private static final String MATCH_TEAM_MOMENTUM_STATS = "matchTeamMomentumStats";

    /** Repository です。 */
    private final MatchTeamMomentumStatsRepository matchTeamMomentumStatsRepository;

    /** 例外ラッパーです。 */
    private final RootCauseWrapper rootCauseWrapper;

    /** ログ管理コンポーネントです。 */
    private final ManageLoggerComponent manageLoggerComponent;

    /**
     * モメンタム統計を1件登録します。
     *
     * @param entity 登録対象Entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void write(MatchTeamMomentumStatsEntity entity) {
    	final String METHOD_NAME = "write";
        String fillChar = setLoggerFillChar(entity);

        int result = matchTeamMomentumStatsRepository.insert(entity);

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
                "write",
                MessageCdConst.MCD00005I_INSERT_SUCCESS,
                new String[] {
                        BM_NUMBER,
                        MATCH_TEAM_MOMENTUM_STATS,
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
    private String setLoggerFillChar(MatchTeamMomentumStatsEntity entity) {

        StringBuilder sb = new StringBuilder();

        sb.append("[matchId=").append(entity.getMatchId()).append("]");
        sb.append("[country=").append(entity.getCountry()).append("]");
        sb.append("[league=").append(entity.getLeagueName()).append("]");
        sb.append("[team=").append(entity.getTeamName()).append("]");
        sb.append("[opponent=").append(entity.getOpponentTeamName()).append("]");
        sb.append("[asOfSeconds=").append(entity.getAsOfSeconds()).append("]");
        sb.append("[windowMinutes=").append(entity.getWindowMinutes()).append("]");
        sb.append("[momentumTrend=").append(entity.getMomentumTrend()).append("]");

        return sb.toString();
    }
}
