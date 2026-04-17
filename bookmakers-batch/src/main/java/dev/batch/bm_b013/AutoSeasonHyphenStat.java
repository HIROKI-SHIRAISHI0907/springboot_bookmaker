package dev.batch.bm_b013;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.master.CountryLeagueSeasonMasterBatchRepository;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.DateUtil;

/**
 * シーズン終了日ハイフン更新ロジック
 * @author shiraishitoshio
 *
 */
@Service
public class AutoSeasonHyphenStat {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = AutoSeasonHyphenStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = AutoSeasonHyphenStat.class.getName();

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
	@Transactional
	public void execute() throws Exception {
		final String METHOD_NAME = "execute";
		// ログ出力
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// システム日時取得
		String sysDate = DateUtil.getSysDate();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		LocalDateTime now = LocalDateTime.parse(sysDate, formatter);

		// シーズン終了日リストを保持
		List<CountryLeagueSeasonMasterEntity> list = countryLeagueSeasonMasterBatchRepository.
			findDateList();
		Map<String, String> countryLeagueMap = list.stream()
				.filter(Objects::nonNull)
				.filter(entity -> entity.getCountry() != null)
				.filter(entity -> entity.getLeague() != null)
				.filter(entity -> entity.getEndSeasonDate() != null)
				.collect(Collectors.toMap(
						entity -> entity.getCountry() + "-" + entity.getLeague(),
						CountryLeagueSeasonMasterEntity::getEndSeasonDate,
						(oldValue, newValue) -> newValue,
						LinkedHashMap::new
				));

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

			// シーズン終了日をシステム日時が超えていたら「---」に更新
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

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	}

}
