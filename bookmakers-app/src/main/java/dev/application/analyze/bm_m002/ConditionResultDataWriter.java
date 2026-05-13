package dev.application.analyze.bm_m002;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.ConditionResultDataRepository;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

@Service
public class ConditionResultDataWriter {

	private static final String PROJECT_NAME = ConditionResultDataWriter.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();
    private static final String CLASS_NAME = ConditionResultDataWriter.class.getName();

    @Autowired
    private ConditionResultDataRepository conditionResultDataRepository;

    @Autowired
    private RootCauseWrapper rootCauseWrapper;

    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

    @Transactional
    public void save(boolean updFlg, Integer[] updConditionCountIntList,
                     String condition, String hash, String fillChar) {
        final String METHOD_NAME = "save";

        ConditionResultDataEntity conditionResultDataEntity = new ConditionResultDataEntity();
        conditionResultDataEntity.setMailTargetCount(String.valueOf(updConditionCountIntList[0]));
        conditionResultDataEntity.setMailAnonymousTargetCount(String.valueOf(updConditionCountIntList[1]));
        conditionResultDataEntity.setMailTargetSuccessCount(String.valueOf(updConditionCountIntList[2]));
        conditionResultDataEntity.setMailTargetFailCount(String.valueOf(updConditionCountIntList[3]));
        conditionResultDataEntity.setExMailTargetToNoResultCount(String.valueOf(updConditionCountIntList[4]));
        conditionResultDataEntity.setExNoFinDataToNoResultCount(String.valueOf(updConditionCountIntList[5]));
        conditionResultDataEntity.setGoalDelete(String.valueOf(updConditionCountIntList[6]));
        conditionResultDataEntity.setAlterTargetMailAnonymous(String.valueOf(updConditionCountIntList[7]));
        conditionResultDataEntity.setAlterTargetMailFail(String.valueOf(updConditionCountIntList[8]));
        conditionResultDataEntity.setNoResultCount(String.valueOf(updConditionCountIntList[9]));
        conditionResultDataEntity.setErrData(String.valueOf(updConditionCountIntList[10]));
        conditionResultDataEntity.setConditionData(condition);
        conditionResultDataEntity.setHash(hash);

        String messageCd;
        boolean errFlg = false;
        int result;

        if (updFlg) {
            messageCd = "更新";
            result = this.conditionResultDataRepository.update(conditionResultDataEntity);
            if (result != 1) {
                errFlg = true;
                messageCd = "更新エラー";
            }
            fillChar += ", BM_M002 更新件数: 1件";
        } else {
            messageCd = "新規登録";
            result = this.conditionResultDataRepository.insert(conditionResultDataEntity);
            if (result != 1) {
                errFlg = true;
                messageCd = "新規登録エラー";
            }
            fillChar += ", BM_M002 登録件数: 1件";
        }

        if (errFlg) {
            this.rootCauseWrapper.throwUnexpectedRowCount(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    messageCd,
                    1, result,
                    null
            );
        }

        this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar);
    }
}
