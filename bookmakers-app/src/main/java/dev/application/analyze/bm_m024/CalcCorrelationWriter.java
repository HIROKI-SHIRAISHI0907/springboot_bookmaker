package dev.application.analyze.bm_m024;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.CalcCorrelationRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M024 DB永続化専用サービス
 * 登録・更新などDB処理はこのクラスに集約し、個別トランザクションで実行する。
 */
@Service
public class CalcCorrelationWriter {

    /** プロジェクト名（ログ用） */
    private static final String PROJECT_NAME = CalcCorrelationWriter.class
            .getProtectionDomain().getCodeSource().getLocation().getPath();

    /** クラス名（ログ用） */
    private static final String CLASS_NAME = CalcCorrelationWriter.class.getName();

    /** BM_STAT_NUMBER */
    private static final String BM_NUMBER = "BM_M024";

    /** 相関結果登録レポジトリ */
    @Autowired
    private CalcCorrelationRepository calcCorrelationRepository;

    /** 例外ラッパー */
    @Autowired
    private RootCauseWrapper rootCauseWrapper;

    /** ログ管理 */
    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

    /**
     * 相関結果を1件登録する。
     * 1件ごとに独立トランザクションで処理する。
     *
     * @param entity 登録対象
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void insert(CalcCorrelationEntity entity) {
        final String METHOD_NAME = "insert";

        String fillChar = setLoggerFillChar(
                entity.getChkBody(),
                entity.getScore(),
                entity.getCountry(),
                entity.getLeague(),
                entity.getHome(),
                entity.getAway());

        int result = this.calcCorrelationRepository.insert(entity);
        if (result != 1) {
            String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
            this.rootCauseWrapper.throwUnexpectedRowCount(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    messageCd, 1, result, fillChar);
        }

        String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
        this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
                BM_NUMBER + " 登録件数: " + result + "件 (" + fillChar + ")");
    }

    /**
     * ログの埋め字を生成
     */
    private String setLoggerFillChar(String chkBody, String score,
                                     String country, String league,
                                     String home, String away) {
        StringBuilder sb = new StringBuilder();
        sb.append("調査内容: ").append(chkBody).append(", ");
        sb.append("スコア: ").append(score).append(", ");
        sb.append("国: ").append(country).append(", ");
        sb.append("リーグ: ").append(league).append(", ");
        sb.append("ホーム: ").append(home).append(", ");
        sb.append("アウェー: ").append(away);
        return sb.toString();
    }
}
