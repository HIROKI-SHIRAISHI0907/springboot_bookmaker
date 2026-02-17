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
	private static final String CLASS_NAME = MainStat.class.getSimpleName();

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
	    this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

	    // ★ 読み取り範囲を決める（DBのlast_success_csv + S3最大から算出）
	    CsvSeqManageService.CsvSeqRange range = csvSeqManageService.decideRangeOrNull();
	    if (range == null) {
	    	log.info("[CsvSeqManageService END] range == null");
	        // 追いついている
	        this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	        return BatchResultConst.BATCH_OK;
	    }
	    log.info("[CsvSeqManageService INFO] range = {}:{}",range.getFrom(),range.getTo());

	    String csvNumber = String.valueOf(range.getFrom());
	    String csvBackNumber = String.valueOf(range.getTo());

	    // ★ その範囲だけ読む
	    Map<String, Map<String, List<BookDataEntity>>> getStatMap =
	            this.getStatInfo.getStatMap(csvNumber, csvBackNumber);

	    try {
	        this.statService.execute(getStatMap);
	        this.rankingService.execute(getStatMap);

	        // ★ 全部成功したら最後に成功した番号を進める
	        csvSeqManageService.markSuccess(range.getTo());

	    } catch (Exception e) {
	        return BatchResultConst.BATCH_ERR;
	    }

	    this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	    return BatchResultConst.BATCH_OK;
	}

}
