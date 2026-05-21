package dev.batch.bm_b010;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.master.FutureMasterRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.entity.FutureEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * 未来データ更新サービスクラス
 * @author shiraishitoshio
 *
 */
@Service
@Transactional
public class FutureStartFlgService {

    private static final String PROJECT_NAME = FutureStartFlgService.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();

    private static final String CLASS_NAME = FutureStartFlgService.class.getName();

    private static final String STRAT_FLG_0 = "0";

    @Autowired
    private FutureMasterRepository futureMasterRepository;

    @Autowired
    private RootCauseWrapper rootCauseWrapper;

    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

    public int execute(Map<String, List<DataEntity>> csvMap) throws Exception {
        final String METHOD_NAME = "execute";

        long startTime = System.nanoTime();

        this.manageLoggerComponent.debugStartInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME, "start");

        if (this.futureMasterRepository.findAll() == 0) {
            this.manageLoggerComponent.debugInfoLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00099I_LOG,
                    "データが存在しません（future_master）");

            this.manageLoggerComponent.debugEndInfoLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, "end");
            return 0;
        }

        if (csvMap != null && !csvMap.isEmpty()) {
            for (Map.Entry<String, List<DataEntity>> map : csvMap.entrySet()) {
                List<DataEntity> list = map.getValue();
                if (list != null && !list.isEmpty()) {
                    String home = list.get(0).getHomeTeamName();
                    String away = list.get(0).getAwayTeamName();
                    if (home != null && away != null) {
                        startFlgUpdate(home, away, STRAT_FLG_0);
                    }
                }
            }
        } else {
            startFlgUpdate(STRAT_FLG_0);
        }

        this.manageLoggerComponent.debugEndInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME, "end");

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                MessageCdConst.MCD00099I_LOG,
                "処理時間(ms): " + durationMs);

        return 0;
    }

    private synchronized void startFlgUpdate(String home, String away, String flg) {
        final String METHOD_NAME = "startFlgUpdate";
        String fillChar = setLoggerFillChar(home, away);

        FutureEntity entity = new FutureEntity();
        entity.setHomeTeamName(home);
        entity.setAwayTeamName(away);

        List<FutureEntity> findList = this.futureMasterRepository.findOnlyTeam(entity);
        if (!findList.isEmpty()) {
            int result = this.futureMasterRepository.updateStartFlg(findList.get(0).getSeq(), flg);
            if (result != 1) {
                String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
                this.rootCauseWrapper.throwUnexpectedRowCount(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                        messageCd,
                        1, result,
                        String.format("home=%s, away=%s", home, away));
            }

            this.manageLoggerComponent.debugInfoLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00006I_UPDATE_SUCCESS,
                    fillChar,
                    "更新件数: 1件");
        }
    }

    private synchronized void startFlgUpdate(String flg) {
        final String METHOD_NAME = "startFlgUpdate";
        int result = -99;
        try {
            result = this.futureMasterRepository.updateFutureTimeFlg(flg);
        } catch (Exception e) {
            String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
            this.manageLoggerComponent.createSystemException(
                    PROJECT_NAME,
                    CLASS_NAME,
                    METHOD_NAME,
                    messageCd,
                    null,
                    e);
        }

        this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                MessageCdConst.MCD00006I_UPDATE_SUCCESS,
                "更新件数: " + result + "件");
    }

    private String setLoggerFillChar(String home, String away) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("ホーム: ").append(home).append(", ");
        stringBuilder.append("アウェー: ").append(away);
        return stringBuilder.toString();
    }
}
