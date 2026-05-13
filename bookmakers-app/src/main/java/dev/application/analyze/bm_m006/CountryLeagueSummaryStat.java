package dev.application.analyze.bm_m006;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M006統計分析ロジック（手動データ投入の場合は適用対象外）
 * @author shiraishitoshio
 *
 */
@Component
public class CountryLeagueSummaryStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CountryLeagueSummaryStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CountryLeagueSummaryStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M006_COUNTRY_LEAGUE_SUMMARY";

	/** 登録処理 */
	@Autowired
	private CountryLeagueSummaryWriter countryLeagueSummaryWriter;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * キー単位ロック
	 */
	private final ConcurrentHashMap<String, Object> lockMap = new ConcurrentHashMap<>();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		try {
			// 1) 正規化キーで集計 (country|league)
			Map<String, Integer> leagueCountMap = new HashMap<>();
			for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
				String[] sp = ExecuteMainUtil.splitLeagueInfo(entry.getKey());
				if (sp.length < 2) {
					continue;
				}

				String country = normalizeKey(sp[0]);
				String league = normalizeKey(sp[1]);
				String key = country + "|" + league;
				int add = (entry.getValue() == null) ? 0 : entry.getValue().size();
				leagueCountMap.merge(key, add, Integer::sum);
			}

			// 2) 並列実行 + キー単位で同期して保存
			leagueCountMap.entrySet().parallelStream().forEach(e -> {
				String[] sp = e.getKey().split("\\|", 2);
				String country = sp[0];
				String league = sp[1];
				int count = e.getValue();

				Object lock = lockMap.computeIfAbsent(country + "-" + league, k -> new Object());
				synchronized (lock) {
					this.countryLeagueSummaryWriter.upsert(country, league, count);
				}
			});
		} finally {
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	/**
	 * 全角/半角/NBSP/連続空白などを正規化してキーぶれを抑止
	 * @param s
	 * @return
	 */
	private static String normalizeKey(String s) {
		if (s == null) {
			return "";
		}
		String n = Normalizer.normalize(s, Normalizer.Form.NFKC);
		n = n.replace('･', '・').replace('·', '・');
		n = n.replace('\u00A0', ' ').replace('\u3000', ' ');
		n = n.trim().replaceAll("\\s+", " ");
		return n;
	}

}
