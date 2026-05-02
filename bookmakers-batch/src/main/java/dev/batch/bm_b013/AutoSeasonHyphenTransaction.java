package dev.batch.bm_b013;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.master.CountryLeagueSeasonMasterBatchRepository;
import dev.common.logger.ManageLoggerComponent;

/**
 * シーズン終了日ハイフン更新ロジック
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class AutoSeasonHyphenTransaction {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = AutoSeasonHyphenTransaction.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = AutoSeasonHyphenTransaction.class.getName();

	/** シーズンバッチレポジトリ */
	@Autowired
	private CountryLeagueSeasonMasterBatchRepository countryLeagueSeasonMasterBatchRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 実行メソッド
	 * @throws Exception
	 */
	public void execute(TransactionDTO dto) throws Exception {
		final String METHOD_NAME = "execute";
		Map<String, String> countryLeagueMap = dto.getCountryLeagueMap();
		LocalDateTime now = dto.getNow();
		DateTimeFormatter formatter = dto.getFormatter();

		for (Map.Entry<String, String> entry : countryLeagueMap.entrySet()) {
			String key = entry.getKey();
			String endSeasonDate = entry.getValue();

			if (endSeasonDate == null || endSeasonDate.isBlank()) {
				continue;
			}

			// DB値例: 2026-05-24 00:00:00+09
			// 比較用に先頭19文字だけ使う
			String normalizedEndSeasonDate = endSeasonDate.substring(0, 19);
			LocalDateTime endDateTime = LocalDateTime.parse(normalizedEndSeasonDate, formatter);

			// シーズン終了日をシステム日時が超えていたらNULLに更新
			if (now.isAfter(endDateTime)) {
				String[] keyArray = key.split("-", 2);
				String country = keyArray[0];
				String league = keyArray[1];

				int result = countryLeagueSeasonMasterBatchRepository
						.clearEndSeasonDate(country, league);

				if (result == 0) {
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, null,
							"clearEndSeasonDate result==0 country=" + country + ", league=" + league);
					continue;
				}

				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						"シーズン終了更新: country=" + country + ", league=" + league);
			}
		}

	}

}
