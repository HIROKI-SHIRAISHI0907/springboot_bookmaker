package dev.application.main.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.application.analyze.bm_m027.RankingService;
import dev.application.analyze.bm_m098.CsvSeqManageService;
import dev.application.analyze.interf.ServiceIF;
import dev.common.constant.BatchResultConst;
import dev.common.entity.BookDataEntity;
import dev.common.getinfo.GetStatInfo;
import dev.common.logger.ManageLoggerComponent;
import lombok.extern.slf4j.Slf4j;

/**
 * 統計バッチ実行クラス
 * @author shiraishitoshio
 *
 */
@Service
@Slf4j
public class MainStat implements ServiceIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MainStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = MainStat.class.getName();

	/**
	 * 統計情報取得管理クラス
	 */
	@Autowired
	private GetStatInfo getStatInfo;

	/**
	 * CSV管理クラス
	 */
	@Autowired
	private CsvSeqManageService csvSeqManageService;

	/** StatService */
	@Autowired
	private CoreStat statService;

	/** RankingService */
	@Autowired
	private RankingService rankingService;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int execute() throws Exception {
	    final String METHOD_NAME = "execute";
	    manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

	    CsvSeqManageService.CsvSeqRange range = csvSeqManageService.decideRangeOrNull();
	    if (range == null) {
	        log.info("[CsvSeqManageService END] range == null");
	        manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	        return BatchResultConst.BATCH_OK;
	    }
	    log.info("[CsvSeqManageService INFO] range = {}:{}", range.getFrom(), range.getTo());

	    String from = String.valueOf(range.getFrom());
	    String to = String.valueOf(range.getTo());

	    // ✅ まずキーだけ取る（軽い）
	    List<String> keys = getStatInfo.listCsvKeysInRange(from, to);
	    if (keys.isEmpty()) {
	        manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	        return BatchResultConst.BATCH_OK;
	    }
	    log.info("[getStatInfo.listCsvKeysInRange size info] keys.size = {}", keys.size());

	    try {
	        int lastProcessed = range.getFrom() - 1;

	        int process = 1;
	        for (String key : keys) {
	            Map<String, Map<String, List<BookDataEntity>>> oneMap =
	                    getStatInfo.getStatMapForSingleKey(key);

	            if (oneMap.isEmpty()) {
	                lastProcessed = Math.max(lastProcessed, extractSeq(key));
	                process++;
	                continue;
	            }

	            log.info("[String key : keys info] key = {}", key);
	            log.info("[String key : keys info] country-league = {}", oneMap.keySet());

	            log.info("[MainStat] statService start key={}", key);
	            statService.execute(oneMap, false);
	            log.info("[MainStat] statService end key={}", key);

	            log.info("[MainStat] rankingService start key={}", key);
	            rankingService.execute(oneMap, false);
	            log.info("[MainStat] rankingService end key={}", key);

	            int seq = extractSeq(key);
	            lastProcessed = Math.max(lastProcessed, seq);
	            csvSeqManageService.markSuccess(lastProcessed);

	            log.info("[stat calc fin info] csv situation: {}/{}", process, range.getLastOnDb());

	            process++;
	            oneMap.clear();
	        }

	        csvSeqManageService.markSuccess(range.getTo());

	    } catch (Exception e) {
	        log.error("[MainStat] failed", e);
	        return BatchResultConst.BATCH_ERR;
	    }

	    manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	    return BatchResultConst.BATCH_OK;
	}

	private static int extractSeq(String key) {
	    // "6819.csv" -> 6819
	    int dot = key.indexOf('.');
	    if (dot <= 0) return -1;
	    try { return Integer.parseInt(key.substring(0, dot)); } catch (Exception e) { return -1; }
	}

}
