package dev.batch.bm_b001;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.batch.constant.BatchConstant;
import dev.batch.interf.BatchIF;
import dev.batch.repository.master.CountryLeagueSeasonMasterRepository;
import dev.common.config.PathConfig;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.util.DateUtil;

/**
 * 国リーグマスタ終了時間更新バッチ
 * @author shiraishitoshio
 *
 */
public class UpdateTimesCountryLeagueMasterBatch implements BatchIF {

	/** CountryLeagueSeasonMasterRepositoryクラス */
	@Autowired
	private CountryLeagueSeasonMasterRepository countryLeagueSeasonMasterRepository;

	/** パス設定 */
	@Autowired
	private PathConfig pathConfig;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int execute() {
		// システム日付を取得
		String nowDate = DateUtil.getSysDate();
		// JSON保管用パス
		String jsonPath = pathConfig.getB001JsonFolder() + "b001_country_league.json";
		// フォルダが未作成なら作成する
		File dir = new File(pathConfig.getB001JsonFolder());
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                return BatchConstant.BATCH_ERROR;
            }
        }

		Map<String, String> countryLeagueMap = new HashMap<>();

		// 日付がMasterの終了日を超えていたら「---」に更新
		List<CountryLeagueSeasonMasterEntity> expiredDate =
				this.countryLeagueSeasonMasterRepository.findExpiredByEndDate(nowDate);

		for (CountryLeagueSeasonMasterEntity entity : expiredDate) {
			// Map に詰める
	        countryLeagueMap.put(
	                entity.getCountry(),
	                entity.getLeague()
	        );

			int result = this.countryLeagueSeasonMasterRepository
			.clearEndSeasonDate(entity.getCountry(), entity.getLeague());
			// ログ
			if (result != 1) return BatchConstant.BATCH_ERROR;
		}

		// ★ JSON 出力
	    try {
	    	ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter()
                  .writeValue(new File(jsonPath), countryLeagueMap);
	    } catch (Exception e) {
	        return BatchConstant.BATCH_ERROR;
	    }


		return BatchConstant.BATCH_SUCCESS;
	}



}
