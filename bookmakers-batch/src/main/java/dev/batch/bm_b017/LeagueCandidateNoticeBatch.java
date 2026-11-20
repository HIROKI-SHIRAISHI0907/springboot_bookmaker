package dev.batch.bm_b017;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;

/**
 * 取るべきリーグ候補の通知バッチ（B017） 2026-11-21 追加
 *
 * no-game スクレイパーが出力した「対象外リーグの試合一覧」と、登録済みチーム
 * （country_league_master）を突き合わせ、対象リーグのチームが出場している
 * 対象外リーグを「取るべきリーグの候補」として管理者にメールする。
 *
 * @author shiraishitoshio
 */
@Service("B017")
public class LeagueCandidateNoticeBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = LeagueCandidateNoticeBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = LeagueCandidateNoticeBatch.class.getName();

	/** エラーコード */
	private static final String ERROR_CODE = "BM_B017_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B017";

	@Autowired
	private LeagueCandidateNoticeService leagueCandidateNoticeService;

	@Override
	protected String batchCode() {
		return BATCH_CODE;
	}

	@Override
	protected String errorCode() {
		return ERROR_CODE;
	}

	@Override
	protected String projectName() {
		return PROJECT_NAME;
	}

	@Override
	protected String className() {
		return CLASS_NAME;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doExecute(JobContext ctx) throws Exception {
		this.leagueCandidateNoticeService.execute();
	}
}
