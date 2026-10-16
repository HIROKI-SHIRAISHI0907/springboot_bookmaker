package dev.batch.bm_b005;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.master.FutureMasterRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.FutureEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_B005未来データDB管理部品
 *
 * <p>2026-10 追加: 延期の試合の日程変更</p>
 * <ul>
 *   <li>取り込む予定が delay_postpone で延期になっている試合で、日時が変わっていれば、
 *       新しく登録せずに future_master の同じ予定の future_time を上書きする（{@link PostponedRescheduleService}）。
 *       上書きした試合は呼び出し側（FutureStat）でまとめて delay_postpone から削除する。</li>
 *   <li>日程変更の判定は重複チェックより先に行う（同じ mid の予定が既にあっても、日時が変わっていれば上書きする）。</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@Component
public class FutureDBService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = FutureDBService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = FutureDBService.class.getName();

	/** game_link のクエリパラメータ mid= の値を抽出する正規表現 */
	private static final Pattern MID_PATTERN = Pattern.compile("[?&]mid=([^&\\s]+)");

	/** FutureRepositoryレポジトリクラス */
	@Autowired
	private FutureMasterRepository futureRepository;

	/** 延期の試合の日程変更 */
	@Autowired
	private PostponedRescheduleService postponedRescheduleService;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * チェックメソッド（日程変更なし。互換用）
	 * @param chkEntities
	 * @param fillChar
	 */
	public List<FutureEntity> selectInBatch(List<FutureEntity> chkEntities, String fillChar) {
		return selectInBatch(chkEntities, fillChar, null, null);
	}

	/**
	 * チェックメソッド
	 * <p>
	 * 判定の順番:
	 * 0. 延期中の試合で日時が変わっていれば future_master を上書きし、登録しない（rescheduled に積む）
	 * 1. game_link に含まれる mid= の値が一致するデータが既に存在する場合は登録しない（最優先）
	 * 2. mid が取得できない場合のみ、従来通り home_team_name/away_team_name の一致で判定する（優先度を下げる）
	 * あわせて、同一バッチ内に同じ mid が複数含まれる場合（同じ試合の重複スクレイピング）も
	 * 2件目以降を除去する。
	 * </p>
	 * @param chkEntities 取り込む予定
	 * @param fillChar ログ用
	 * @param postponed 延期中の試合（{@link PostponedRescheduleService#loadPostponed()}。null なら日程変更しない）
	 * @param rescheduled 日程を変えた試合を積むリスト（null 可）
	 */
	public List<FutureEntity> selectInBatch(List<FutureEntity> chkEntities, String fillChar,
			List<PostponedRescheduleService.PostponedEntry> postponed,
			List<PostponedRescheduleService.Rescheduled> rescheduled) {
		final String METHOD_NAME = "selectInBatch";
		List<FutureEntity> entities = new ArrayList<>();
		Set<String> seenMidsInBatch = new HashSet<>();

		for (FutureEntity entity : chkEntities) {
			String mid = extractMid(entity.getGameLink());

			if (mid != null && !mid.isBlank()) {
				// 同一バッチ内での重複（同じ試合が複数回含まれているケース）
				if (!seenMidsInBatch.add(mid)) {
					continue;
				}
			}

			// 延期の試合の日程変更（上書きしたら登録しない）
			if (postponed != null && !postponed.isEmpty()) {
				try {
					PostponedRescheduleService.Rescheduled r = this.postponedRescheduleService.tryReschedule(entity, postponed);
					if (r != null) {
						if (rescheduled != null) {
							rescheduled.add(r);
						}
						continue;
					}
				} catch (RuntimeException e) {
					// 日程変更に失敗しても通常の登録は続ける
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
							"延期の試合の日程変更に失敗（通常の判定を続ける）: " + fillChar + " ("
									+ entity.getHomeTeamName() + " vs " + entity.getAwayTeamName() + ")");
				}
			}

			if (mid != null && !mid.isBlank()) {
				// DB上に同じmidの試合が既に存在するかどうか（最優先の重複判定）
				if (futureRepository.findCountByMid(mid) > 0) {
					continue;
				}
			} else {
				// mid が取得できない場合のみ、従来通りチーム名の重複で判定
				if (futureRepository.findDataCount(entity) > 0) {
					continue;
				}
			}

			entities.add(entity);
		}
		return entities;
	}

	/**
	 * game_link のクエリパラメータ mid= の値を抽出する。
	 * 例: https://www.flashscore.co.jp/match/soccer/xxx/yyy/?mid=hffbi8em -> "hffbi8em"
	 * @param gameLink
	 * @return mid の値。抽出できない場合は null。
	 */
	private String extractMid(String gameLink) {
		if (gameLink == null || gameLink.isBlank()) {
			return null;
		}
		Matcher matcher = MID_PATTERN.matcher(gameLink);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

	/**
	 * 登録メソッド
	 * @param insertEntities
	 */
	@Transactional(transactionManager = "masterTxManager", rollbackFor = Exception.class)
	public void insertInBatchOrThrow(List<FutureEntity> insertEntities) throws Exception {
		final String METHOD_NAME = "insertInBatchOrThrow";

		final int BATCH_SIZE = 10;
		for (int i = 0; i < insertEntities.size(); i += BATCH_SIZE) {
			int end = Math.min(i + BATCH_SIZE, insertEntities.size());
			List<FutureEntity> batch = insertEntities.subList(i, end);

			for (FutureEntity entity : batch) {
				try {
					int result = futureRepository.insert(entity);
					if (result != 1) {
						throw new Exception("master insert failed. result=" + result);
					}
					String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
					this.manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
							"登録件数: " + result + "件 (" + entity.getHomeTeamName() + " vs "
							+ entity.getAwayTeamName() + ")");
				} catch (DuplicateKeyException e) {
					// 重複は成功扱い（現状踏襲）
					manageLoggerComponent.debugWarnLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00002W_DUPLICATION_WARNING,
							"(" + entity.getHomeTeamName() + " vs "
									+ entity.getAwayTeamName() + ")");
				}
			}
		}
	}
}
