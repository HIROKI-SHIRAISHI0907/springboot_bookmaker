package dev.application.analyze.bm_m043;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.ModelEvaluationSummaryRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import lombok.RequiredArgsConstructor;

/**
 * ModelEvaluationSummaryEntity を永続化する Writer です。
 *
 * <p>モデル評価サマリを1件登録します。</p>
 */
@Component
@RequiredArgsConstructor
public class ModelEvaluationSummaryWriter {

    /** プロジェクト名です。 */
    private static final String PROJECT_NAME = "bookmaker";

    /** クラス名です。 */
    private static final String CLASS_NAME = "ModelEvaluationSummaryWriter";

    /** BM番号です。 */
    private static final String BM_NUMBER = "BM_M043";

    /** 処理名称です。 */
    private static final String MODEL_EVALUATION_SUMMARY = "modelEvaluationSummary";

    /** Repository です。 */
    private final ModelEvaluationSummaryRepository modelEvaluationSummaryRepository;

    /** 例外ラッパーです。 */
    private final RootCauseWrapper rootCauseWrapper;

    /** ログ管理コンポーネントです。 */
    private final ManageLoggerComponent manageLoggerComponent;

    /**
     * モデル評価サマリを1件登録します。
     *
     * @param entity 登録対象Entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void insert(ModelEvaluationSummaryEntity entity) {
    	final String METHOD_NAME = "insert";
        String fillChar = setLoggerFillChar(entity);

        int result = modelEvaluationSummaryRepository.insert(entity);

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
                        MODEL_EVALUATION_SUMMARY,
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
    private String setLoggerFillChar(ModelEvaluationSummaryEntity entity) {

        StringBuilder sb = new StringBuilder();

        sb.append("[country=").append(entity.getCountry()).append("]");
        sb.append("[league=").append(entity.getLeagueName()).append("]");
        sb.append("[modelName=").append(entity.getModelName()).append("]");
        sb.append("[modelVersion=").append(entity.getModelVersion()).append("]");
        sb.append("[taskType=").append(entity.getTaskType()).append("]");
        sb.append("[targetName=").append(entity.getTargetName()).append("]");
        sb.append("[seasonRange=").append(entity.getSeasonRange()).append("]");
        sb.append("[validationMethod=").append(entity.getValidationMethod()).append("]");
        sb.append("[accuracy=").append(entity.getAccuracy()).append("]");
        sb.append("[precisionScore=").append(entity.getPrecisionScore()).append("]");
        sb.append("[recallScore=").append(entity.getRecallScore()).append("]");
        sb.append("[f1Score=").append(entity.getF1Score()).append("]");
        sb.append("[rocAuc=").append(entity.getRocAuc()).append("]");
        sb.append("[prAuc=").append(entity.getPrAuc()).append("]");
        sb.append("[brierScore=").append(entity.getBrierScore()).append("]");
        sb.append("[mae=").append(entity.getMae()).append("]");
        sb.append("[rmse=").append(entity.getRmse()).append("]");
        sb.append("[mape=").append(entity.getMape()).append("]");
        sb.append("[r2Score=").append(entity.getR2Score()).append("]");
        sb.append("[evaluatedAt=").append(entity.getEvaluatedAt()).append("]");
        sb.append("[note=").append(entity.getNote()).append("]");

        return sb.toString();
    }
}
