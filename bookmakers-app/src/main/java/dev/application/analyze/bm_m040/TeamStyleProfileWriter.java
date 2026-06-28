package dev.application.analyze.bm_m040;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.TeamStyleProfileRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import lombok.RequiredArgsConstructor;

/**
 * TeamStyleProfileEntity を永続化する Writer です。
 *
 * <p>チーム単位のプレースタイルプロファイルを1件登録します。</p>
 */
@Component
@RequiredArgsConstructor
public class TeamStyleProfileWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamStyleProfileWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamStyleProfileWriter.class.getName();

    /** BM番号です。 */
    private static final String BM_NUMBER = "BM_M040";

    /** 処理名称です。 */
    private static final String TEAM_STYLE_PROFILE = "teamStyleProfile";

    /** Repository です。 */
    private final TeamStyleProfileRepository teamStyleProfileRepository;

    /** 例外ラッパーです。 */
    private final RootCauseWrapper rootCauseWrapper;

    /** ログ管理コンポーネントです。 */
    private final ManageLoggerComponent manageLoggerComponent;

    /**
     * チームスタイルプロファイルを1件登録します。
     *
     * @param entity 登録対象Entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void insert(TeamStyleProfileEntity entity) {
    	final String METHOD_NAME = "insert";
        String fillChar = setLoggerFillChar(entity);

        int result = teamStyleProfileRepository.insert(entity);

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
                        TEAM_STYLE_PROFILE,
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
    private String setLoggerFillChar(TeamStyleProfileEntity entity) {

        StringBuilder sb = new StringBuilder();

        sb.append("[country=").append(entity.getCountry()).append("]");
        sb.append("[league=").append(entity.getLeagueName()).append("]");
        sb.append("[teamId=").append(entity.getTeamId()).append("]");
        sb.append("[teamName=").append(entity.getTeamName()).append("]");
        sb.append("[fromDate=").append(entity.getFromDate()).append("]");
        sb.append("[toDate=").append(entity.getToDate()).append("]");
        sb.append("[styleClusterId=").append(entity.getStyleClusterId()).append("]");
        sb.append("[styleLabel=").append(entity.getStyleLabel()).append("]");
        sb.append("[sampleMatchCount=").append(entity.getSampleMatchCount()).append("]");
        sb.append("[styleNote=").append(entity.getStyleNote()).append("]");

        return sb.toString();
    }
}
