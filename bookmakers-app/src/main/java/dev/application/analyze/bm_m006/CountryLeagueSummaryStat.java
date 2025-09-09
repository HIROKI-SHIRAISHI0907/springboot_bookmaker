package dev.application.analyze.bm_m006;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.CountryLeagueSummaryRepository;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M006統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class CountryLeagueSummaryStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CountryLeagueSummaryStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CountryLeagueSummaryStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M006_COUNTRY_LEAGUE_SUMMARY";

	/** CountryLeagueSummaryRepositoryレポジトリクラス */
	@Autowired
	private CountryLeagueSummaryRepository countryLeagueSummaryRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 結果の登録/更新処理
		// 国-リーグごとの出現回数をカウント
		Map<String, Integer> leagueCountMap = new HashMap<>();
		for (Map.Entry<String, Map<String, List<BookDataEntity>>> countryEntry : entities.entrySet()) {
			Map<String, List<BookDataEntity>> leagueMap = countryEntry.getValue();
			leagueCountMap.merge(countryEntry.getKey(), leagueMap.size(), Integer::sum); // 件数合算
		}

		//  並列で処理
		leagueCountMap.entrySet().parallelStream().forEach(entry -> {
			String leagueEntry = entry.getKey();
			int count = entry.getValue();
			String[] split = ExecuteMainUtil.splitLeagueInfo(leagueEntry);
			String country = split[0];
			String league = split[1];

			// データ取得
			CountryLeagueSummaryOutputDTO dto = getData(country, league);
			String id = dto.getSeq();
			boolean updFlg = dto.isUpdFlg();
			// カウント加算
			String cnt = String.valueOf(Integer.parseInt(dto.getCnt()) + count);
			// 登録 or 更新
			save(id, country, league, dto.getCnt(), cnt, updFlg);
		});

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * 取得データ
	 * @param country 国
	 * @param league リーグ
	 * @return CountryLeagueSummaryOutputDTO
	 */
	private CountryLeagueSummaryOutputDTO getData(String country, String league) {
		CountryLeagueSummaryOutputDTO countryLeagueSummaryOutputDTO = new CountryLeagueSummaryOutputDTO();
		List<CountryLeagueSummaryEntity> datas = this.countryLeagueSummaryRepository.findByCountryLeague(country,
				league);
		if (!datas.isEmpty()) {
			countryLeagueSummaryOutputDTO.setUpdFlg(true);
			countryLeagueSummaryOutputDTO.setSeq(datas.get(0).getId());
			countryLeagueSummaryOutputDTO.setCnt(datas.get(0).getCsvCount());
		} else {
			countryLeagueSummaryOutputDTO.setUpdFlg(false);
			countryLeagueSummaryOutputDTO.setCnt("0");
		}
		return countryLeagueSummaryOutputDTO;
	}

	/**
	 * 登録
	 * @param id ID
	 * @param country 国
	 * @param league リーグ
	 * @param befCnt 前件数
	 * @param cnt 件数
	 * @param updFlg 更新フラグ
	 */
	private synchronized void save(String id, String country, String league, String befCnt,
			String cnt, boolean updFlg) {
		final String METHOD_NAME = "save";

		CountryLeagueSummaryEntity countryLeagueSummaryEntity = new CountryLeagueSummaryEntity();
		countryLeagueSummaryEntity.setCountry(country);
		countryLeagueSummaryEntity.setLeague(league);
		countryLeagueSummaryEntity.setDataCount("0");
		if (updFlg) {
			countryLeagueSummaryEntity.setId(id);
			countryLeagueSummaryEntity.setCsvCount(cnt);
			int result = this.countryLeagueSummaryRepository.update(countryLeagueSummaryEntity);
			if (result != 1) {
				String messageCd = "更新エラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
				this.manageLoggerComponent.createSystemException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						messageCd,
						null);
			}
			String messageCd = "更新件数";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					"BM_M006 更新件数: (country, league)" + "(" + country + ", " + league + ") "
					+ befCnt + "件→" + cnt + "件");
		} else {
			countryLeagueSummaryEntity.setCsvCount(cnt);
			int result = this.countryLeagueSummaryRepository.insert(countryLeagueSummaryEntity);
			if (result != 1) {
				String messageCd = "新規登録エラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
				this.manageLoggerComponent.createSystemException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						messageCd,
						null);
			}
			String messageCd = "登録件数";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					"BM_M006 登録件数: (country, league)" + "(" + country + ", " + league + ") "
							+ "0件→" + cnt + "件");
		}

	}

}
