package dev.application.analyze.bm_m044;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.ModelCalibrationDetailRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import lombok.RequiredArgsConstructor;

/**
 * ModelCalibrationDetailEntity を永続化する Writer です。
 *
 * <p>モデルのキャリブレーション詳細を1件登録します。</p>
 */
@Component
@RequiredArgsConstructor
public class ModelCalibrationDetailWriter {

    /** プロジェクト名です。 */
    private static final String PROJECT_NAME = "bookmaker";

    /** クラス名です。 */
    private static final String CLASS_NAME = "ModelCalibrationDetailWriter";

    /** BM番号です。 */
    private static final String BM_NUMBER = "BM_M044";

    /** 処理名称です。 */
    private static final String MODEL_CALIBRATION_DETAIL = "modelCalibrationDetail";

    /** Repository です。 */
    private final ModelCalibrationDetailRepository modelCalibrationDetailRepository;

    /** 例外ラッパーです。 */
    private final RootCauseWrapper rootCauseWrapper;

    /** ログ管理コンポーネントです。 */
    private final ManageLoggerComponent manageLoggerComponent;

    /**
     * モデルのキャリブレーション詳細を1件登録します。
     *
     * @param entity 登録対象Entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void insert(ModelCalibrationDetailEntity entity) {
    	final String METHOD_NAME = "insert";
        String fillChar = setLoggerFillChar(entity);

        int result = modelCalibrationDetailRepository.insert(entity);

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
                        MODEL_CALIBRATION_DETAIL,
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
    private String setLoggerFillChar(ModelCalibrationDetailEntity entity) {

        StringBuilder sb = new StringBuilder();

        sb.append("[modelName=").append(entity.getModelName()).append("]");
        sb.append("[modelVersion=").append(entity.getModelVersion()).append("]");
        sb.append("[targetName=").append(entity.getTargetName()).append("]");
        sb.append("[binIndex=").append(entity.getBinIndex()).append("]");
        sb.append("[predictedProbAvg=").append(entity.getPredictedProbAvg()).append("]");
        sb.append("[actualRate=").append(entity.getActualRate()).append("]");
        sb.append("[sampleCount=").append(entity.getSampleCount()).append("]");

        return sb.toString();
    }
}
