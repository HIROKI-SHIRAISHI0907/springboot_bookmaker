package dev.application.analyze.bm_m006;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.bm.CountryLeagueSummaryRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M006統計分析ロジック
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

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M006";

	/** CountryLeagueSummaryRepositoryレポジトリクラス */
	@Autowired
	private CountryLeagueSummaryRepository countryLeagueSummaryRepository;

	/** ログ管理ラッパー*/
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 既存マップ
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

        // 1) 正規化キーで集計 (country|league)
        Map<String, Integer> leagueCountMap = new HashMap<>();
        for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
            String[] sp = ExecuteMainUtil.splitLeagueInfo(entry.getKey());
            if (sp.length < 2) continue;
            String country = normalizeKey(sp[0]);
            String league  = normalizeKey(sp[1]);
            String key = country + "|" + league;
            int add = (entry.getValue() == null) ? 0 : entry.getValue().size();
            leagueCountMap.merge(key, add, Integer::sum);
        }

        // 2) 並列OKだがキー単位で同期して保存（insert or update）
        leagueCountMap.entrySet().parallelStream().forEach(e -> {
            String[] sp = e.getKey().split("\\|", 2);
            String country = sp[0];
            String league  = sp[1];
            int count = e.getValue();

            Object lock = lockMap.computeIfAbsent(country + "-" + league , k -> new Object());
            synchronized (lock) {
                // 先に存在確認
                CountryLeagueSummaryOutputDTO dto = getData(country, league);
                String beforeCnt = dto.getCnt(); // 文字列
                String toAdd     = String.valueOf(count);

                // 保存（重複キーならフォールバック）
                saveWithDuplicateFallback(dto.isUpdFlg(), dto.getSeq(), country, league, beforeCnt, toAdd);
            }
        });

        this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
        this.manageLoggerComponent.clear();
    }

    /**
     * DuplicateKey 時は再読込して update に切替（Repositoryは既存のまま）
     * @param updFlg
     * @param id
     * @param country
     * @param league
     * @param befCnt
     * @param addCnt
     */
    private void saveWithDuplicateFallback(boolean updFlg, String id,
                                           String country, String league,
                                           String befCnt, String addCnt) {
        final String METHOD_NAME = "saveWithDuplicateFallback";
        CountryLeagueSummaryEntity e = new CountryLeagueSummaryEntity();
        e.setCountry(country);
        e.setLeague(league);
        e.setDataCount("0"); // 要件次第で加算するなら同様に扱う
        try {
            if (updFlg) {
                // 既存あり: 上書きではなく加算
                int newCnt = parseOrZero(befCnt) + parseOrZero(addCnt);
                e.setId(id);
                e.setCsvCount(String.valueOf(newCnt));
                int result = this.countryLeagueSummaryRepository.update(e);
                if (result != 1) {
                	String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
    				this.rootCauseWrapper.throwUnexpectedRowCount(
    				        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
    				        messageCd,
    				        1, result,
    				        String.format("id=%s, country=%s, league=%s", id, country, league)
    				    );
                }

                String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
    			this.manageLoggerComponent.debugInfoLog(
    					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, BM_NUMBER + " 更新件数: " + result + "件");
            } else {
                // 新規: まず insert を試す
                e.setCsvCount(addCnt);
                int result = this.countryLeagueSummaryRepository.insert(e);
                if (result == 0) {
                	String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
    				this.rootCauseWrapper.throwUnexpectedRowCount(
    				        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
    				        messageCd,
    				        1, result,
    				        null
    				    );
                }

                String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
    			this.manageLoggerComponent.debugInfoLog(
    					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, BM_NUMBER + " 登録件数: " + result + "件");
            }
        } catch (DuplicateKeyException dup) {
            // 競合: 直近の値を再取得して加算更新
            List<CountryLeagueSummaryEntity> rows =
                this.countryLeagueSummaryRepository.findByCountryLeague(country, league);
            if (!rows.isEmpty()) {
                CountryLeagueSummaryEntity cur = rows.get(0);
                int newCnt = parseOrZero(cur.getCsvCount()) + parseOrZero(addCnt);
                CountryLeagueSummaryEntity u = new CountryLeagueSummaryEntity();
                u.setId(cur.getId());
                u.setCountry(country);
                u.setLeague(league);
                u.setDataCount(cur.getDataCount() == null ? "0" : cur.getDataCount());
                u.setCsvCount(String.valueOf(newCnt));
                int result = this.countryLeagueSummaryRepository.update(u);
                if (result != 1) {
                	String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
                	this.rootCauseWrapper.throwUnexpectedRowCount(
    				        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
    				        messageCd,
    				        1, result,
    				        String.format("id=%s, country=%s, league=%s", cur.getId(), country, league)
    				    );
                }

                String messageCd = MessageCdConst.MCD00009I_REINSERT_DUE_TO_DUPLICATION_OR_COMPETITION;
    			this.manageLoggerComponent.debugInfoLog(
    					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, BM_NUMBER + " 登録件数: " + result + "件");
            } else {
                throw dup; // 理論上ここには来ないはず
            }
        }

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
	 * 0防止
	 * @param s
	 * @return
	 */
	private static int parseOrZero(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    /**
     * 全角/半角/NBSP/連続空白などを正規化してキーぶれを抑止
     * @param s
     * @return
     */
    private static String normalizeKey(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC);
        n = n.replace('･','・').replace('·','・');
        n = n.replace('\u00A0',' ').replace('\u3000',' ');
        n = n.trim().replaceAll("\\s+", " ");
        return n;
    }

}
