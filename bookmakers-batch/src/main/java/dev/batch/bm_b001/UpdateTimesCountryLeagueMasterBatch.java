package dev.batch.bm_b001;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.batch.constant.BatchConstant;
import dev.batch.interf.BatchIF;
import dev.batch.repository.master.CountryLeagueSeasonMasterRepository;
import dev.common.config.PathConfig;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.DateUtil;

/**
 * 国リーグマスタ終了時間更新バッチ。
 * <p>
 * システム日付がマスタの終了日を超えたレコードについて、終了日をクリア（"---" 等）に更新し、
 * 対象となった国・リーグを JSON に書き出す。
 * </p>
 *
 * <p><b>実行方式</b></p>
 * <ul>
 *   <li>開始ログ：{@link ManageLoggerComponent#debugStartInfoLog(String, String, String, String...)}</li>
 *   <li>異常時ログ：{@link ManageLoggerComponent#debugErrorLog(String, String, String, String, Exception, String...)}</li>
 *   <li>終了ログ：{@link ManageLoggerComponent#debugEndInfoLog(String, String, String, String...)}</li>
 *   <li>戻り値で成功/失敗を判定（SUCCESS/ERROR）</li>
 * </ul>
 *
 * <p>
 * 注意：本バッチは複数件更新を伴うため、部分更新を避けたい場合はトランザクション管理を推奨。
 * </p>
 *
 * @author shiraishitoshio
 */
@Service("B001")
public class UpdateTimesCountryLeagueMasterBatch implements BatchIF {

    /** プロジェクト名 */
    private static final String PROJECT_NAME = UpdateTimesCountryLeagueMasterBatch.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();

    /** クラス名 */
    private static final String CLASS_NAME = UpdateTimesCountryLeagueMasterBatch.class.getSimpleName();

    /** エラーコード（運用ルールに合わせて変更） */
    private static final String ERROR_CODE = "BM_B001_ERROR";

    /** CountryLeagueSeasonMasterRepositoryクラス */
    @Autowired
    private CountryLeagueSeasonMasterRepository countryLeagueSeasonMasterRepository;

    /** パス設定 */
    @Autowired
    private PathConfig pathConfig;

    /** ログ管理クラス */
    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

    /** JSON出力 */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * バッチ処理を実行する。
     * <p>
     * 全バッチ共通の実行テンプレートとして、開始/終了ログを必ず出力し、
     * 例外は内部で捕捉して異常終了コードを返却する。
     * </p>
     *
     * @return
     * <ul>
     *   <li>{@link BatchConstant#BATCH_SUCCESS}：正常終了</li>
     *   <li>{@link BatchConstant#BATCH_ERROR}：異常終了</li>
     * </ul>
     */
    @Override
    public int execute() {
        final String METHOD_NAME = "execute";
        this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

        try {
            // システム日付を取得
            String nowDate = DateUtil.getSysDate();

            // JSON保管用パス
            String jsonPath = pathConfig.getB001JsonFolder() + "b001_country_league.json";

            // フォルダが未作成なら作成する
            File dir = new File(pathConfig.getB001JsonFolder());
            if (!dir.exists() && !dir.mkdirs()) {
            	this.manageLoggerComponent.debugErrorLog(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null, "doesnt make folder (" + pathConfig.getB001JsonFolder() + ")"
                );
                return BatchConstant.BATCH_ERROR;
            }

            // 日付がMasterの終了日を超えていたら「---」に更新
            List<CountryLeagueSeasonMasterEntity> expiredDate =
                    this.countryLeagueSeasonMasterRepository.findExpiredByEndDate(nowDate);

            Map<String, Set<String>> countryLeagueMap = new HashMap<>();

            for (CountryLeagueSeasonMasterEntity entity : expiredDate) {
                String country = entity.getCountry();
                String league  = entity.getLeague();

                countryLeagueMap
                        .computeIfAbsent(country, k -> new LinkedHashSet<>())
                        .add(league);

                int result = this.countryLeagueSeasonMasterRepository.clearEndSeasonDate(country, league);
                if (result != 1) {
                	this.manageLoggerComponent.debugErrorLog(
                            PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null, "clearEndSeasonDate result!=1"
                    );
                    return BatchConstant.BATCH_ERROR;
                }
            }

            // JSON出力
            this.objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(jsonPath), countryLeagueMap);

            return BatchConstant.BATCH_SUCCESS;

        } catch (Exception e) {
            this.manageLoggerComponent.debugErrorLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e
            );
            return BatchConstant.BATCH_ERROR;

        } finally {
            this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
        }
    }
}
