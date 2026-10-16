package dev.batch.bm_b005;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.interf.FutureEntityIF;
import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.entity.FutureEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.util.FileDeleteUtil;

/**
 * BM_M022未来データ登録ロジック
 *
 * <p>2026-10 追加: 延期の試合の日程変更</p>
 * <ul>
 *   <li>最初に delay_postpone から延期中の試合を読み（{@link PostponedRescheduleService#loadPostponed()}）、
 *       各ファイルの登録チェックで日時が変わった延期の試合は future_master を上書きする（{@link FutureDBService}）。</li>
 *   <li>全てのファイルの登録が終わったら、日程を変えた試合の延期を delay_postpone から削除する
 *       （途中で例外になった場合は削除しない）。</li>
 * </ul>
 * <p>2026-10 修正（ヘッダしかない CSV が S3 に残り続ける問題）</p>
 * <ul>
 *   <li>GetFutureInfo はヘッダのみのファイルを「キー → 空のリスト」で返す。そのファイルは登録せず、削除対象にだけ入れる
 *       （他のファイルの登録が最後まで終わったら、まとめて削除される）。</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@Service
public class FutureStat implements FutureEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = FutureStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = FutureStat.class.getName();

	/** FutureDBService部品 */
	@Autowired
	private FutureDBService futureDBService;

	/** 延期の試合の日程変更 */
	@Autowired
	private PostponedRescheduleService postponedRescheduleService;

	/** Config */
	@Autowired
	private PathConfig config;

	/** S3Operator */
	@Autowired
	private S3Operator s3Operator;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 * @throws Exception
	 */
	@Override
	@Transactional
	public void futureStat(Map<String, List<FutureEntity>> entities) throws Exception {
		final String METHOD_NAME = "futureStat";
		// ログ出力
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 延期中の試合（読めなければ空 = 日程変更しない）
		List<PostponedRescheduleService.PostponedEntry> postponed = this.postponedRescheduleService.loadPostponed();
		List<PostponedRescheduleService.Rescheduled> rescheduled = new ArrayList<>();

		List<String> insertPath = new ArrayList<String>();
		// 今後の対戦カードを登録する
		int emptyFiles = 0;
		for (Map.Entry<String, List<FutureEntity>> map : entities.entrySet()) {
			String filePath = map.getKey();
			String fillChar = "ファイル名: " + filePath;
			// ヘッダのみ（GetFutureInfo が空のリストで返す）: 登録はせず、削除対象にだけ入れる
			if (map.getValue() == null || map.getValue().isEmpty()) {
				emptyFiles++;
				insertPath.add(filePath);
				continue;
			}
			try {
				List<FutureEntity> insertEntities = this.futureDBService.selectInBatch(
						map.getValue(), fillChar, postponed, rescheduled);
				this.futureDBService.insertInBatchOrThrow(insertEntities);
				insertPath.add(filePath);
			} catch (Exception e) {
				String messageCd = "システムエラー";
				throw new Exception(messageCd, e);
			}
		}

		if (emptyFiles > 0) {
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"登録対象0件（ヘッダのみ）のファイル: " + emptyFiles + "件（登録せず削除します）");
		}

		// 日程を変えた試合の延期を delay_postpone から削除する（失敗してもログのみ）
		this.postponedRescheduleService.removeFromDelayPostpone(rescheduled);

		// 途中で例外が起きなければ全てのファイルを削除する
		String bucket = config.getS3BucketsFuture(); // バケット名取得
		FileDeleteUtil.deleteS3Files(
				insertPath,
				bucket,
				s3Operator,
				manageLoggerComponent,
				PROJECT_NAME,
				CLASS_NAME,
				METHOD_NAME,
				"FUTURE");

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	}

}