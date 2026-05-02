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

import dev.batch.repository.master.CountryLeagueSeasonMasterBatchRepository;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.DateUtil;

/**
 * シーズンデータ更新Wrapper
 * @author shiraishitoshio
 *
 */
@Service
public class SeasonDataWrapper {

	private static final String PROJECT_NAME = AutoSeasonHyphenTransaction.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = AutoSeasonHyphenTransaction.class.getName();

	/** シーズンバッチレポジトリ */
	@Autowired
	private CountryLeagueSeasonMasterBatchRepository countryLeagueSeasonMasterBatchRepository;

	/** シーズン終了日ハイフン更新ロジック */
	@Autowired
	private AutoSeasonHyphenTransaction autoSeasonHyphenTransaction;

	/** テーブル関係の削除 */
	@Autowired
	private EachTableTransaction eachTableTransaction;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 実行クラス
	 */
	public void execute() {
		final String METHOD_NAME = "execute";
		// ログ出力
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// システム日時取得
		String sysDate = DateUtil.getSysDate();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		LocalDateTime now = LocalDateTime.parse(sysDate, formatter);

		TransactionDTO dto = new TransactionDTO();

		dto.setFormatter(formatter);
		dto.setNow(now);

		// シーズン終了日リストを保持
		List<CountryLeagueSeasonMasterEntity> list = countryLeagueSeasonMasterBatchRepository.findDateList();

		// country-league -> endSeasonDate のMap
		Map<String, String> countryLeagueMap = list.stream()
				.filter(Objects::nonNull)
				.filter(entity -> entity.getCountry() != null)
				.filter(entity -> entity.getLeague() != null)
				.filter(entity -> entity.getEndSeasonDate() != null)
				.collect(Collectors.toMap(
						entity -> entity.getCountry() + "-" + entity.getLeague(),
						CountryLeagueSeasonMasterEntity::getEndSeasonDate,
						(oldValue, newValue) -> newValue,
						LinkedHashMap::new));

		dto.setCountryLeagueMap(countryLeagueMap);

		// country-league の一覧を DTO に保持
		List<String> countryLeagueList = list.stream()
				.filter(Objects::nonNull)
				.filter(entity -> entity.getCountry() != null)
				.filter(entity -> entity.getLeague() != null)
				.map(entity -> entity.getCountry() + "-" + entity.getLeague())
				.collect(Collectors.toList());

		dto.setCountryLeague(countryLeagueList);

		try {
			this.autoSeasonHyphenTransaction.execute(dto);
		} catch (Exception e) {
			// TODO: handle exception
		}

		try {
			this.eachTableTransaction.execute(dto);
		} catch (Exception e) {
			// TODO: handle exception
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	}

}
