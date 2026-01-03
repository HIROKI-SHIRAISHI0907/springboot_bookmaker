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
import dev.common.util.DateUtil;

/**
 * 国リーグマスタ終了時間更新バッチ
 * @author shiraishitoshio
 *
 */
@Service
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

		// 日付がMasterの終了日を超えていたら「---」に更新
		List<CountryLeagueSeasonMasterEntity> expiredDate =
				this.countryLeagueSeasonMasterRepository.findExpiredByEndDate(nowDate);

		Map<String, Set<String>> countryLeagueMap = new HashMap<String, Set<String>>();

		for (CountryLeagueSeasonMasterEntity entity : expiredDate) {
		    String country = entity.getCountry();
		    String league  = entity.getLeague();

		    countryLeagueMap
		        .computeIfAbsent(country, k -> new LinkedHashSet<String>())
		        .add(league);

		    int result = this.countryLeagueSeasonMasterRepository
		        .clearEndSeasonDate(country, league);
		    if (result != 1) return BatchConstant.BATCH_ERROR;
		}

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
