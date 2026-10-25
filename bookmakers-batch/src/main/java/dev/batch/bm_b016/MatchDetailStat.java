package dev.batch.bm_b016;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.interf.MatchDetailEntityIF;
import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.dto.MatchDetailDataDTO;
import dev.common.s3.S3Operator;
import dev.common.util.FileDeleteUtil;

/**
 * MatchDetailStat 登録ロジック
 *
 * <ol>
 *   <li>試合（S3 の 1 フォルダ）ごとに、match_detail_* の 9 テーブルへ登録する。
 *       1 試合 = 1 トランザクション（{@link MatchDetailDBService#registerMatch}）。
 *       その試合の行を消してから入れ直すので、同じ試合を何度登録しても重複しない（スクレイパーを FORCE=1 で取り直した場合など）。</li>
 *   <li>登録できた試合は、S3 のフォルダ内のファイル（CSV・manifest）を削除する（DB が正になる）。
 *       バケット直下の _done/&lt;試合ID&gt;.json（スクレイパーの取得済みマーカー）は消さないので、同じ試合は再取得されない。</li>
 *   <li>登録に失敗した試合は S3 に残して次回もう一度登録する。他の試合は続ける。最後に 1 件でも失敗があれば例外を投げる（バッチは異常終了）。</li>
 * </ol>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li>match_*.csv が無い（0 行）フォルダは登録しない（試合の基本情報が無いため）。S3 に残るのでログを見て確認する。</li>
 *   <li>スクレイピング時に一部のページが取れなかった試合（manifest の errors）も、取れた分だけ登録する。
 *       取れなかったページは match_detail_match.scrape_errors に残る。</li>
 * </ul>
 *
 * @author shiraishitoshio
 *
 */
@Service
public class MatchDetailStat implements MatchDetailEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MatchDetailStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = MatchDetailStat.class.getName();

	/** Config */
	@Autowired
	private PathConfig config;

	/** S3Operator */
	@Autowired
	private S3Operator s3Operator;

	/** MatchDetailDBService部品 */
	@Autowired
	private MatchDetailDBService matchDetailDBService;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void matchDetailStat(Map<String, MatchDetailDataDTO> map) throws Exception {
		final String METHOD_NAME = "matchDetailStat";
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		String bucket = config.getS3BucketsDetailData();
		int success = 0;
		int skipped = 0;
		List<String> failed = new ArrayList<>();

		for (Map.Entry<String, MatchDetailDataDTO> entry : map.entrySet()) {
			String folderKey = entry.getKey();
			MatchDetailDataDTO data = entry.getValue();

			if (data == null || data.getMatchList() == null || data.getMatchList().isEmpty()) {
				skipped++;
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00003E_EXECUTION_SKIP, null,
						"試合の基本情報（match_*.csv）が無いため登録しません: folder=" + folderKey + "（S3 に残します）");
				continue;
			}

			// 1 試合 = 1 トランザクション
			try {
				int rows = this.matchDetailDBService.registerMatch(data);
				success++;
				this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00005I_INSERT_SUCCESS,
						"登録: folder=" + folderKey + ", 件数=" + rows
								+ (data.getScrapeErrors().isEmpty() ? "" : ", 取れなかったページ=" + data.getScrapeErrors()));
			} catch (Exception e) {
				failed.add(folderKey);
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
						"登録失敗（ロールバック済み。S3 に残して次回もう一度登録します）: folder=" + folderKey);
				continue;
			}

			// 登録できた試合のファイルを削除（削除に失敗しても次回は消してから入れ直すので重複しない）
			FileDeleteUtil.deleteS3Files(
					data.getS3Keys(),
					bucket,
					s3Operator,
					manageLoggerComponent,
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					"MATCH_DETAIL " + folderKey);
		}

		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				MessageCdConst.MCD00099I_LOG,
				"試合詳細データ登録結果: 対象=" + map.size() + " 登録=" + success
						+ " 基本情報なしで未登録=" + skipped + " 失敗=" + failed.size());

		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		if (!failed.isEmpty()) {
			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION
					+ " 試合詳細データの登録に失敗した試合があります: " + failed);
		}
	}
}
